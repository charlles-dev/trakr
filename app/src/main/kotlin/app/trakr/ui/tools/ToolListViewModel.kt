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
import app.trakr.repository.ToolRepository
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
    private val repository: ToolRepository,
    private val ble: BleGateway,
) : ViewModel() {
    /** Ferramentas ordenadas com as presentes primeiro (e depois por nome). */
    val tools: Flow<List<Tool>> =
        repository.observeTools().map { list ->
            list.sortedWith(compareByDescending<Tool> { it.present }.thenBy { it.name })
        }

    /** Mensagem de status para o usuÃ¡rio (Snackbar). */
    private val _message = MutableStateFlow<UiMessage?>(null)
    val message: StateFlow<UiMessage?> = _message.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val _isCapturingTag = MutableStateFlow(false)
    val isCapturingTag: StateFlow<Boolean> = _isCapturingTag.asStateFlow()

    private val _capturedTag = MutableStateFlow<String?>(null)
    val capturedTag: StateFlow<String?> = _capturedTag.asStateFlow()

    fun consumeMessage() {
        _message.value = null
    }

    fun clearCapturedTag() {
        _capturedTag.value = null
    }

    /** Dispara leitura de tag por aproximação no Finder. */
    fun captureTagFromFinder() {
        if (_isCapturingTag.value) return
        _isCapturingTag.value = true
        _message.value = UiMessage(R.string.msg_approaching_tag)

        if (ble.devices.value.isEmpty()) {
            // Modo simulação se o hardware estiver desconectado
            viewModelScope.launch {
                delay(1200)
                _isCapturingTag.value = false
                val demoHex = "E280116060" + (10000000..99999999).random()
                _capturedTag.value = demoHex
                _message.value = UiMessage(R.string.msg_tag_captured)
            }
            return
        }

        ble.captureTag {
            _isCapturingTag.value = false
            _message.value = UiMessage(R.string.msg_no_device)
        }
    }

    /** Sincroniza todo o inventário local com a Flash LittleFS do TRK-Finder. */
    fun syncInventoryToFinder(currentTools: List<Tool>) {
        if (ble.devices.value.isEmpty()) {
            _message.value = UiMessage(R.string.msg_no_device)
            return
        }
        ble.syncInventory(currentTools) {
            _message.value = UiMessage(R.string.msg_no_device)
        }
        _message.value = UiMessage(R.string.msg_syncing_flash)
    }

    val isNfcReading: StateFlow<Boolean> = app.trakr.core.nfc.NfcReaderHelper.isReading
    val nfcTag: StateFlow<String?> = app.trakr.core.nfc.NfcReaderHelper.scannedTag

    fun startNfcScan(activity: android.app.Activity) {
        if (!app.trakr.core.nfc.NfcReaderHelper.isNfcAvailable(activity)) {
            _message.value = UiMessage("NFC desativado ou não disponível neste aparelho")
            return
        }
        _message.value = UiMessage("Aproxime a tag NFC na traseira do celular...")
        app.trakr.core.nfc.NfcReaderHelper.startListening(activity) { tag ->
            _message.value = UiMessage("Tag NFC capturada: ${tag.takeLast(8)}")
        }
    }

    fun stopNfcScan(activity: android.app.Activity) {
        app.trakr.core.nfc.NfcReaderHelper.stopListening(activity)
    }

    fun clearNfcTag() {
        app.trakr.core.nfc.NfcReaderHelper.clearScannedTag()
    }

    fun importBatch(
        context: android.content.Context,
        uri: android.net.Uri,
    ) {
        viewModelScope.launch {
            try {
                val tools = app.trakr.core.importexport.ToolImportHelper.parseToolsFromUri(context, uri)
                if (tools.isNotEmpty()) {
                    repository.importBatchTools(tools)
                    _message.value = UiMessage("${tools.size} ferramentas importadas com sucesso!")
                } else {
                    _message.value = UiMessage("Nenhuma ferramenta válida encontrada no arquivo")
                }
            } catch (e: Exception) {
                _message.value = UiMessage(R.string.msg_generic_error)
            }
        }
    }

    fun exportPdf(
        context: android.content.Context,
        currentTools: List<Tool>,
    ) {
        if (currentTools.isEmpty()) {
            _message.value = UiMessage(R.string.tools_empty)
            return
        }
        app.trakr.core.export.ToolPdfExportHelper.generateAndSharePdfReport(context, currentTools)
    }

    private val _selectedCategoryFilter = MutableStateFlow<String?>(null)
    val selectedCategoryFilter: StateFlow<String?> = _selectedCategoryFilter.asStateFlow()

    fun setCategoryFilter(filter: String?) {
        _selectedCategoryFilter.value = filter
    }

    fun exportReport(
        context: android.content.Context,
        currentTools: List<Tool>,
        format: String = "csv",
    ) {
        if (currentTools.isEmpty()) {
            _message.value = UiMessage(R.string.tools_empty)
            return
        }
        app.trakr.core.export.ToolExportHelper.shareAuditReport(context, currentTools, format)
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
        category: String = "manual",
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
                category = category.lowercase(),
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
        // Consome os ACKs do firmware para confirmar add/remove/capture no rastreador.
        viewModelScope.launch {
            ble.lastReply.collectLatest { reply ->
                when (reply?.cmd) {
                    "capture_tag" -> {
                        _isCapturingTag.value = false
                        if (reply.status == "ok") {
                            val tag = reply.payload?.optString("tag", "")
                            if (!tag.isNullOrBlank()) {
                                _capturedTag.value = tag
                                _message.value = UiMessage(R.string.msg_tag_captured)
                            }
                        } else {
                            _message.value = UiMessage(R.string.msg_no_tag_detected)
                        }
                    }
                    "sync_inventory" -> {
                        if (reply.status == "ok") {
                            _message.value = UiMessage(R.string.msg_sync_ok)
                        }
                    }
                    "add_tool" ->
                        if (reply.status == "ok") {
                            _message.value = UiMessage(R.string.msg_added_remote)
                        } else {
                            when (reply.reason) {
                                "auth_required" -> _message.value = UiMessage(R.string.msg_auth_required)
                                "auth_failed" -> _message.value = UiMessage(R.string.msg_auth_failed)
                                else -> _message.value = UiMessage(R.string.msg_generic_error)
                            }
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
                        ToolRepository(AppContainer.database.toolDao()),
                        BleManager,
                    )
                }
            }
    }
}
