package app.trakr.core.ble

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import app.trakr.core.notifications.NotificationService
import app.trakr.data.AppContainer
import app.trakr.data.InventoryParser
import app.trakr.model.AlertEvent
import app.trakr.model.EventRecord
import app.trakr.model.ToolboxStore
import app.trakr.repository.ToolboxRepository
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Estado global do motor BLE (scan/erros). */
sealed interface BleStatus {
    data object HwUnavailable : BleStatus
    data object Disabled : BleStatus
    data object Idle : BleStatus
    data object Scanning : BleStatus
    data class Connected(val deviceName: String) : BleStatus
    data class Error(val message: String) : BleStatus
}

/** Informação de uma maleta conectada (sessão BLE). */
data class BleDeviceInfo(
    val address: String,
    val name: String,
    val toolboxId: String?,
)

/**
 * Motor BLE multi-maleta:
 * - Escaneia em janelas e conecta-se a TODAS as maletas TRAKR encontradas
 *   (cada uma com a sua própria sessão GATT);
 * - Roda um escaner periódico para (re)conectar maletas que apareçam/sumam;
 * - Roteia eventos e histórico de cada sessão para o perfil (toolboxId) da
 *   própria maleta no Room.
 */
object BleManager {

    private const val REQUESTED_MTU = 512
    private const val SCAN_WINDOW_MS = 5000L
    private const val RESCAN_INTERVAL_MS = 20000L
    private val cccdUuid: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    private val _status = MutableStateFlow<BleStatus>(BleStatus.Idle)
    val status: StateFlow<BleStatus> = _status.asStateFlow()

    private val _devices = MutableStateFlow<List<BleDeviceInfo>>(emptyList())
    val devices: StateFlow<List<BleDeviceInfo>> = _devices.asStateFlow()

    val connectedCount: Int get() = sessions.size

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val repository = ToolboxRepository(AppContainer.database.toolboxDao())

    private var context: Context? = null
    private val sessions = mutableMapOf<String, BleSession>()
    private val foundDevices = mutableMapOf<String, BluetoothDevice>()
    private var scanJob: Job? = null
    private var rescanJob: Job? = null
    private var scanning = false
    private var running = false

    // ---------------- Ciclo de vida ----------------

    fun start(ctx: Context) {
        context = ctx.applicationContext

        val manager = ctx.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = manager?.adapter
        if (adapter == null) {
            _status.value = BleStatus.HwUnavailable
            return
        }
        if (!adapter.isEnabled) {
            _status.value = BleStatus.Disabled
            return
        }

        running = true
        startScanWindow()
    }

