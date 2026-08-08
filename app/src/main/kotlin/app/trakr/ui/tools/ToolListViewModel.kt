package app.trakr.ui.tools

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.trakr.core.ble.BleManager
import app.trakr.data.AppContainer
import app.trakr.model.Tool
import app.trakr.model.ToolboxStore
import app.trakr.repository.ToolboxRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ToolListViewModel : ViewModel() {

    private val repository = ToolboxRepository(AppContainer.database.toolboxDao())

    val tools: Flow<List<Tool>> = ToolboxStore.current
        .flatMapLatest { selection -> repository.observeTools(selection.id) }

    /** Mensagem de status para o usuário (Snackbar). */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun consumeMessage() {
        _message.value = null
    }

    fun addTool(name: String, epc: String) {
        val cleanName = name.trim()
        val cleanEpc = epc.trim().uppercase()
        if (cleanName.isEmpty() || cleanEpc.isEmpty()) {
            _message.value = "Nome e tag são obrigatórios"
            return
        }
        val toolboxId = ToolboxStore.current.value.id
        val tool = Tool(
            id = UUID.randomUUID().toString(),
            toolboxId = toolboxId,
            name = cleanName,
            epc = cleanEpc,
            present = true,
        )
        BleManager.addTool(tool.name, tool.epc) {
            viewModelScope.launch {
                repository.addToolLocal(tool)
                _message.value = "Maleta offline — salvo apenas no app"
            }
        }
        _message.value = "Adicionando \"${tool.name}\"..."
    }

    /** Envia {remove_tool}; fallback local se desconectado. */
    fun removeTool(tool: Tool) {
        BleManager.removeTool(tool.id) {
            viewModelScope.launch {
                repository.removeToolLocal(tool.id)
                _message.value = "Maleta offline — removido apenas no app"
            }
        }
        _message.value = "Removendo \"${tool.name}\"..."
    }
}