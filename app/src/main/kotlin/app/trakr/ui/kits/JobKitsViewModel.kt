package app.trakr.ui.kits

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.trakr.core.ble.BleGateway
import app.trakr.core.ble.BleManager
import app.trakr.data.AppContainer
import app.trakr.model.JobKit
import app.trakr.model.Tool
import app.trakr.repository.ToolRepository
import app.trakr.ui.UiMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

class JobKitsViewModel(
    private val repository: ToolRepository,
    private val ble: BleGateway,
) : ViewModel() {
    val allKits: StateFlow<List<JobKit>> =
        repository.observeJobKits().stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList(),
        )

    val allTools: StateFlow<List<Tool>> =
        repository.observeTools().stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList(),
        )

    private val _activeMissionKit = MutableStateFlow<JobKit?>(null)
    val activeMissionKit: StateFlow<JobKit?> = _activeMissionKit.asStateFlow()

    private val _message = MutableStateFlow<UiMessage?>(null)
    val message: StateFlow<UiMessage?> = _message.asStateFlow()

    fun consumeMessage() {
        _message.value = null
    }

    fun createKit(
        name: String,
        description: String,
        selectedToolIds: List<String>,
    ) {
        if (name.isBlank() || selectedToolIds.isEmpty()) {
            _message.value = UiMessage("Nome do kit e ao menos 1 ferramenta são obrigatórios")
            return
        }
        val kit =
            JobKit(
                id = UUID.randomUUID().toString(),
                name = name.trim(),
                description = description.trim(),
                toolIdsCsv = selectedToolIds.joinToString(","),
            )
        viewModelScope.launch {
            repository.saveJobKit(kit)
            _message.value = UiMessage("Kit '${kit.name}' criado com sucesso!")
        }
    }

    fun deleteKit(id: String) {
        viewModelScope.launch {
            repository.deleteJobKit(id)
            if (_activeMissionKit.value?.id == id) {
                _activeMissionKit.value = null
            }
            _message.value = UiMessage("Kit removido")
        }
    }

    fun startMission(kit: JobKit) {
        _activeMissionKit.value = kit
        ble.rescan()
        _message.value = UiMessage("Missão iniciada com o kit '${kit.name}'")
    }

    fun finishMission() {
        _activeMissionKit.value = null
        _message.value = UiMessage("Missão concluída!")
    }

    companion object {
        val Factory: ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    val dao = AppContainer.database.toolDao()
                    JobKitsViewModel(ToolRepository(dao), BleManager)
                }
            }
    }
}
