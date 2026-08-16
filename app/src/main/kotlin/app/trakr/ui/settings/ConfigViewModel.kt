package app.trakr.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.trakr.R
import app.trakr.core.ble.BleDeviceInfo
import app.trakr.core.ble.BleGateway
import app.trakr.core.ble.BleManager
import app.trakr.data.AppContainer
import app.trakr.data.InventoryParser
import app.trakr.data.InventoryParser.TrackerConfig
import app.trakr.repository.ToolRepository
import app.trakr.ui.UiMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ConfigViewModel(
    private val ble: BleGateway,
    private val repository: ToolRepository,
) : ViewModel() {
    val devices: StateFlow<List<BleDeviceInfo>> = ble.devices
    val bleStatus: StateFlow<app.trakr.core.ble.BleStatus> = ble.status
    val tools: Flow<List<app.trakr.model.Tool>> = repository.observeTools()

    private val _config = MutableStateFlow<TrackerConfig?>(null)
    val config: StateFlow<TrackerConfig?> = _config.asStateFlow()

    private val _message = MutableStateFlow<UiMessage?>(null)
    val message: StateFlow<UiMessage?> = _message.asStateFlow()

    fun consumeMessage() {
        _message.value = null
    }

    fun loadConfig() {
        ble.getConfig {
            _message.value = UiMessage(R.string.msg_no_device)
        }
    }

    fun setBeep(enabled: Boolean) {
        ble.setConfig(mapOf("beep" to enabled)) {
            _message.value = UiMessage(R.string.msg_no_device)
        }
    }

    fun setListenMs(ms: Int) {
        ble.setConfig(mapOf("listen_ms" to ms)) {
            _message.value = UiMessage(R.string.msg_no_device)
        }
    }

    fun setRadarMs(ms: Int) {
        ble.setConfig(mapOf("radar_ms" to ms)) {
            _message.value = UiMessage(R.string.msg_no_device)
        }
    }

    fun setTxPower(dbm: Int) {
        ble.setConfig(mapOf("tx_power_dbm" to dbm)) {
            _message.value = UiMessage(R.string.msg_no_device)
        }
    }

    fun setRssiOffset(offset: Int) {
        ble.setConfig(mapOf("rssi_offset" to offset)) {
            _message.value = UiMessage(R.string.msg_no_device)
        }
    }

    fun setRssiThreshold(th: Int) {
        ble.setConfig(mapOf("rssi_threshold" to th)) {
            _message.value = UiMessage(R.string.msg_no_device)
        }
    }

    fun setEnvProfile(env: String) {
        ble.setConfig(mapOf("env_profile" to env)) {
            _message.value = UiMessage(R.string.msg_no_device)
        }
    }

    fun auth(pin: String) {
        ble.auth(pin) {
            _message.value = UiMessage(R.string.msg_no_device)
        }
    }

    fun setPin(pin: String) {
        ble.setConfig(mapOf("pin" to pin)) {
            _message.value = UiMessage(R.string.msg_no_device)
        }
    }

    fun clearPin() {
        setPin("")
    }

    fun rescan() {
        ble.rescan()
    }

    data class SensorInfo(
        val hasOled: Boolean = false,
        val hasIna219: Boolean = false,
        val hasBme280: Boolean = false,
        val hasMpu: Boolean = false,
        val hasVib: Boolean = false,
        val hasBtn2: Boolean = false,
        val txPowerDbm: Int = 26,
        val rssiOffset: Int = 0,
        val env: String = "default",
    )

    private val _sensors = MutableStateFlow<SensorInfo?>(null)
    val sensors: StateFlow<SensorInfo?> = _sensors.asStateFlow()

    private val _addons = MutableStateFlow<List<String>>(emptyList())
    val addons: StateFlow<List<String>> = _addons.asStateFlow()

    val allToolSettings = repository.observeAllToolSettings()
    val trackerMutes = repository.observeTrackerMutes()

    fun setToolMuted(
        toolId: String,
        muted: Boolean,
    ) {
        viewModelScope.launch {
            val current =
                try {
                    repository.let {
                        AppContainer.database.toolDao().getAllToolSettings()
                            .find { s -> s.toolId == toolId }
                    }
                } catch (_: Exception) {
                    null
                } ?: repository.let { null }
            // Fallback: cria novo se nao existe
            val newSetting =
                app.trakr.model.ToolAlertSetting(
                    toolId = toolId,
                    muted = muted,
                )
            repository.upsertToolSetting(newSetting)
        }
    }

    fun setTrackerMuted(
        address: String,
        muted: Boolean,
    ) {
        viewModelScope.launch {
            repository.setTrackerMute(
                app.trakr.model.TrackerMute(address = address, muted = muted),
            )
        }
    }

    private val _backupJson = MutableStateFlow<String?>(null)
    val backupJson: StateFlow<String?> = _backupJson.asStateFlow()

    fun loadSensors() {
        ble.getSensors {
            _message.value = UiMessage(R.string.msg_no_device)
        }
        ble.getAddons {
            _message.value = UiMessage(R.string.msg_no_device)
        }
    }

    fun exportBackup() {
        viewModelScope.launch {
            try {
                val json = repository.exportBackupJson()
                _backupJson.value = json
                _message.value = UiMessage(R.string.msg_backup_exported)
            } catch (e: Exception) {
                _message.value = UiMessage(R.string.msg_backup_failed)
            }
        }
    }

    fun importBackup(json: String) {
        viewModelScope.launch {
            try {
                val ok = repository.importBackupJson(json)
                _message.value =
                    if (ok) {
                        UiMessage(R.string.msg_backup_imported)
                    } else {
                        UiMessage(R.string.msg_backup_failed)
                    }
                if (ok) loadConfig()
            } catch (e: Exception) {
                _message.value = UiMessage(R.string.msg_backup_failed)
            }
        }
    }

    fun consumeBackup() {
        _backupJson.value = null
    }

    init {
        viewModelScope.launch {
            ble.devices.collect { devices ->
                if (devices.isNotEmpty() && _config.value == null) loadConfig()
            }
        }
        viewModelScope.launch {
            ble.lastReply.collectLatest { reply ->
                when (reply?.cmd) {
                    "get_config", "set_config" -> {
                        if (reply.status == "ok") {
                            val cfg = reply.let(InventoryParser::trackerConfig)
                            if (cfg != null) {
                                _config.value = cfg
                            }
                            if (reply.cmd == "set_config") {
                                loadConfig()
                            }
                        } else {
                            when (reply.reason) {
                                "auth_required" ->
                                    _message.value =
                                        UiMessage(R.string.msg_auth_required)
                                "auth_failed" ->
                                    _message.value =
                                        UiMessage(R.string.msg_auth_failed)
                                else ->
                                    _message.value =
                                        UiMessage(R.string.msg_config_failed)
                            }
                        }
                    }
                    "auth" -> {
                        if (reply.status == "ok") {
                            _message.value = UiMessage(R.string.msg_auth_ok)
                            loadConfig()
                        } else {
                            _message.value = UiMessage(R.string.msg_auth_failed)
                        }
                    }
                    "get_sensors" -> {
                        if (reply.status == "ok") {
                            val p = reply.payload
                            if (p != null) {
                                _sensors.value =
                                    SensorInfo(
                                        hasOled = p.optBoolean("has_oled", false),
                                        hasIna219 = p.optBoolean("has_ina219", false),
                                        hasBme280 = p.optBoolean("has_bme280", false),
                                        hasMpu = p.optBoolean("has_mpu", false),
                                        hasVib = p.optBoolean("has_vib", false),
                                        hasBtn2 = p.optBoolean("has_btn2", false),
                                        txPowerDbm = p.optInt("tx_power_dbm", 26),
                                        rssiOffset = p.optInt("rssi_offset", 0),
                                        env = p.optString("env", "default"),
                                    )
                            }
                        }
                    }
                    "get_addons" -> {
                        if (reply.status == "ok") {
                            val p = reply.payload
                            val arr = p?.optJSONArray("addons")
                            if (arr != null) {
                                val list = mutableListOf<String>()
                                for (i in 0 until arr.length()) list += arr.optString(i)
                                _addons.value = list
                            }
                        }
                    }
                }
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    val dao = AppContainer.database.toolDao()
                    ConfigViewModel(BleManager, ToolRepository(dao))
                }
            }
    }
}
