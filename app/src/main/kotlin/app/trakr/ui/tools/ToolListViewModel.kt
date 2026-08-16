package app.trakr.ui.tools

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.trakr.R
import app.trakr.core.ble.BleGateway
import app.trakr.core.ble.BleManager
import app.trakr.data.AppContainer
import app.trakr.model.Tool
import app.trakr.repository.ToolboxRepository
import app.trakr.ui.UiMessage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.UUID

class ToolListViewModel(
    private val repository: ToolboxRepository,
    private val ble: BleGateway,
) : ViewModel() {
    /** Ferramentas ordenadas com as presentes primeiro (e depois por nome). */
    val tools: Flow<List<Tool>> =
        repository.observeTools().map { list ->
            list.sortedWith(compareByDescending<Tool> { it.present }.thenBy { it.name })
        }

    /** Mensagem de status para o usuário (Snackbar). */
    private val _message = MutableStateFlow<UiMessage?>(null)
    val message: StateFlow<UiMessage?> = _message.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

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

    fun addTool(
        name: String,
        epc: String,
    ) {
        val cleanName = name.trim()
        val cleanEpc = epc.trim().uppercase()
        if (cleanName.isEmpty() || cleanEpc.isEmpty()) {
            _message.value = UiMessage(R.string.msg_required_fields)
            return
        }
        val tool =
            Tool(
                id = UUID.randomUUID().toString(),
                name = cleanName,
                epc = cleanEpc,
                present = false,
            )
        ble.addTool(tool.name, tool.epc) {
            viewModelScope.launch {
                try {
                    repository.addToolLocal(tool)
                    _message.value = UiMessage(R.string.msg_saved_local)
                } catch (e: Exception) {
                    _message.value = UiMessage(R.string.msg_generic_error)
                }
            }
        }
        _message.value = UiMessage(R.string.msg_adding, listOf(tool.name))
    }

    /** Envia {remove_tool} (id + epc); fallback local se desconectado. */
    fun removeTool(tool: Tool) {
        ble.removeTool(tool.id, tool.epc) {
            viewModelScope.launch {
                try {
                    repository.removeToolLocal(tool.id)
                    _message.value = UiMessage(R.string.msg_removed_local)
                } catch (e: Exception) {
                    _message.value = UiMessage(R.string.msg_generic_error)
                }
            }
        }
        _message.value = UiMessage(R.string.msg_removing, listOf(tool.name))
    }

    init {
        // Consome os ACKs do firmware para confirmar add/remove no rastreador.
        viewModelScope.launch {
            ble.lastReply.collectLatest { reply ->
                when (reply?.cmd) {
                    "add_tool" ->
                        if (reply.status == "ok") {
                            _message.value = UiMessage(R.string.msg_added_remote)
                        } else {
                            _message.value = UiMessage(R.string.msg_generic_error)
                        }
                    "remove_tool" ->
                        if (reply.status == "ok") {
                            _message.value = UiMessage(R.string.msg_removed_remote)
                        } else {
                            _message.value = UiMessage(R.string.msg_generic_error)
                        }
                }
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    ToolListViewModel(
                        ToolboxRepository(AppContainer.database.toolboxDao()),
                        BleManager,
                    )
                }
            }
    }
}
