package app.trakr.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.trakr.core.ble.BleDeviceInfo
import app.trakr.core.ble.BleGateway
import app.trakr.core.ble.BleManager
import app.trakr.core.ble.BleStatus
import app.trakr.data.AppContainer
import app.trakr.model.Tool
import app.trakr.repository.ToolboxRepository
import app.trakr.ui.UiMessage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class DashboardViewModel(
    private val repository: ToolboxRepository,
    private val ble: BleGateway,
) : ViewModel() {
    /** Ferramentas ordenadas com as presentes primeiro (e depois por nome). */
    val tools: Flow<List<Tool>> =
        repository.observeTools().map { list ->
            list.sortedWith(compareByDescending<Tool> { it.present }.thenBy { it.name })
        }

    val devices: StateFlow<List<BleDeviceInfo>> = ble.devices

    val status: StateFlow<BleStatus> = ble.status

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val _message = MutableStateFlow<UiMessage?>(null)
    val message: StateFlow<UiMessage?> = _message.asStateFlow()

    fun consumeMessage() {
        _message.value = null
    }

    /** Pede um novo ciclo de scan ao motor BLE (pull-to-refresh). */
    fun refresh() {
        if (_refreshing.value) return
        _refreshing.value = true
        ble.rescan()
        viewModelScope.launch {
            delay(1500)
            _refreshing.value = false
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    DashboardViewModel(
                        ToolboxRepository(AppContainer.database.toolboxDao()),
                        BleManager,
                    )
                }
            }
    }
}
