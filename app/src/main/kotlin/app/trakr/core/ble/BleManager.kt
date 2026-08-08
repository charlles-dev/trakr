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
import app.trakr.repository.ToolboxRepository
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Estado da conexão BLE com a maleta. */
sealed interface BleStatus {
    data object HwUnavailable : BleStatus
    data object Disabled : BleStatus
    data object Idle : BleStatus
    data object Scanning : BleStatus
    data object Connecting : BleStatus
    data class Connected(val deviceName: String) : BleStatus
    data class Error(val message: String) : BleStatus
}

/**
 * Motor BLE do app: conecta na maleta, recebe inventário e eventos via
 * GATT notify e persiste no Room.
 */
object BleManager {

    private const val REQUESTED_MTU = 512
    private val cccdUuid: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    private val _status = MutableStateFlow<BleStatus>(BleStatus.Idle)
    val status: StateFlow<BleStatus> = _status.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val repository = ToolboxRepository(AppContainer.database.toolboxDao())

    private var context: Context? = null
    private var gatt: BluetoothGatt? = null
    private var pendingDevice: BluetoothDevice? = null

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
        }
    }

    fun stop() {
        try {
            val adapter = context?.let {
                (it.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
            }
            adapter?.bluetoothLeScanner?.stopScan(scanCallback)
        } catch (e: Exception) {
            // ignora
        }
        gatt?.disconnect()
        gatt?.close()
        gatt = null
        pendingDevice = null
        _status.value = BleStatus.Idle
    }

    private fun connect(device: BluetoothDevice) {
        val ctx = context ?: return
        gatt?.close()
        _status.value = BleStatus.Connecting
        pendingDevice = device
        try {
            gatt = device.connectGatt(ctx, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } catch (e: SecurityException) {
            _status.value = BleStatus.Error("Sem permissão de conexão BLE")
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val name = result.device.name ?: result.scanRecord?.deviceName
            val matches = name == BleProfile.DEVICE_NAME || name?.startsWith("TRAKR") == true
            if (!matches) return

            try {
                context?.let { ctx ->
                    val adapter = (ctx.getSystemService(Context.BLUETOOTH_SERVICE)
                        as? BluetoothManager)?.adapter
                    adapter?.bluetoothLeScanner?.stopScan(this)
                }
            } catch (_: Exception) {}

            connect(result.device)
        }

        override fun onScanFailed(errorCode: Int) {
            _status.value = BleStatus.Error("Falha no scan BLE (código $errorCode)")
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothGatt.STATE_CONNECTED -> gatt.discoverServices()
                BluetoothGatt.STATE_DISCONNECTED -> {
                    _status.value = BleStatus.Idle
                    gatt.close()
                    if (this@BleManager.gatt === gatt) this@BleManager.gatt = null
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                _status.value = BleStatus.Error("Falha ao descobrir serviços ($status)")
                return
            }

            val service: BluetoothGattService? = gatt.getService(BleProfile.SERVICE_UUID)
            if (service == null) {
                _status.value = BleStatus.Error("Serviço do Trakr não encontrado")
                gatt.disconnect()
                return
            }

            enableNotification(gatt, service.getCharacteristic(BleProfile.INVENTORY_UUID))
            enableNotification(gatt, service.getCharacteristic(BleProfile.EVENT_UUID))

            gatt.requestMtu(REQUESTED_MTU)

            service.getCharacteristic(BleProfile.CONTROL_UUID)?.let { control ->
                try {
                    writeCharacteristic(gatt, control, """{"cmd":"rescan"}""")
                } catch (e: SecurityException) {
                    _status.value = BleStatus.Error("Sem permissão de escrita BLE")
                }
            }

            _status.value = BleStatus.Connected(gatt.device?.name ?: "Maleta")
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
            handleCharacteristic(characteristic, characteristic.value)
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            handleCharacteristic(characteristic, value)
        }

        private fun handleCharacteristic(
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

    // ---------------- Comandos para a maleta (GATT Control) ----------------

    /**
     * Envia comando JSON pela characteristic de controle.
     * Usa o inventário local (Room) como fallback quando desconectado.
     */
    private fun sendControl(json: String, onUnavailable: () -> Unit) {
        val g = gatt
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

    private fun onInventory(json: String) {
        val (toolbox, tools) = InventoryParser.parseInventory(json)
        scope.launch { repository.saveInventory(toolbox, tools) }
    }

    private fun onEvent(json: String) {
        val event = InventoryParser.parseEvent(json) ?: return
        scope.launch {
            repository.insertAlert(
                AlertEvent(
                    toolId = event.toolId,
                    toolName = event.toolName,
                    createdAt = System.currentTimeMillis(),
                ),
            )
        }
        context?.let { NotificationService(it).showMissingToolAlert(event.toolName) }
    }
}