    fun stop() {
        running = false
        scanning = false
        scanJob?.cancel()
        rescanJob?.cancel()
        try {
            val adapter = context?.let {
                (it.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
            }
            adapter?.bluetoothLeScanner?.stopScan(scanCallback)
        } catch (e: Exception) {
            // ignora
        }
        sessions.values.forEach { it.disconnect() }
        sessions.clear()
        foundDevices.clear()
        _devices.value = emptyList()
        _status.value = BleStatus.Idle
    }

    /** Pede um novo ciclo de escaneamento (usado também no onDestroy do app). */
    fun rescan() {
        if (running) startScanWindow()
    }

    // ---------------- Escaneamento ----------------

    private fun startScanWindow() {
        if (!running || scanning) return
        scanning = true
        foundDevices.clear()

        val adapter = context?.let {
            (it.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
        } ?: return
        try {
            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()
            val filters = listOf(
                ScanFilter.Builder().setDeviceName(BleProfile.DEVICE_NAME).build(),
            )
            _status.value = BleStatus.Scanning
            adapter.bluetoothLeScanner.startScan(filters, settings, scanCallback)
        } catch (e: SecurityException) {
            _status.value = BleStatus.Error("Permissão BLE necessária")
            scanning = false
            return
        }

        scanJob = scope.launch {
            delay(SCAN_WINDOW_MS)
            stopScanNow()
            connectFoundDevices()
            scanning = false
            scheduleRescan()
        }
    }

    private fun stopScanNow() {
        try {
            val adapter = context?.let {
                (it.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
            }
            adapter?.bluetoothLeScanner?.stopScan(scanCallback)
        } catch (e: Exception) {
            // ignora
        }
    }

    private fun scheduleRescan() {
        rescanJob?.cancel()
        rescanJob = scope.launch {
            delay(RESCAN_INTERVAL_MS)
            startScanWindow()
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val name = result.device.name ?: result.scanRecord?.deviceName
            val matches = name == BleProfile.DEVICE_NAME || name?.startsWith("TRAKR") == true
            if (!matches) return
            foundDevices[result.device.address] = result.device
        }

        override fun onScanFailed(errorCode: Int) {
            _status.value = BleStatus.Error("Falha no scan BLE (código $errorCode)")
            scanning = false
            scheduleRescan()
        }
    }

    private fun connectFoundDevices() {
        var newConnections = 0
        foundDevices.values.forEach { device ->
            if (!sessions.containsKey(device.address)) {
                val session = BleSession(device)
                sessions[device.address] = session
                session.connect()
                newConnections++
            }
        }
        if (newConnections > 0) updateDevices()
        if (sessions.isNotEmpty()) {
            _status.value = BleStatus.Connected(
                if (sessions.size == 1) sessions.values.first().deviceName
                else "${sessions.size} maletas",
            )
        }
    }

    // ---------------- Sessão (1 por maleta) ----------------

    private class BleSession(val device: BluetoothDevice) {
        var gatt: BluetoothGatt? = null
        /** Perfil descoberto na maleta (definido pelo payload `id` do inventário). */
        var toolboxId: String? = null

        val deviceName: String
            get() = device.name ?: "Maleta ${device.address.takeLast(6)}"

        fun connect() {
            val ctx = context ?: return
            try {
                gatt = device.connectGatt(
                    ctx, false, sessionCallback(this), BluetoothDevice.TRANSPORT_LE,
                )
            } catch (e: SecurityException) {
                _status.value = BleStatus.Error("Sem permissão de conexão BLE")
            }
        }

        fun disconnect() {
            gatt?.disconnect()
            gatt?.close()
            gatt = null
        }

        /** Remove a sessão do mapa e re-arma o scan (reconexão automática). */
        fun drop() {
            disconnect()
            BleManager.sessions.remove(device.address)
            BleManager.updateDevices()
            if (BleManager.running) BleManager.scheduleRescan() else BleManager.rescan()
            if (BleManager.sessions.isEmpty()) BleManager._status.value = BleStatus.Idle
        }
    }

    private fun updateDevices() {
        _devices.value = sessions.values.map { session ->
            BleDeviceInfo(
                address = session.device.address,
                name = session.deviceName,
                toolboxId = session.toolboxId,
            )
        }
    }

    private fun sessionCallback(session: BleSession): BluetoothGattCallback =
        object : BluetoothGattCallback() {

            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                when (newState) {
                    BluetoothGatt.STATE_CONNECTED -> gatt.discoverServices()
                    BluetoothGatt.STATE_DISCONNECTED -> {
                        if (sessions[session.device.address] === session) {
                            session.drop()
                        } else {
                            gatt.close()
                        }
                    }
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    session.drop()
                    return
                }

                val service: BluetoothGattService? = gatt.getService(BleProfile.SERVICE_UUID)
                if (service == null) {
                    _status.value = BleStatus.Error("Serviço do Trakr não encontrado")
                    session.drop()
                    return
                }

                enableNotification(gatt, service.getCharacteristic(BleProfile.INVENTORY_UUID))
                enableNotification(gatt, service.getCharacteristic(BleProfile.EVENT_UUID))

                gatt.requestMtu(REQUESTED_MTU)

                // Seleciona o perfil ativo só no primeiro contato; depois a
                // maleta continua no perfil que ela mesma escolheu (via app).
                if (session.toolboxId == null) {
                    val selection = ToolboxStore.current.value
                    service.getCharacteristic(BleProfile.CONTROL_UUID)?.let { control ->
                        try {
                            writeCharacteristic(
                                gatt, control,
                                """{"cmd":"select_toolbox","id":"${selection.id}"}""",
                            )
                            writeCharacteristic(gatt, control, """{"cmd":"rescan"}""")
                        } catch (e: SecurityException) {
                            _status.value = BleStatus.Error("Sem permissão de escrita BLE")
                        }
                    }
                }
                service.getCharacteristic(BleProfile.HISTORY_UUID)?.let { history ->
                    try {
                        readHistory(gatt, history)
                    } catch (e: SecurityException) {
                        _status.value = BleStatus.Error("Sem permissão de leitura BLE")
                    }
                }
            }

            override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
                // MTU negociada — não é necessário agir.
            }

            @Deprecated("Compatibilidade com Android < 13")
            @Suppress("DEPRECATION")
            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
            ) {
                handleCharacteristic(session, characteristic, characteristic.value)
            }

            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray,
            ) {
                handleCharacteristic(session, characteristic, value)
            }

            private fun handleCharacteristic(
                session: BleSession,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray,
            ) {
                val text = value.toString(Charsets.UTF_8)
                when (characteristic.uuid) {
                    BleProfile.INVENTORY_UUID -> onInventory(session, text)
                    BleProfile.EVENT_UUID -> onEvent(session, text)
                }
            }

            override fun onCharacteristicRead(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray,
                status: Int,
            ) {
                if (status != BluetoothGatt.GATT_SUCCESS) return
                if (characteristic.uuid == BleProfile.HISTORY_UUID) {
                    onHistory(session, value.toString(Charsets.UTF_8))
                }
            }

