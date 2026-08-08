package app.trakr.ui.dashboard

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.trakr.core.ble.BleDeviceInfo
import app.trakr.core.ble.BleManager
import app.trakr.data.AppContainer
import app.trakr.model.EventRecord
import app.trakr.model.Tool
import app.trakr.model.Toolbox
import app.trakr.model.ToolboxStore
import app.trakr.model.ToolboxStore.Selection
import app.trakr.repository.ToolboxRepository
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ToolboxViewModel : ViewModel() {

    private val repository = ToolboxRepository(AppContainer.database.toolboxDao())

    val toolboxes: Flow<List<Toolbox>> = repository.observeToolboxes()

    val current: StateFlow<Selection> = ToolboxStore.current

    val devices: StateFlow<List<BleDeviceInfo>> = BleManager.devices

    val tools: Flow<List<Tool>> = ToolboxStore.current
        .flatMapLatest { selection -> repository.observeTools(selection.id) }

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun consumeMessage() {
        _message.value = null
    }

    /** Troca a maleta ativa local e tenta sincronizar o firmware. */
    fun selectToolbox(selection: Selection) {
        ToolboxStore.select(selection)
        registerLocally(selection)
        BleManager.selectToolbox(selection.id)
        _message.value = "Maleta ativa: ${selection.name}"
    }

    /** Cria uma maleta nova (id slug da fonte de verdade) e seleciona. */
    fun createToolbox(name: String) {
        val clean = name.trim()
        if (clean.isEmpty()) {
            _message.value = "Nome da maleta é obrigatório"
            return
        }
        val id = clean.lowercase().replace(Regex("[^a-z0-9-]"), "-")
        val selection = Selection(id = id, name = clean)
        ToolboxStore.select(selection)
        registerLocally(selection)
        BleManager.selectToolbox(id)
        _message.value = "Maleta \"$clean\" criada e selecionada"
    }

    private fun registerLocally(selection: Selection) {
        viewModelScope.launch {
            repository.upsertToolboxLocal(
                Toolbox(id = selection.id, name = selection.name),
            )
        }
    }

    /**
     * Exporta o histórico da maleta ativa como CSV no cache e devolve a Uri
     * via FileProvider para o app compartilhar.
     */
    fun exportHistory(context: Context, onReady: (Uri) -> Unit) {
        viewModelScope.launch {
            val selection = ToolboxStore.current.value
            val events = AppContainer.database.toolboxDao().getEvents(selection.id)
            val uri = writeCsv(context, selection, events)
            onReady(uri)
        }
    }

    private fun writeCsv(
        context: Context,
        selection: Selection,
        events: List<EventRecord>,
    ): Uri {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "trakr_${selection.id}_history.csv")
        val sb = StringBuilder("ts;type;tool_id;tool_name;created_at\n")
        events.forEach { e ->
            sb.append("${e.ts};${e.type};${e.toolId};${e.toolName};${e.createdAt}\n")
        }
        file.writeText(sb.toString())
        return androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
    }
}