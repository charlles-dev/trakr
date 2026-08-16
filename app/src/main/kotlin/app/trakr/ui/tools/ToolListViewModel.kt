package app.trakr.ui.tools

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.trakr.R
import app.trakr.core.ble.BleManager
import app.trakr.data.AppContainer
import app.trakr.model.Tool
import app.trakr.repository.ToolboxRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class ToolListViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = ToolboxRepository(AppContainer.database.toolboxDao())

    val tools: Flow<List<Tool>> = repository.observeTools()

    /** Mensagem de status para o usuário (Snackbar). */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun consumeMessage() {
        _message.value = null
    }

    fun addTool(
        name: String,
        epc: String,
    ) {
        val cleanName = name.trim()
        val cleanEpc = epc.trim().uppercase()
        if (cleanName.isEmpty() || cleanEpc.isEmpty()) {
            _message.value = getApplication<Application>().getString(R.string.msg_required_fields)
            return
        }
        val tool =
            Tool(
                id = UUID.randomUUID().toString(),
                name = cleanName,
                epc = cleanEpc,
                present = false,
            )
        BleManager.addTool(tool.name, tool.epc) {
            viewModelScope.launch {
                repository.addToolLocal(tool)
                _message.value =
                    getApplication<Application>()
                        .getString(R.string.msg_saved_local)
            }
        }
        _message.value = getApplication<Application>().getString(R.string.msg_adding, tool.name)
    }

    /** Envia {remove_tool} (id + epc); fallback local se desconectado. */
    fun removeTool(tool: Tool) {
        BleManager.removeTool(tool.id, tool.epc) {
            viewModelScope.launch {
                repository.removeToolLocal(tool.id)
                _message.value =
                    getApplication<Application>()
                        .getString(R.string.msg_removed_local)
            }
        }
        _message.value = getApplication<Application>().getString(R.string.msg_removing, tool.name)
    }
}
