package app.trakr.ui.radar

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.trakr.R
import app.trakr.core.ble.BleDeviceInfo
import app.trakr.core.ble.BleManager
import app.trakr.data.AppContainer
import app.trakr.model.RadarReport
import app.trakr.model.Tool
import app.trakr.repository.ToolboxRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class RadarViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = ToolboxRepository(AppContainer.database.toolboxDao())

    val devices: StateFlow<List<BleDeviceInfo>> = BleManager.devices

    /** Relatório mais recente do modo radar. */
    val radarReport: StateFlow<RadarReport?> = BleManager.radarReport

    /** Ferramentas cadastradas (fonte para escolher o alvo). */
    val tools: Flow<List<Tool>> = repository.observeTools()

    /** Snapshot das ferramentas (para enviar id + tag ao firmware). */
    private val toolsSnapshot = MutableStateFlow<List<Tool>>(emptyList())

    private val _targetId = MutableStateFlow<String?>(null)
    val targetId: StateFlow<String?> = _targetId.asStateFlow()

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    /** True quando existe pelo menos um TRK-Finder conectado. */
    val hasRadarDevice: StateFlow<Boolean> =
        devices
            .map { it.isNotEmpty() }
            .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, false)

    fun selectTarget(id: String) {
        _targetId.value = id
    }

    fun consumeMessage() {
        _message.value = null
    }

    fun start() {
        val id = _targetId.value
        val tool = toolsSnapshot.value.firstOrNull { it.id == id }
        if (id == null || tool == null) {
            _message.value = getApplication<Application>().getString(R.string.msg_choose_target)
            return
        }
        // Otimista: se o firmware recusar (ex.: ferramenta inexistente),
        // o ACK abaixo reverte o estado e informa o motivo.
        _running.value = true
        BleManager.startRadar(id, tool.epc) {
            _running.value = false
            _message.value = getApplication<Application>().getString(R.string.msg_no_device)
        }
    }

    fun stop() {
        _running.value = false
        BleManager.stopRadar { /* silencioso: o radar pode já ter parado */ }
    }

    init {
        viewModelScope.launch {
            tools.collect { toolsSnapshot.value = it }
        }
        viewModelScope.launch {
            BleManager.lastReply.collectLatest { reply ->
                when (reply?.cmd) {
                    "start_radar" ->
                        if (reply.status == "error") {
                            _running.value = false
                            _message.value =
                                when (reply.reason) {
                                    "tool_not_found" ->
                                        getApplication<Application>()
                                            .getString(R.string.msg_target_not_found)
                                    "unknown_cmd" ->
                                        getApplication<Application>()
                                            .getString(R.string.msg_radar_unsupported)
                                    else ->
                                        getApplication<Application>()
                                            .getString(R.string.msg_start_refused)
                                }
                        }
                    "stop_radar" -> _running.value = false
                }
            }
        }
    }
}
