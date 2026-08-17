package app.trakr.ui.tools

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.trakr.core.ble.BleGateway
import app.trakr.core.ble.BleManager
import app.trakr.data.AppContainer
import app.trakr.model.RssiSample
import app.trakr.model.ToolEvent
import app.trakr.repository.ToolRepository
import app.trakr.ui.UiMessage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ToolDetailViewModel(
    private val repository: ToolRepository,
    private val ble: BleGateway,
) : ViewModel() {
    private val epc = MutableStateFlow<String?>(null)
    private val toolId = MutableStateFlow<String?>(null)
    private val _isLocating = MutableStateFlow(false)
    val isLocating: StateFlow<Boolean> = _isLocating.asStateFlow()

    private val _message = MutableStateFlow<UiMessage?>(null)
    val message: StateFlow<UiMessage?> = _message.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val samples: StateFlow<List<RssiSample>> =
        epc.flatMapLatest { value ->
            if (value.isNullOrBlank()) {
                flowOf(emptyList())
            } else {
                repository.observeRssiSamples(value, limit = 50)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val events: StateFlow<List<ToolEvent>> =
        toolId.flatMapLatest { value ->
            if (value.isNullOrBlank()) {
                flowOf(emptyList())
            } else {
                repository.observeToolEvents(value, limit = 50)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setEpc(value: String) {
        epc.value = value
    }

    fun setToolId(id: String) {
        toolId.value = id
    }

    fun startLocating(toolEpc: String) {
        val tid = toolId.value ?: toolEpc
        _isLocating.value = true
        ble.startRadar(tid, toolEpc) {
            _isLocating.value = false
            _message.value = UiMessage("TRK-Finder não conectado. Conecte via BLE para buscar.")
        }
        viewModelScope.launch {
            repository.recordToolEvent(
                toolId = tid,
                eventType = "SCAN",
                details = "Busca física iniciada no TRK-Finder",
            )
        }
    }

    fun stopLocating() {
        _isLocating.value = false
        ble.stopRadar {
            // Callback se offline
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    companion object {
        val Factory: ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    ToolDetailViewModel(
                        ToolRepository(AppContainer.database.toolDao()),
                        BleManager,
                    )
                }
            }
    }
}
