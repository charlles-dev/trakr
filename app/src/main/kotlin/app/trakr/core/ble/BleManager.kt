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
import android.util.Log
import app.trakr.R
import app.trakr.data.AppContainer
import app.trakr.data.InventoryParser
import app.trakr.data.InventoryParser.CmdReply
import app.trakr.model.RadarReport
import app.trakr.repository.ToolboxRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

/** Estado global do motor BLE (scan/erros). */
sealed interface BleStatus {
    data object HwUnavailable : BleStatus

    data object Disabled : BleStatus

    data object Idle : BleStatus

    data object Scanning : BleStatus

    data class Connected(val deviceName: String) : BleStatus

    data class Error(val message: String) : BleStatus
}

/** Informação de um rastreador TRK-Finder conectado (sessão BLE). */
data class BleDeviceInfo(
    val address: String,
    val name: String,
)

/**
 * Motor BLE do rastreador portátil:
 * - Escaneia em janelas e conecta-se a TODOS os rastreadores TRK-Finder
 *   encontrados (cada um com a sua própria sessão GATT);
 * - Roda um escaner periódico para (re)conectar rastreadores que apareçam/sumam;
 * - Roteia eventos (radar_report/cmd_reply) e inventário de cada sessão.
 */
object BleManager : BleGateway {
    private const val TAG = "BleManager"
    private const val REQUESTED_MTU = 512
    private const val SCAN_WINDOW_MS = 5000L
    private const val RESCAN_INTERVAL_MS = 20000L
    private val cccdUuid: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    private val _status = MutableStateFlow<BleStatus>(BleStatus.Idle)
    override val status: StateFlow<BleStatus> = _status.asStateFlow()

    private val _devices = MutableStateFlow<List<BleDeviceInfo>>(emptyList())
    override val devices: StateFlow<List<BleDeviceInfo>> = _devices.asStateFlow()

    private val _radarReport = MutableStateFlow<RadarReport?>(null)

    /** Último relatório do modo radar (rastreador portátil), ou null. */
    override val radarReport: StateFlow<RadarReport?> = _radarReport.asStateFlow()

    private val _lastReply = MutableStateFlow<CmdReply?>(null)

    /** Último ACK de comando recebido do firmware, ou null. */
    override val lastReply: StateFlow<CmdReply?> = _lastReply.asStateFlow()

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

    // ---------------- Utilidades ----------------

    /** Resolve um recurso de string com o contexto do app (se disponível). */
    private fun str(
        resId: Int,
        vararg args: Any,
    ): String = context?.getString(resId, *args) ?: ""

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
            val adapter =
                context?.let {
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
        _radarReport.value = null
        _status.value = BleStatus.Idle
    }

    /** Pede um novo ciclo de escaneamento (usado também no onDestroy do app). */
    override fun rescan() {
        if (running) startScanWindow()
    }

    // ---------------- Escaneamento ----------------

