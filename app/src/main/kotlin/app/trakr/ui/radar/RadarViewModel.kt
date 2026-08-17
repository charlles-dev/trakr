package app.trakr.ui.radar

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
import app.trakr.model.RadarReport
import app.trakr.model.Tool
import app.trakr.repository.ToolRepository
import app.trakr.ui.UiMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class RadarViewModel(
    private val repository: ToolRepository,
    private val ble: BleGateway,
) : ViewModel() {
    val devices: StateFlow<List<BleDeviceInfo>> = ble.devices
    val radarReport: StateFlow<RadarReport?> = ble.radarReport
    val liveReport = ble.liveReport
    val multiReport = ble.multiReport
    val tools: Flow<List<Tool>> = repository.observeTools()

    private val toolsSnapshot = MutableStateFlow<List<Tool>>(emptyList())

    private val _targetId = MutableStateFlow<String?>(null)
    val targetId: StateFlow<String?> = _targetId.asStateFlow()

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    private val simulationRunningState = MutableStateFlow(false)
    val simulationRunning: StateFlow<Boolean> = simulationRunningState.asStateFlow()

    private val simulatedReportState = MutableStateFlow<RadarReport?>(null)

    val activeReport: StateFlow<RadarReport?> =
        combine(radarReport, simulatedReportState) { real, sim ->
            real ?: sim
        }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _rssiHistory = MutableStateFlow<List<Int>>(emptyList())
    val rssiHistory: StateFlow<List<Int>> = _rssiHistory.asStateFlow()

    private val _message = MutableStateFlow<UiMessage?>(null)
    val message: StateFlow<UiMessage?> = _message.asStateFlow()

    val hasRadarDevice: StateFlow<Boolean> =
        devices.map { it.isNotEmpty() }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    init {
        viewModelScope.launch {
            activeReport.collectLatest { report ->
                if (report != null && report.present) {
                    val current = _rssiHistory.value.toMutableList()
                    current.add(report.rssi)
                    if (current.size > 30) current.removeAt(0)
                    _rssiHistory.value = current
                } else if (!_running.value && !simulationRunningState.value) {
                    _rssiHistory.value = emptyList()
                }
            }
        }
    }

    private var simulationJob: Job? = null

    fun selectTarget(id: String) {
        _targetId.value = id
    }

    fun consumeMessage() {
        _message.value = null
    }

    fun start() {
        val id = _targetId.value
        val tool =
            toolsSnapshot.value.firstOrNull { it.id == id }
                ?: toolsSnapshot.value.firstOrNull()
                ?: Tool(id = "demo", name = "Alvo de Teste", epc = "E280116001")

        if (_targetId.value == null) {
            _targetId.value = tool.id
        }

        if (devices.value.isEmpty()) {
            startSimulation(tool)
            return
        }

        _running.value = true
        ble.startRadar(tool.id, tool.epc) {
            _running.value = false
            _message.value = UiMessage(R.string.msg_no_device)
        }
    }

    fun stop() {
        _running.value = false
        simulationRunningState.value = false
        simulationJob?.cancel()
        simulationJob = null
        simulatedReportState.value = null
        ble.stopRadar { }
        ble.stopLive { }
    }

    fun startSimulation(tool: Tool? = null) {
        stop()
        val targetTool =
            tool ?: toolsSnapshot.value.firstOrNull() ?: Tool(id = "demo", name = "Alvo de Teste", epc = "E280116001")
        simulationRunningState.value = true
        simulationJob =
            viewModelScope.launch {
                var currentRssi = -80
                var step = 1
                while (isActive) {
                    delay(700)
                    currentRssi += (step * (2..6).random())
                    if (currentRssi >= -35) {
                        step = -1
                    } else if (currentRssi <= -78) {
                        step = 1
                    }
                    val hint =
                        when {
                            currentRssi > -45 -> "hold"
                            step > 0 -> "continue"
                            else -> "turn_around"
                        }
                    simulatedReportState.value =
                        RadarReport(
                            tag = targetTool.epc,
                            rssi = currentRssi,
                            delta = if (step > 0) (1..4).random() else -(1..4).random(),
                            present = true,
                            hint = hint,
                        )
                }
            }
    }

    fun startLive() {
        _running.value = true
        ble.startLive(500) {
            _running.value = false
            _message.value = UiMessage(R.string.msg_no_device)
        }
    }

    fun startMulti() {
        val epcs = toolsSnapshot.value.map { it.epc }.filter { it.isNotBlank() }
        if (epcs.isEmpty()) {
            _message.value = UiMessage(R.string.msg_no_device)
            return
        }
        _running.value = true
        ble.startMultiRadar(epcs) {
            _running.value = false
            _message.value = UiMessage(R.string.msg_no_device)
        }
    }

    init {
        viewModelScope.launch {
            tools.collect { toolsSnapshot.value = it }
        }
        viewModelScope.launch {
            ble.lastReply.collectLatest { reply ->
                when (reply?.cmd) {
                    "start_radar" ->
                        if (reply.status == "error") {
                            _running.value = false
                            _message.value =
                                when (reply.reason) {
                                    "tool_not_found" -> UiMessage(R.string.msg_target_not_found)
                                    "unknown_cmd" -> UiMessage(R.string.msg_radar_unsupported)
                                    else -> UiMessage(R.string.msg_start_refused)
                                }
                        }
                    "stop_radar", "stop_live" -> _running.value = false
                    "start_live", "start_radar_multi", "start_multi" -> {
                        if (reply.status == "error") {
                            _running.value = false
                            _message.value = UiMessage(R.string.msg_start_refused)
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
                    RadarViewModel(
                        ToolRepository(AppContainer.database.toolDao()),
                        BleManager,
                    )
                }
            }
    }
}
