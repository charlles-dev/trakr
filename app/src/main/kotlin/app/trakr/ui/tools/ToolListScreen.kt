package app.trakr.ui.tools

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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
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
    onLocate: (String) -> Unit = {},
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
            onLocate = { onLocate(tool.id) },
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

    val filtered =
        remember(tools, query) {
            if (query.isBlank()) {
                tools
            } else {
                tools.filter {
                    it.name.contains(query.trim(), ignoreCase = true) ||
                        it.epc.contains(query.trim(), ignoreCase = true)
                }
            }
        }

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
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = viewModel::refresh,
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding),
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.TopCenter,
            ) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .maxContentWidth()
                            .fillMaxHeight(),
                ) {
                    if (tools.isEmpty()) {
                        EmptyState(
                            icon = Icons.Filled.Build,
                            title = stringResource(R.string.tools_empty),
                            hint = stringResource(R.string.tools_empty_hint),
                            modifier = Modifier.align(Alignment.Center),
                        )
                    } else {
                        Column(Modifier.fillMaxSize()) {
                            OutlinedTextField(
                                value = query,
                                onValueChange = { query = it },
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 4.dp),
                                placeholder = { Text(stringResource(R.string.search_placeholder)) },
                                leadingIcon = {
                                    Icon(Icons.Filled.Search, contentDescription = null)
                                },
                                singleLine = true,
                            )
                            if (filtered.isEmpty()) {
                                Box(
                                    modifier =
                                        Modifier
                                            .fillMaxSize()
                                            .padding(top = 48.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    EmptyState(
                                        icon = Icons.Filled.Search,
                                        title = stringResource(R.string.search_no_results),
                                        hint = stringResource(R.string.search_no_results_hint, query.trim()),
                                    )
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(16.dp),
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
        }
    }

    if (showAddDialog) {
        AddToolDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { name, epc ->
                viewModel.addTool(name, epc)
                showAddDialog = false
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
    onDismiss: () -> Unit,
    onConfirm: (name: String, epc: String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var epc by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_title_new_tool)) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.dialog_label_name)) },
                    singleLine = true,
                )
                Spacer(Modifier.size(8.dp))
                OutlinedTextField(
                    value = epc,
                    onValueChange = { epc = it },
                    label = { Text(stringResource(R.string.dialog_label_epc)) },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name, epc) }) {
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
