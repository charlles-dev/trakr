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
import app.trakr.repository.ToolRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
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

/** InformaÃ§Ã£o de um rastreador TRK-Finder conectado (sessÃ£o BLE). */
data class BleDeviceInfo(
    val address: String,
    val name: String,
)

/**
 * Motor BLE do rastreador portÃ¡til:
 * - Escaneia em janelas e conecta-se a TODOS os rastreadores TRK-Finder
 *   encontrados (cada um com a sua prÃ³pria sessÃ£o GATT);
 * - Roda um escaner periÃ³dico para (re)conectar rastreadores que apareÃ§am/sumam;
 * - Roteia eventos (radar_report/cmd_reply) e inventÃ¡rio de cada sessÃ£o.
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
    override val radarReport: StateFlow<RadarReport?> = _radarReport.asStateFlow()
    private val _liveReport = MutableStateFlow<app.trakr.model.LiveReport?>(null)
    override val liveReport: StateFlow<app.trakr.model.LiveReport?> = _liveReport.asStateFlow()
    private val _multiReport = MutableStateFlow<app.trakr.model.MultiRadarReport?>(null)
    override val multiReport: StateFlow<app.trakr.model.MultiRadarReport?> = _multiReport.asStateFlow()

    private val _lastReply = MutableStateFlow<CmdReply?>(null)
    override val lastReply: StateFlow<CmdReply?> = _lastReply.asStateFlow()

    val connectedCount: Int get() = sessions.size

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val repository = ToolRepository(AppContainer.database.toolDao())

    private var context: Context? = null
    private val sessions = mutableMapOf<String, BleSession>()
    private val chunkBuffers = mutableMapOf<Pair<String, String>, ChunkAssembly>()
    private val foundDevices = mutableMapOf<String, BluetoothDevice>()
    private var scanJob: Job? = null
    private var rescanJob: Job? = null
    private var scanning = false
    private var running = false

    // ---------------- Utilidades ----------------

    /** Resolve um recurso de string com o contexto do app (se disponÃ­vel). */
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

    /** Pede um novo ciclo de escaneamento (usado tambÃ©m no onDestroy do app). */
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

    // ---------------- SessÃ£o (1 por rastreador) ----------------

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

        /** Remove a sessÃ£o do mapa e re-arma o scan (reconexÃ£o automÃ¡tica). */
        fun drop() {
            disconnect()
            BleManager.sessions.remove(device.address)
            BleManager.dropSessionBuffers(this)
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

                // Sincroniza o relógio do rastreador com o epoch UTC do celular
                // (firmware calcula delta e persiste em config.json) e em seguida
                // pede a varredura inicial — o firmware responde com inventário
                // completo (notify) e entra em sincronização.
                service.getCharacteristic(BleProfile.CONTROL_UUID)?.let { control ->
                    try {
                        val epochMs = System.currentTimeMillis()
                        writeCharacteristic(gatt, control, """{"cmd":"set_clock","epoch_ms":$epochMs}""")
                        // Pequeno delay para evitar colisão de WRITE no GATT
                        // (stack BLE não tem fila de escrita nesta implementação).
                        scope.launch {
                            delay(350)
                            try {
                                writeCharacteristic(gatt, control, """{"cmd":"rescan"}""")
                            } catch (e: SecurityException) {
                                _status.value = BleStatus.Error(str(R.string.ble_error_write_permission))
                            } catch (_: Exception) {
                                // Ignora falhas de escrita pós-desconexão
                            }
                        }
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
                // MTU negociada â€” nÃ£o Ã© necessÃ¡rio agir.
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

/** Descartar sessão e buffers de chunks órfãos ao desconectar/expirar. */
    private fun dropSessionBuffers(session: BleSession) {
        chunkBuffers.keys.retainAll { it.first != session.device.address }
    }

    private fun handleCharacteristic(
        session: BleSession,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray,
    ) {
        val text = value.toString(Charsets.UTF_8)
        when (characteristic.uuid) {
            BleProfile.INVENTORY_UUID -> dispatch(session, text) { onInventory(it) }
            BleProfile.EVENT_UUID -> dispatch(session, text) { onEvent(it) }
        }
    }

    /**
     * Reage a payloads JSON, remontando mensagens divididas em chunks pelo
     * firmware (envelope {"t":"chunk","k":...,"n":...,"i":...,"d":...}).
     * Envelopes não-chunk (ou os antigos, sem chunking) seguem direto.
     */
    private fun dispatch(
        session: BleSession,
        text: String,
        deliver: (String) -> Unit,
    ) {
        val envelope = try {
            JSONObject(text)
        } catch (_: Exception) {
            null
        }
        if (envelope == null || envelope.optString("t") != "chunk") {
            deliver(text)
            return
        }

        val key = session.device.address to envelope.optString("k")
        val total = envelope.optInt("n", -1)
        val index = envelope.optInt("i", -1)
        val data = envelope.optString("d")
        if (total <= 0 || index < 0 || index >= total) {
            chunkBuffers.remove(key)
            return
        }

        var assembly = chunkBuffers[key]
        if (assembly == null || assembly.total != total) {
            assembly = ChunkAssembly(total)
            chunkBuffers[key] = assembly
        }
        if (assembly.parts[index] == null) {
            assembly.received++
            assembly.parts[index] = data
        }
        if (assembly.received == total) {
            chunkBuffers.remove(key)
            if (assembly.parts.all { it != null }) {
                deliver(assembly.parts.joinToString(""))
            }
        }
    }

    /** Buffer de remontagem de chunks de uma mensagem JSON. */
    private class ChunkAssembly(val total: Int) {
        var received = 0
        val parts = arrayOfNulls<String>(total)
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

    /** Pede as configuraÃ§Ãµes do rastreador: {"cmd":"get_config"} */
    override fun getConfig(onUnavailable: () -> Unit) {
        sendControl("""{"cmd":"get_config"}""", onUnavailable)
    }

    /** Altera configs do rastreador: {"cmd":"set_config",...} */
    override fun setConfig(
        fields: Map<String, Any>,
        onUnavailable: () -> Unit,
    ) {
        val doc = JSONObject()
        doc.put("cmd", "set_config")
        fields.forEach { (key, value) -> doc.put(key, value) }
        sendControl(doc.toString(), onUnavailable)
    }

    /** Autentica no rastreador: {"cmd":"auth","pin":"..."} */
    override fun auth(
        pin: String,
        onUnavailable: () -> Unit,
    ) {
        val doc = JSONObject()
        doc.put("cmd", "auth")
        doc.put("pin", pin)
        sendControl(doc.toString(), onUnavailable)
    }

    override fun getHistory(
        month: String?,
        onUnavailable: () -> Unit,
    ) {
        val doc = JSONObject()
        doc.put("cmd", "get_history")
        if (!month.isNullOrBlank()) doc.put("month", month)
        sendControl(doc.toString(), onUnavailable)
    }

    override fun listArchives(onUnavailable: () -> Unit) {
        sendControl("""{"cmd":"list_archives"}""", onUnavailable)
    }

    override fun getSensors(onUnavailable: () -> Unit) {
        sendControl("""{"cmd":"get_sensors"}""", onUnavailable)
    }

    override fun getAddons(onUnavailable: () -> Unit) {
        sendControl("""{"cmd":"get_addons"}""", onUnavailable)
    }

    override fun startLive(
        intervalMs: Int,
        onUnavailable: () -> Unit,
    ) {
        sendControl("""{"cmd":"start_live","interval_ms":$intervalMs}""", onUnavailable)
    }

    override fun stopLive(onUnavailable: () -> Unit) {
        sendControl("""{"cmd":"stop_live"}""", onUnavailable)
    }

    override fun startMultiRadar(
        tags: List<String>,
        onUnavailable: () -> Unit,
    ) {
        val doc = JSONObject()
        doc.put("cmd", "start_radar_multi")
        doc.put("tags", org.json.JSONArray(tags))
        sendControl(doc.toString(), onUnavailable)
    }

    override fun setTxPower(
        dbm: Int,
        onUnavailable: () -> Unit,
    ) {
        sendControl("""{"cmd":"set_tx_power","dbm":$dbm}""", onUnavailable)
    }

    override fun captureTag(onUnavailable: () -> Unit) {
        sendControl("""{"cmd":"capture_tag"}""", onUnavailable)
    }

    override fun syncInventory(
        tools: List<app.trakr.model.Tool>,
        onUnavailable: () -> Unit,
    ) {
        val doc = JSONObject()
        doc.put("cmd", "sync_inventory")
        val arr = org.json.JSONArray()
        tools.forEach { tool ->
            val obj = JSONObject()
            obj.put("id", tool.id)
            obj.put("name", tool.name)
            obj.put("epc", tool.epc)
            arr.put(obj)
        }
        doc.put("tools", arr)
        sendControl(doc.toString(), onUnavailable)
    }

    override fun locateFinder(
        durationSec: Int,
        onUnavailable: () -> Unit,
    ) {
        sendControl("""{"cmd":"find_device","duration_sec":$durationSec}""", onUnavailable)
    }

    override fun setRfPower(
        dbm: Int,
        onUnavailable: () -> Unit,
    ) {
        sendControl("""{"cmd":"set_rf_power","power_dbm":$dbm}""", onUnavailable)
    }

    override fun writeEpcTag(
        newEpc: String,
        onUnavailable: () -> Unit,
    ) {
        val doc = JSONObject()
        doc.put("cmd", "write_epc")
        doc.put("new_epc", newEpc.trim().uppercase())
        sendControl(doc.toString(), onUnavailable)
    }

    override fun getBlackboxLogs(onUnavailable: () -> Unit) {
        sendControl("""{"cmd":"get_blackbox_logs"}""", onUnavailable)
    }

    // ---------------- OTA (firmware) ----------------

    /** Abre a sessÃ£o OTA no rastreador: {"cmd":"ota_begin","size":N} */
    fun beginOta(size: Long) {
        sendControl("""{"cmd":"ota_begin","size":$size}""") { /* resposta via cmd_reply */ }
    }

    /** Finaliza a OTA: o firmware valida, define boot e reinicia. */
    fun endOta() {
        sendControl("""{"cmd":"ota_end"}""") { /* resposta via cmd_reply */ }
    }

    /** Descarta a sessÃ£o OTA em andamento (erro/cancelamento). */
    fun abortOta() {
        sendControl("""{"cmd":"ota_abort"}""") { /* resposta via cmd_reply */ }
    }

    /** Escreve um chunk binÃ¡rio na caracterÃ­stica OTA. */
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

    // ---------------- PersistÃªncia ----------------

    private fun onInventory(json: String) {
        val tools = InventoryParser.parseInventory(json)
        scope.launch {
            try {
                repository.saveInventory(tools)
                repository.insertScanSession(
                    app.trakr.model.ScanSession(
                        ts = System.currentTimeMillis(),
                        connectedTrackers = sessions.size,
                        toolsSeen = tools.count { it.present },
                        toolsTotal = tools.size,
                        triggeredBy = "inventory",
                    ),
                )
            } catch (e: Exception) {
                Log.w(TAG, "Falha ao salvar inventario local", e)
            }
        }
    }

    private fun onEvent(json: String) {
        InventoryParser.parseRadarReport(json)?.let { report ->
            _radarReport.value = report
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
                        Log.w(TAG, "Falha ao espelhar radar_report", e)
                    }
                }
            }
            return
        }
        InventoryParser.parseLiveReport(json)?.let { live ->
            _liveReport.value = live
            scope.launch {
                live.reads.forEach { r ->
                    try {
                        repository.recordRssi(r.tag, r.rssi)
                    } catch (_: Exception) {
                    }
                }
            }
            return
        }
        InventoryParser.parseMultiReport(json)?.let { multi ->
            _multiReport.value = multi
            return
        }

        InventoryParser.parseCmdReply(json)?.let { _lastReply.value = it }
    }
}
