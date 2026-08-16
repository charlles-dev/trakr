package app.trakr.ui.alerts

import androidx.lifecycle.ViewModel
import app.trakr.data.AppContainer
import app.trakr.model.AlertEvent
import app.trakr.repository.ToolboxRepository
import kotlinx.coroutines.flow.Flow

class AlertsViewModel : ViewModel() {
    private val repository = ToolboxRepository(AppContainer.database.toolboxDao())

    val alerts: Flow<List<AlertEvent>> = repository.observeAlerts()
}
