@file:Suppress("ktlint:standard:max-line-length", "MaxLineLength")

package app.trakr.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.trakr.R
import app.trakr.core.ble.BleStatus
import app.trakr.ui.components.ConnectionBanner
import app.trakr.ui.components.EmptyState
import app.trakr.ui.components.SectionHeader
import app.trakr.ui.components.StatTile
import app.trakr.ui.components.StatusBadge
import app.trakr.ui.components.ToolCard
import app.trakr.ui.components.TrakrWordmark
import app.trakr.ui.components.maxContentWidth
import app.trakr.ui.theme.AlertRed
import app.trakr.ui.theme.NeonGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    modifier: Modifier = Modifier,
    onOpenTools: () -> Unit = {},
    onOpenConfig: () -> Unit = {},
    onOpenKits: () -> Unit = {},
    viewModel: DashboardViewModel = viewModel(factory = DashboardViewModel.Factory),
) {
    val tools by viewModel.tools.collectAsStateWithLifecycle(initialValue = emptyList())
    val devices by viewModel.devices.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    // Haptic curto quando o rastreador conecta (ou reconecta).
    val haptic = LocalHapticFeedback.current
    var wasConnected by remember { mutableStateOf(false) }
    LaunchedEffect(devices.isNotEmpty()) {
        val connectedNow = devices.isNotEmpty()
        if (connectedNow && !wasConnected) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
        wasConnected = connectedNow
    }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it.resolve(context))
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                colors =
                    TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                    ),
                title = { TrakrWordmark() },
                actions = {
                    IconButton(onClick = onOpenKits) {
                        Icon(
                            imageVector = Icons.Filled.Checklist,
                            contentDescription = "Kits & Missões",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = onOpenConfig) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = stringResource(R.string.action_open_config),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        val presentCount = tools.count { it.present }
        val missingCount = tools.size - presentCount
        val connected = devices.isNotEmpty()
        val scanning = status is BleStatus.Scanning

        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.TopCenter,
            ) {
                if (tools.isEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxWidth().maxContentWidth().fillMaxHeight().padding(horizontal = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        ConnectionBanner(
                            deviceName = devices.joinToString { it.name }.ifEmpty { null },
                            connected = connected,
                            scanning = scanning,
                            modifier = Modifier.padding(top = 24.dp),
                        )
                        EmptyState(
                            icon = Icons.Filled.Info,
                            title = stringResource(R.string.dashboard_empty),
                            hint = stringResource(R.string.dashboard_empty_hint),
                        )
                        TextButton(onClick = onOpenTools) {
                            Text(stringResource(R.string.dashboard_empty_cta))
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().maxContentWidth().fillMaxHeight(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        item {
                            ConnectionBanner(
                                deviceName = devices.joinToString { it.name }.ifEmpty { null },
                                connected = connected,
                                scanning = scanning,
                            )
                        }
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                StatTile(
                                    label = stringResource(R.string.stat_total),
                                    value = tools.size.toString(),
                                    modifier = Modifier.weight(1f),
                                )
                                StatTile(
                                    label = stringResource(R.string.stat_detected),
                                    value = presentCount.toString(),
                                    valueColor = NeonGreen,
                                    modifier = Modifier.weight(1f),
                                )
                                StatTile(
                                    label = stringResource(R.string.stat_absent),
                                    value = missingCount.toString(),
                                    valueColor = if (missingCount > 0) AlertRed else NeonGreen,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                        item {
                            SectionHeader(stringResource(R.string.tools_section))
                        }
                        items(tools, key = { it.id }) { tool ->
                            ToolCard(
                                tool = tool,
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
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
