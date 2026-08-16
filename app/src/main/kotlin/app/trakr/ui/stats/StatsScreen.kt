package app.trakr.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.trakr.R
import app.trakr.ui.components.maxContentWidth

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    modifier: Modifier = Modifier,
    viewModel: StatsViewModel = viewModel(factory = StatsViewModel.Factory),
) {
    val mostForgotten by viewModel.mostForgotten.collectAsStateWithLifecycle()
    val scansPerDay by viewModel.scansPerDay.collectAsStateWithLifecycle()
    val alertsPerDay by viewModel.alertsPerDay.collectAsStateWithLifecycle()
    val longestAbsent by viewModel.longestAbsent.collectAsStateWithLifecycle()
    val presenceRate by viewModel.presenceRate.collectAsStateWithLifecycle()
    val recentScans by viewModel.recentScans.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Estatísticas") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().maxContentWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Text("Visão geral", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("Taxa de presença", style = MaterialTheme.typography.bodySmall)
                            Text(
                                text = if (presenceRate != null) "${(presenceRate!! * 100).toInt()}%" else "--",
                                style = MaterialTheme.typography.headlineSmall,
                            )
                        }
                        Column {
                            Text("Varreduras (7d)", style = MaterialTheme.typography.bodySmall)
                            Text(text = "${scansPerDay.sumOf { it.cnt }}", style = MaterialTheme.typography.headlineSmall)
                        }
                        Column {
                            Text("Alertas totais", style = MaterialTheme.typography.bodySmall)
                            Text(text = "${alertsPerDay.sumOf { it.cnt }}", style = MaterialTheme.typography.headlineSmall)
                        }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                }

                item {
                    Text("Ferramentas mais esquecidas", style = MaterialTheme.typography.titleSmall)
                }
                if (mostForgotten.isEmpty()) {
                    item { Text("Nenhum alerta registrado", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                } else {
                    items(mostForgotten) { item ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(item.toolName, style = MaterialTheme.typography.bodyMedium)
                            Text("${item.cnt}x", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }

                item { HorizontalDivider(Modifier.padding(vertical = 4.dp)); Text("Ausentes há mais tempo", style = MaterialTheme.typography.titleSmall) }
                if (longestAbsent.isEmpty()) {
                    item { Text("Todas presentes", style = MaterialTheme.typography.bodySmall) }
                } else {
                    items(longestAbsent) { tool ->
                        Text("${tool.name} — ausente desde ${tool.lastSeenAt?.let { java.text.SimpleDateFormat("dd/MM HH:mm").format(java.util.Date(it)) } ?: "--"}", style = MaterialTheme.typography.bodySmall)
                    }
                }

                item { HorizontalDivider(Modifier.padding(vertical = 4.dp)); Text("Varreduras por dia", style = MaterialTheme.typography.titleSmall) }
                items(scansPerDay) { dc ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(dc.day, style = MaterialTheme.typography.bodySmall)
                        Text("${dc.cnt}", style = MaterialTheme.typography.bodySmall)
                    }
                }

                item { HorizontalDivider(Modifier.padding(vertical = 4.dp)); Text("Alertas por dia", style = MaterialTheme.typography.titleSmall) }
                items(alertsPerDay) { dc ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(dc.day, style = MaterialTheme.typography.bodySmall)
                        Text("${dc.cnt}", style = MaterialTheme.typography.bodySmall)
                    }
                }

                item { HorizontalDivider(Modifier.padding(vertical = 4.dp)); Text("Últimas sessões de varredura", style = MaterialTheme.typography.titleSmall) }
                items(recentScans) { s ->
                    Text(
                        "${java.text.SimpleDateFormat("dd/MM HH:mm").format(java.util.Date(s.ts))} — ${s.toolsSeen}/${s.toolsTotal} vistas via ${s.triggeredBy} (${s.connectedTrackers} trackers)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
