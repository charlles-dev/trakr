@file:Suppress("ktlint:standard:max-line-length", "MaxLineLength")

package app.trakr.ui.tools

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Contactless
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.trakr.R
import app.trakr.model.Tool
import app.trakr.ui.components.CardActionButton
import app.trakr.ui.components.EmptyState
import app.trakr.ui.components.StatusBadge
import app.trakr.ui.components.ToolCard
import app.trakr.ui.components.maxContentWidth
import app.trakr.ui.motion.pressScale
import app.trakr.ui.theme.AlertRed
import app.trakr.ui.theme.NeonGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolListScreen(
    modifier: Modifier = Modifier,
    pendingToolId: String? = null,
    onPendingConsumed: () -> Unit = {},
    viewModel: ToolListViewModel = viewModel(factory = ToolListViewModel.Factory),
) {
    val tools by viewModel.tools.collectAsStateWithLifecycle(initialValue = emptyList())
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showAddDialog by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var selectedTool by remember { mutableStateOf<Tool?>(null) }
    var toolToRemove by remember { mutableStateOf<Tool?>(null) }

    // Deep link de notificação: abre direto o detalhe da ferramenta.
    LaunchedEffect(pendingToolId, tools) {
        if (pendingToolId != null) {
            tools.firstOrNull { it.id == pendingToolId }?.let {
                selectedTool = it
                onPendingConsumed()
            }
        }
    }

    selectedTool?.let { tool ->
        ToolDetailScreen(
            tool = tool,
            onBack = { selectedTool = null },
            modifier = Modifier.fillMaxSize(),
        )
        return
    }

    val context = LocalContext.current

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it.resolve(context))
            viewModel.consumeMessage()
        }
    }

    val selectedCategoryFilter by viewModel.selectedCategoryFilter.collectAsStateWithLifecycle()
    val isCapturingTag by viewModel.isCapturingTag.collectAsStateWithLifecycle()
    val capturedTag by viewModel.capturedTag.collectAsStateWithLifecycle()
    val isNfcReading by viewModel.isNfcReading.collectAsStateWithLifecycle()
    val nfcTag by viewModel.nfcTag.collectAsStateWithLifecycle()

    val fileImportLauncher =
        androidx.activity.compose.rememberLauncherForActivityResult(
            contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
        ) { uri ->
            uri?.let { viewModel.importBatch(context, it) }
        }

    val filtered =
        remember(tools, query, selectedCategoryFilter) {
            tools.filter { tool ->
                val matchesQuery =
                    query.isBlank() ||
                        tool.name.contains(query.trim(), ignoreCase = true) ||
                        tool.epc.contains(query.trim(), ignoreCase = true)
                val matchesCategory =
                    when (selectedCategoryFilter) {
                        null -> true
                        "absent" -> !tool.present
                        else -> tool.category.equals(selectedCategoryFilter, ignoreCase = true)
                    }
                matchesQuery && matchesCategory
            }
        }

    val filterOptions =
        listOf(
            null to stringResource(R.string.category_all),
            "absent" to stringResource(R.string.category_absent),
            "manual" to stringResource(R.string.category_manual),
            "eletrica" to stringResource(R.string.category_eletrica),
            "medicao" to stringResource(R.string.category_medicao),
            "epi" to stringResource(R.string.category_epi),
            "outro" to stringResource(R.string.category_outro),
        )

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tab_tools)) },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                    ),
                actions = {
                    IconButton(
                        onClick = { fileImportLauncher.launch(arrayOf("text/*", "application/json", "*/*")) },
                    ) {
                        Icon(
                            Icons.Filled.UploadFile,
                            contentDescription = "Importar Planilha / JSON",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(
                        onClick = { viewModel.exportPdf(context, tools) },
                        enabled = tools.isNotEmpty(),
                    ) {
                        Icon(
                            Icons.Filled.Description,
                            contentDescription = "Exportar PDF Formatado",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(
                        onClick = { viewModel.exportReport(context, tools) },
                        enabled = tools.isNotEmpty(),
                    ) {
                        Icon(
                            Icons.Filled.Share,
                            contentDescription = stringResource(R.string.action_export_report),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(
                        onClick = { viewModel.syncInventoryToFinder(tools) },
                        enabled = tools.isNotEmpty(),
                    ) {
                        Icon(
                            Icons.Filled.Refresh,
                            contentDescription = stringResource(R.string.action_sync_finder),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                modifier = Modifier.pressScale(pressedScale = 0.90f, spring = false),
            ) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = stringResource(R.string.action_add_tool),
                )
            }
        },
    ) { padding ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding),
            contentAlignment = Alignment.TopCenter,
        ) {
            PullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = viewModel::refresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .maxContentWidth()
                            .padding(horizontal = 16.dp),
                ) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text(stringResource(R.string.search_placeholder)) },
                        leadingIcon = {
                            Icon(
                                Icons.Filled.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        singleLine = true,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                    )

                    // Barra de chips de filtros por categoria
                    androidx.compose.foundation.lazy.LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                    ) {
                        items(filterOptions) { (catId, label) ->
                            val isSelected = selectedCategoryFilter == catId
                            androidx.compose.material3.FilterChip(
                                selected = isSelected,
                                onClick = { viewModel.setCategoryFilter(if (isSelected) null else catId) },
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                                colors =
                                    androidx.compose.material3.FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    ),
                            )
                        }
                    }

                    if (filtered.isEmpty()) {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight(),
                            contentAlignment = Alignment.Center,
                        ) {
                            EmptyState(
                                icon = if (query.isBlank()) Icons.Filled.Build else Icons.Filled.Search,
                                title =
                                    if (query.isBlank()) {
                                        stringResource(R.string.tools_empty)
                                    } else {
                                        stringResource(R.string.search_no_results)
                                    },
                                hint =
                                    if (query.isBlank()) {
                                        stringResource(R.string.tools_empty_hint)
                                    } else {
                                        stringResource(R.string.search_no_results_hint, query.trim())
                                    },
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 80.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            items(filtered, key = { it.id }) { tool ->
                                ToolCard(
                                    tool = tool,
                                    onClick = { selectedTool = tool },
                                    trailing = {
                                        StatusBadge(
                                            text =
                                                if (tool.present) {
                                                    stringResource(R.string.status_detected)
                                                } else {
                                                    stringResource(R.string.status_absent)
                                                },
                                            color = if (tool.present) NeonGreen else AlertRed,
                                        )
                                        CardActionButton(
                                            onClick = { toolToRemove = tool },
                                            contentDescription = stringResource(R.string.action_remove_tool, tool.name),
                                        )
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        val activity = context as? android.app.Activity
        AddToolDialog(
            isCapturing = isCapturingTag,
            capturedTag = capturedTag,
            isNfcReading = isNfcReading,
            nfcTag = nfcTag,
            onCapture = viewModel::captureTagFromFinder,
            onNfcCapture = { activity?.let { viewModel.startNfcScan(it) } },
            onDismiss = {
                showAddDialog = false
                activity?.let { viewModel.stopNfcScan(it) }
                viewModel.clearCapturedTag()
                viewModel.clearNfcTag()
            },
            onConfirm = { name, epc, category ->
                viewModel.addTool(name, epc, category)
                showAddDialog = false
                activity?.let { viewModel.stopNfcScan(it) }
                viewModel.clearCapturedTag()
                viewModel.clearNfcTag()
            },
        )
    }

    toolToRemove?.let { tool ->
        AlertDialog(
            onDismissRequest = { toolToRemove = null },
            title = { Text(stringResource(R.string.msg_confirm_remove_title, tool.name)) },
            text = { Text(stringResource(R.string.msg_confirm_remove_hint)) },
            confirmButton = {
                TextButton(onClick = {
                    toolToRemove = null
                    viewModel.removeTool(tool)
                }) {
                    Text(stringResource(R.string.action_remove), color = AlertRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { toolToRemove = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun AddToolDialog(
    isCapturing: Boolean,
    capturedTag: String?,
    isNfcReading: Boolean,
    nfcTag: String?,
    onCapture: () -> Unit,
    onNfcCapture: () -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (name: String, epc: String, category: String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var epc by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("manual") }

    val categories =
        listOf(
            "manual" to stringResource(R.string.category_manual),
            "eletrica" to stringResource(R.string.category_eletrica),
            "medicao" to stringResource(R.string.category_medicao),
            "epi" to stringResource(R.string.category_epi),
            "outro" to stringResource(R.string.category_outro),
        )

    LaunchedEffect(capturedTag, nfcTag) {
        nfcTag?.let { epc = it } ?: capturedTag?.let { epc = it }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_title_new_tool)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.dialog_label_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = epc,
                    onValueChange = { epc = it },
                    label = { Text(stringResource(R.string.dialog_label_epc)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Text(
                    text = stringResource(R.string.dialog_label_category),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                androidx.compose.foundation.lazy.LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    items(categories) { (catId, catName) ->
                        val selected = selectedCategory == catId
                        androidx.compose.material3.FilterChip(
                            selected = selected,
                            onClick = { selectedCategory = catId },
                            label = { Text(catName, fontSize = 12.sp) },
                            colors =
                                androidx.compose.material3.FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                ),
                        )
                    }
                }

                // Botão Primário: NFC no Celular (Precisão de Contato Físico)
                OutlinedButton(
                    onClick = onNfcCapture,
                    enabled = !isNfcReading,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .pressScale(),
                    border = BorderStroke(1.dp, NeonGreen),
                ) {
                    Icon(
                        Icons.Filled.Contactless,
                        contentDescription = null,
                        tint = NeonGreen,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        if (isNfcReading) "Aproxime tag na traseira do celular..." else "Ler NFC no Celular (Contato)",
                        color = NeonGreen,
                        fontWeight = FontWeight.Bold,
                    )
                }

                // Botão Secundário: UHF no Finder
                OutlinedButton(
                    onClick = onCapture,
                    enabled = !isCapturing,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .pressScale(),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Icon(
                        Icons.Filled.Build,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        if (isCapturing) "Aguardando aproximação..." else stringResource(R.string.action_capture_tag),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name, epc, selectedCategory) }) {
                Text(stringResource(R.string.action_add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}