            @Deprecated("Compatibilidade com Android < 13")
            @Suppress("DEPRECATION")
            override fun onCharacteristicRead(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                status: Int,
            ) {
                if (status != BluetoothGatt.GATT_SUCCESS) return
                if (characteristic.uuid == BleProfile.HISTORY_UUID) {
                    onHistory(session, characteristic.value.toString(Charsets.UTF_8))
                }
            }
        }

    // ---------------- Utilidades GATT ----------------

    private fun readHistory(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.readCharacteristic(characteristic)
            } else {
                @Suppress("DEPRECATION")
                gatt.readCharacteristic(characteristic)
            }
        } catch (e: SecurityException) {
            _status.value = BleStatus.Error("Sem permissão de leitura BLE")
        }
    }

    @Suppress("DEPRECATION")
    private fun enableNotification(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic?) {
        if (characteristic == null) return
        try {
            gatt.setCharacteristicNotification(characteristic, true)
            characteristic.getDescriptor(cccdUuid)?.let { descriptor ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                } else {
                    descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    gatt.writeDescriptor(descriptor)
                }
            }
        } catch (e: SecurityException) {
            _status.value = BleStatus.Error("Falha ao habilitar notificações BLE")
        }
    }

    @Suppress("DEPRECATION")
    private fun writeCharacteristic(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        payload: String,
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeCharacteristic(characteristic, payload.toByteArray(), 0)
        } else {
            characteristic.value = payload.toByteArray()
            gatt.writeCharacteristic(characteristic)
        }
    }

    /** Sessão alvo para comandos: a da maleta do perfil ativo, senão a única. */
    private fun targetGatt(): BluetoothGatt? {
        val active = ToolboxStore.current.value.id
        val session = sessions.values.firstOrNull { it.toolboxId == active }
        if (session != null) return session.gatt
        if (sessions.size == 1) return sessions.values.first().gatt
        return null
    }

    private fun sendControl(json: String, onUnavailable: () -> Unit) {
        val g = targetGatt()
        val control = g?.getService(BleProfile.SERVICE_UUID)
            ?.getCharacteristic(BleProfile.CONTROL_UUID)
        if (g == null || control == null) {
            onUnavailable()
            return
        }
        try {
            writeCharacteristic(g, control, json)
        } catch (e: SecurityException) {
            _status.value = BleStatus.Error("Sem permissão de escrita BLE")
        }
    }

    // ---------------- Comandos para a maleta (GATT Control) ----------------

    /** Adiciona ferramenta na maleta: {"cmd":"add_tool","name":...,"tag":...} */
    fun addTool(name: String, epc: String, onUnavailable: () -> Unit) {
        sendControl(
            """{"cmd":"add_tool","name":"$name","tag":"$epc"}""",
            onUnavailable,
        )
    }

    /** Remove ferramenta da maleta: {"cmd":"remove_tool","id":...} */
    fun removeTool(id: String, onUnavailable: () -> Unit) {
        sendControl("""{"cmd":"remove_tool","id":"$id"}""", onUnavailable)
    }

    /** Troca o perfil ativo das maletas: {"cmd":"select_toolbox","id":...} */
    fun selectToolbox(id: String) {
        val payload = """{"cmd":"select_toolbox","id":"$id"}"""
        sessions.values.forEach { session ->
            session.toolboxId = id
            val control = session.gatt?.getService(BleProfile.SERVICE_UUID)
                ?.getCharacteristic(BleProfile.CONTROL_UUID)
            if (control != null) {
                try {
                    writeCharacteristic(session.gatt!!, control, payload)
                } catch (e: SecurityException) {
                    _status.value = BleStatus.Error("Sem permissão de escrita BLE")
                }
            }
        }
    }

    // ---------------- Persistência (por sessão → por perfil) ----------------

    private fun onInventory(session: BleSession, json: String) {
        val (toolbox, tools) = InventoryParser.parseInventory(json)
        session.toolboxId = toolbox.id
        updateDevices()
        scope.launch { repository.saveInventory(toolbox, tools) }
    }

    private fun onEvent(session: BleSession, json: String) {
        val event = InventoryParser.parseEvent(json) ?: return
        val toolboxId = session.toolboxId ?: ToolboxStore.current.value.id
        scope.launch {
            repository.insertAlert(
                AlertEvent(
                    toolId = event.toolId,
                    toolName = event.toolName,
                    createdAt = System.currentTimeMillis(),
                ),
            )
            // Espelha o evento no histórico local do perfil da maleta.
            repository.upsertEvent(
                EventRecord(
                    toolboxId = toolboxId,
                    type = event.type,
                    toolId = event.toolId,
                    toolName = event.toolName,
                ),
            )
        }
        // Notificação push apenas para retirada de ferramenta.
        if (event.type == "tool_missing") {
            context?.let { NotificationService(it).showMissingToolAlert(event.toolName) }
        }
    }

    private fun onHistory(session: BleSession, json: String) {
        val toolboxId = session.toolboxId ?: ToolboxStore.current.value.id
        val events = InventoryParser.parseHistory(json, toolboxId)
        scope.launch { repository.saveHistory(toolboxId, events) }
    }
}