    private fun startScanWindow() {
        if (!running || scanning) return
        scanning = true
        foundDevices.clear()

        val adapter =
            context?.let {
                (it.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
            } ?: return
        try {
            val settings =
                ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                    .build()
            val filters =
                listOf(
                    ScanFilter.Builder().setServiceUuid(android.os.ParcelUuid(BleProfile.SERVICE_UUID)).build(),
                )
            _status.value = BleStatus.Scanning
            adapter.bluetoothLeScanner.startScan(filters, settings, scanCallback)
        } catch (e: SecurityException) {
            _status.value = BleStatus.Error(str(R.string.ble_error_permission))
            scanning = false
            return
        }

        scanJob =
            scope.launch {
                delay(SCAN_WINDOW_MS)
                stopScanNow()
                connectFoundDevices()
                scanning = false
                scheduleRescan()
            }
    }

    private fun stopScanNow() {
        try {
            val adapter =
                context?.let {
                    (it.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
                }
            adapter?.bluetoothLeScanner?.stopScan(scanCallback)
        } catch (e: Exception) {
            // ignora
        }
    }

    private fun scheduleRescan() {
        rescanJob?.cancel()
        rescanJob =
            scope.launch {
                delay(RESCAN_INTERVAL_MS)
                startScanWindow()
            }
    }

    private val scanCallback =
        object : ScanCallback() {
            override fun onScanResult(
                callbackType: Int,
                result: ScanResult,
            ) {
                val name = result.device.name ?: result.scanRecord?.deviceName
                if (name?.startsWith("TRK-") != true) return
                foundDevices[result.device.address] = result.device
            }

            override fun onScanFailed(errorCode: Int) {
                _status.value = BleStatus.Error(str(R.string.ble_error_scan_failed, errorCode))
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
            _status.value =
                BleStatus.Connected(
                    if (sessions.size == 1) {
                        sessions.values.first().deviceName
                    } else {
                        str(R.string.ble_multiple_devices, sessions.size)
                    },
                )
        }
    }

    // ---------------- Sessão (1 por rastreador) ----------------

    private class BleSession(val device: BluetoothDevice) {
        var gatt: BluetoothGatt? = null

        val deviceName: String
            get() = device.name ?: str(R.string.ble_device_fallback, device.address.takeLast(6))

        fun connect() {
            val ctx = context ?: return
            try {
                gatt =
                    device.connectGatt(
                        ctx, false, sessionCallback(this), BluetoothDevice.TRANSPORT_LE,
                    )
            } catch (e: SecurityException) {
                _status.value = BleStatus.Error(str(R.string.ble_error_connect_permission))
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
        _devices.value =
            sessions.values.map { session ->
                BleDeviceInfo(
                    address = session.device.address,
                    name = session.deviceName,
                )
            }
    }

    private fun sessionCallback(session: BleSession): BluetoothGattCallback =
        object : BluetoothGattCallback() {
            override fun onConnectionStateChange(
                gatt: BluetoothGatt,
                status: Int,
                newState: Int,
            ) {
                when (newState) {
                    BluetoothGatt.STATE_CONNECTED -> gatt.discoverServices()
                    BluetoothGatt.STATE_DISCONNECTED -> {
                        if (sessions[session.device.address] === session) {
                            _radarReport.value = null
                            session.drop()
                        } else {
                            gatt.close()
                        }
                    }
                }
            }

            override fun onServicesDiscovered(
                gatt: BluetoothGatt,
                status: Int,
            ) {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    session.drop()
                    return
                }

                val service: BluetoothGattService? = gatt.getService(BleProfile.SERVICE_UUID)
                if (service == null) {
                    _status.value = BleStatus.Error(str(R.string.ble_error_service_not_found))
                    session.drop()
                    return
                }

                enableNotification(gatt, service.getCharacteristic(BleProfile.INVENTORY_UUID))
                enableNotification(gatt, service.getCharacteristic(BleProfile.EVENT_UUID))

                gatt.requestMtu(REQUESTED_MTU)

                // Pede a varredura inicial: o firmware responde com o
                // inventário completo (notify) e entra em sincronização.
                service.getCharacteristic(BleProfile.CONTROL_UUID)?.let { control ->
                    try {
                        writeCharacteristic(gatt, control, """{"cmd":"rescan"}""")
                    } catch (e: SecurityException) {
                        _status.value = BleStatus.Error(str(R.string.ble_error_write_permission))
                    }
                }
            }

            override fun onMtuChanged(
                gatt: BluetoothGatt,
                mtu: Int,
                status: Int,
            ) {
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
                    BleProfile.INVENTORY_UUID -> onInventory(text)
                    BleProfile.EVENT_UUID -> onEvent(text)
                }
            }
        }

    // ---------------- Utilidades GATT ----------------

    @Suppress("DEPRECATION")
    private fun enableNotification(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic?,
    ) {
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
            _status.value = BleStatus.Error(str(R.string.ble_error_notifications))
        }
    }

    @Suppress("DEPRECATION")
    private fun writeCharacteristic(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        payload: String,
    ) {
        writeCharacteristic(gatt, characteristic, payload.toByteArray())
    }

    @Suppress("DEPRECATION")
    private fun writeCharacteristic(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        payload: ByteArray,
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeCharacteristic(characteristic, payload, 0)
        } else {
            characteristic.value = payload
            gatt.writeCharacteristic(characteristic)
        }
    }

    private fun controlGatt(): BluetoothGatt? {
        val session = sessions.values.firstOrNull() ?: return null
        return session.gatt
    }

