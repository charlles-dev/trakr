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
import app.trakr.data.InventoryParser
import app.trakr.data.InventoryParser.TrackerConfig
import app.trakr.ui.UiMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ConfigViewModel(
    private val ble: BleGateway,
) : ViewModel() {
    val devices: StateFlow<List<BleDeviceInfo>> = ble.devices

    /** Configurações atuais do TRK-Finder, ou null antes do primeiro get. */
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

    init {
        // Carrega a config assim que um rastreador conectar (e só uma vez:
        // o usuário recarrega manualmente pelo botão se quiser).
        viewModelScope.launch {
            ble.devices.collect { devices ->
                if (devices.isNotEmpty() && _config.value == null) loadConfig()
            }
        }
        viewModelScope.launch {
            ble.lastReply.collectLatest { reply ->
                when (reply?.cmd) {
                    "get_config", "set_config" -> {
                        val config = reply.let(InventoryParser::trackerConfig)
                        if (config != null) {
                            _config.value = config
                        } else {
                            _message.value = UiMessage(R.string.msg_config_failed)
                        }
                    }
                }
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory =
            viewModelFactory {
                initializer { ConfigViewModel(BleManager) }
            }
    }
}
