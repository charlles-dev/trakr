package app.trakr.ui.alerts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.trakr.R
import app.trakr.data.AppContainer
import app.trakr.model.AlertEvent
import app.trakr.model.Tool
import app.trakr.repository.ToolboxRepository
import app.trakr.ui.UiMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AlertsViewModel(
    private val repository: ToolboxRepository,
) : ViewModel() {
    val alerts: Flow<List<AlertEvent>> = repository.observeAlerts()

    /** Inventário atual (para o alerta abrir a ferramenta correspondente). */
    val tools: Flow<List<Tool>> = repository.observeTools()

    private val _message = MutableStateFlow<UiMessage?>(null)
    val message: StateFlow<UiMessage?> = _message.asStateFlow()

    fun consumeMessage() {
        _message.value = null
    }

    fun markRead(alert: AlertEvent) {
        if (alert.read) return
        viewModelScope.launch {
            try {
                repository.markAlertRead(alert.id)
            } catch (e: Exception) {
                // falha silenciosa: apenas o destaque visual deixa de atualizar
            }
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            try {
                repository.clearAlerts()
                _message.value = UiMessage(R.string.alerts_cleared)
            } catch (e: Exception) {
                _message.value = UiMessage(R.string.msg_generic_error)
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    AlertsViewModel(ToolboxRepository(AppContainer.database.toolboxDao()))
                }
            }
    }
}