    private fun sendControl(
        json: String,
        onUnavailable: () -> Unit,
    ) {
        val g = controlGatt()
        val control =
            g?.getService(BleProfile.SERVICE_UUID)
                ?.getCharacteristic(BleProfile.CONTROL_UUID)
        if (g == null || control == null) {
            onUnavailable()
            return
        }
        try {
            writeCharacteristic(g, control, json)
        } catch (e: SecurityException) {
            _status.value = BleStatus.Error(str(R.string.ble_error_write_permission))
        }
    }

    // ---------------- Comandos (GATT Control) ----------------

    /** Adiciona ferramenta no rastreador: {"cmd":"add_tool","name":...,"tag":...} */
    override fun addTool(
        name: String,
        epc: String,
        onUnavailable: () -> Unit,
    ) {
        sendControl(
            """{"cmd":"add_tool","name":"$name","tag":"$epc"}""",
            onUnavailable,
        )
    }

    /** Remove ferramenta do rastreador: id + epc (o firmware casa por EPC se preciso). */
    override fun removeTool(
        id: String,
        epc: String,
        onUnavailable: () -> Unit,
    ) {
        sendControl(
            """{"cmd":"remove_tool","id":"$id","epc":"$epc"}""",
            onUnavailable,
        )
    }

    /** Inicia o modo radar: id + tag (o firmware casa por EPC preferencialmente). */
    override fun startRadar(
        toolId: String,
        tag: String,
        onUnavailable: () -> Unit,
    ) {
        sendControl(
            """{"cmd":"start_radar","id":"$toolId","tag":"$tag"}""",
            onUnavailable,
        )
    }

    /** Para o modo radar: {"cmd":"stop_radar"} */
    override fun stopRadar(onUnavailable: () -> Unit) {
        sendControl("""{"cmd":"stop_radar"}""", onUnavailable)
    }

    // ---------------- OTA (firmware) ----------------

    /** Abre a sessão OTA no rastreador: {"cmd":"ota_begin","size":N} */
    fun beginOta(size: Long) {
        sendControl("""{"cmd":"ota_begin","size":$size}""") { /* resposta via cmd_reply */ }
    }

    /** Finaliza a OTA: o firmware valida, define boot e reinicia. */
    fun endOta() {
        sendControl("""{"cmd":"ota_end"}""") { /* resposta via cmd_reply */ }
    }

    /** Descarta a sessão OTA em andamento (erro/cancelamento). */
    fun abortOta() {
        sendControl("""{"cmd":"ota_abort"}""") { /* resposta via cmd_reply */ }
    }

    /** Escreve um chunk binário na característica OTA. */
    fun sendOtaChunk(chunk: ByteArray): Boolean {
        val g = controlGatt() ?: return false
        val ota =
            g.getService(BleProfile.SERVICE_UUID)
                ?.getCharacteristic(BleProfile.OTA_UUID) ?: return false
        return try {
            writeCharacteristic(g, ota, chunk)
            true
        } catch (e: SecurityException) {
            _status.value = BleStatus.Error(str(R.string.ble_error_write_permission))
            false
        }
    }

    // ---------------- Persistência ----------------

    private fun onInventory(json: String) {
        val tools = InventoryParser.parseInventory(json)
        scope.launch {
            try {
                repository.saveInventory(tools)
            } catch (e: Exception) {
                Log.w(TAG, "Falha ao salvar inventário local", e)
            }
        }
    }

    private fun onEvent(json: String) {
        val report = InventoryParser.parseRadarReport(json)
        if (report != null) {
            _radarReport.value = report
            // Espelha o estado da tag no inventário local (último visto).
            if (report.tag.isNotBlank()) {
                val now = System.currentTimeMillis()
                scope.launch {
                    try {
                        repository.updateToolState(
                            epc = report.tag,
                            present = report.present,
                            rssi = report.rssi,
                            lastSeenAt = now,
                        )
                        repository.recordRssi(report.tag, report.rssi)
                    } catch (e: Exception) {
                        Log.w(TAG, "Falha ao espelhar radar_report no inventário", e)
                    }
                }
            }
            return
        }

        InventoryParser.parseCmdReply(json)?.let { _lastReply.value = it }
    }
}
