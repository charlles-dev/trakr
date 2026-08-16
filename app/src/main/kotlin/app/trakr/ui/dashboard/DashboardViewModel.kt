package app.trakr.ui.dashboard

import androidx.lifecycle.ViewModel
import app.trakr.core.ble.BleDeviceInfo
import app.trakr.core.ble.BleManager
import app.trakr.data.AppContainer
import app.trakr.model.Tool
import app.trakr.repository.ToolboxRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class DashboardViewModel : ViewModel() {
    private val repository = ToolboxRepository(AppContainer.database.toolboxDao())

    val tools: Flow<List<Tool>> = repository.observeTools()

    val devices: StateFlow<List<BleDeviceInfo>> = BleManager.devices

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun consumeMessage() {
        _message.value = null
    }
}
