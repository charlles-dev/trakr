package app.trakr.ui.alerts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.trakr.R
import app.trakr.model.AlertEvent
import app.trakr.ui.components.EmptyState
import app.trakr.ui.components.StatusBadge
import app.trakr.ui.components.maxContentWidth
import app.trakr.ui.theme.AlertRed
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertListScreen(
    modifier: Modifier = Modifier,
    onOpenTool: (String) -> Unit = {},
    viewModel: AlertsViewModel = viewModel(factory = AlertsViewModel.Factory),
) {
    val alerts by viewModel.alerts.collectAsStateWithLifecycle(initialValue = emptyList())
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

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
            TopAppBar(
                title = { Text(stringResource(R.string.tab_alerts)) },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                    ),
                actions = {
                    IconButton(
                        onClick = viewModel::clearAll,
                        enabled = alerts.isNotEmpty(),
                    ) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = stringResource(R.string.action_clear_alerts),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding),
            contentAlignment = Alignment.TopCenter,
        ) {
            if (alerts.isEmpty()) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .maxContentWidth()
                            .fillMaxHeight(),
                    contentAlignment = Alignment.Center,
                ) {
                    EmptyState(
                        icon = Icons.Filled.CheckCircle,
                        title = stringResource(R.string.alerts_empty),
                        hint = stringResource(R.string.alerts_empty_hint),
                    )
                }
            } else {
                AlertsByDay(
                    alerts = alerts,
                    onAlertClick = { alert ->
                        viewModel.markRead(alert)
                        onOpenTool(alert.toolId)
                    },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .maxContentWidth()
                            .fillMaxHeight(),
                )
            }
        }
    }
}

/** Agrupa os alertas por dia (Hoje/Ontem/data completa) e desenha as seções. */
@Composable
private fun AlertsByDay(
    alerts: List<AlertEvent>,
    onAlertClick: (AlertEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val today = LocalDate.now()
    val grouped =
        remember(alerts, today) {
            alerts.groupBy { alert -> dayLabel(alert.createdAt, today) }
        }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        grouped.forEach { (labelRes, groupAlerts) ->
            item(key = "header_$labelRes") {
                SectionLabel(labelRes)
            }
            items(groupAlerts, key = { it.id }) { alert ->
                AlertRow(
                    alert = alert,
                    onClick = { onAlertClick(alert) },
                )
            }
        }
    }
}

private fun dayLabel(
    createdAt: Long,
    today: LocalDate,
): Int =
    when (createdAt.toLocalDate()) {
        today -> R.string.alerts_today
        today.minusDays(1) -> R.string.alerts_yesterday
        else -> R.string.alerts_day
    }

private fun Long.toLocalDate(): LocalDate = java.time.Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate()

@Composable
private fun SectionLabel(label: Int) {
    Text(
        text = stringResource(label),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
    )
}

@Composable
private fun AlertRow(
    alert: AlertEvent,
    onClick: () -> Unit,
) {
    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(AlertRed.copy(alpha = if (alert.read) 0.06f else 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Warning,
                    contentDescription = null,
                    tint = if (alert.read) AlertRed.copy(alpha = 0.5f) else AlertRed,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = alert.toolName,
                    style = MaterialTheme.typography.titleMedium,
                    color =
                        if (alert.read) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                )
                val context = LocalContext.current
                Text(
                    text = relativeTime(alert.createdAt, context),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!alert.read) {
                StatusBadge(text = stringResource(R.string.status_new), color = AlertRed)
            }
        }
    }
}

/** Tempo relativo curto: agora / N min / N h / N d. */
private fun relativeTime(
    createdAt: Long,
    context: android.content.Context,
): String {
    val now = System.currentTimeMillis()
    val diff = now - createdAt
    val minutes = diff / 60_000L
    return when {
        minutes < 1 -> context.getString(R.string.time_now)
        minutes < 60 -> context.getString(R.string.time_minutes, minutes)
        minutes < 1440 -> context.getString(R.string.time_hours, minutes / 60)
        else -> context.getString(R.string.time_days, minutes / 1440)
    }
}
