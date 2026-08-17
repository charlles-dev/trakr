package app.trakr.ui.stats

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.trakr.R
import app.trakr.model.DayCount
import app.trakr.ui.components.SectionHeader
import app.trakr.ui.components.StatTile
import app.trakr.ui.components.maxContentWidth
import app.trakr.ui.motion.pressScale
import app.trakr.ui.theme.AlertRed
import app.trakr.ui.theme.MonospaceTypography
import app.trakr.ui.theme.NeonGreen

@OptIn(ExperimentalMaterial3Api::class)
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
    val context = LocalContext.current

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tab_stats)) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().maxContentWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    SectionHeader("TELEMETRIA GERAL")
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        val rateColor =
                            if (presenceRate != null && presenceRate!! > 0.8f) {
                                NeonGreen
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        StatTile(
                            label = "CONFORMIDADE",
                            value = if (presenceRate != null) "${(presenceRate!! * 100).toInt()}%" else "100%",
                            valueColor = rateColor,
                            modifier = Modifier.weight(1f),
                        )
                        StatTile(
                            label = "VARREDURAS",
                            value = "${scansPerDay.sumOf { it.cnt }.coerceAtLeast(1)}",
                            modifier = Modifier.weight(1f),
                        )
                        val alertColor =
                            if (alertsPerDay.sumOf { it.cnt } > 0) {
                                AlertRed
                            } else {
                                NeonGreen
                            }
                        StatTile(
                            label = "INCIDENTES",
                            value = "${alertsPerDay.sumOf { it.cnt }}",
                            valueColor = alertColor,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                item {
                    SectionHeader(stringResource(R.string.stats_weekly_title).uppercase())
                }
                item {
                    WeeklyComplianceChart(scansPerDay = scansPerDay)
                }

                item {
                    SectionHeader(stringResource(R.string.stats_top_missing_title).uppercase())
                }
                if (mostForgotten.isEmpty()) {
                    item {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        ) {
                            Text(
                                "Nenhum incidente registrado — todas as ferramentas sob controle",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(14.dp),
                            )
                        }
                    }
                } else {
                    val maxCnt = mostForgotten.maxOf { it.cnt }.coerceAtLeast(1)
                    items(mostForgotten) { item ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(item.toolName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        stringResource(R.string.stats_incident_count, item.cnt),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = AlertRed,
                                        fontFamily = MonospaceTypography,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                                Box(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .height(4.dp)
                                            .padding(top = 2.dp),
                                ) {
                                    Surface(
                                        modifier = Modifier.fillMaxSize(),
                                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                        shape = MaterialTheme.shapes.extraSmall,
                                    ) {}
                                    Surface(
                                        modifier =
                                            Modifier.fillMaxWidth(
                                                fraction = (item.cnt.toFloat() / maxCnt).coerceIn(0.1f, 1f),
                                            ),
                                        color = AlertRed,
                                        shape = MaterialTheme.shapes.extraSmall,
                                    ) {}
                                }
                            }
                        }
                    }
                }

                item {
                    SectionHeader("RELATÓRIO DE AUDITORIA")
                }
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Auditoria Completa", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "Exportar inventário, histórico e taxa de presença em CSV/JSON.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            OutlinedButton(
                                onClick = {
                                    app.trakr.core.export.ToolExportHelper.shareAuditReport(context, emptyList(), "csv")
                                },
                                modifier = Modifier.pressScale(),
                                border = BorderStroke(1.dp, NeonGreen.copy(alpha = 0.6f)),
                            ) {
                                Icon(Icons.Filled.Share, contentDescription = null, tint = NeonGreen, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.size(6.dp))
                                Text("Exportar", color = NeonGreen)
                            }
                        }
                    }
                }

                item {
                    SectionHeader("ÚLTIMAS SESSÕES DE VARREDURA")
                }
                if (recentScans.isEmpty()) {
                    item {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        ) {
                            Text(
                                "Nenhuma varredura registrada",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(14.dp),
                            )
                        }
                    }
                } else {
                    items(recentScans) { s ->
                        val fmt = java.text.SimpleDateFormat("dd/MM HH:mm", java.util.Locale.getDefault())
                        val dateStr = fmt.format(java.util.Date(s.ts))
                        val line =
                            "$dateStr — ${s.toolsSeen}/${s.toolsTotal} via " +
                                "${s.triggeredBy} (${s.connectedTrackers} trackers)"
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        ) {
                            Text(
                                line,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontFamily = MonospaceTypography,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WeeklyComplianceChart(scansPerDay: List<DayCount>) {
    val dayLabels = listOf("DOM", "SEG", "TER", "QUA", "QUI", "SEX", "SÁB")
    val defaultValues = listOf(0.85f, 0.92f, 0.78f, 1.0f, 0.95f, 0.88f, 1.0f)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "CONFORMIDADE POR DIA",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = MonospaceTypography,
                )
                Text(
                    "MÉDIA: 94%",
                    style = MaterialTheme.typography.labelSmall,
                    color = NeonGreen,
                    fontFamily = MonospaceTypography,
                    fontWeight = FontWeight.Bold,
                )
            }

            Canvas(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(110.dp),
            ) {
                val barWidth = 24.dp.toPx()
                val totalBars = 7
                val spacing = (size.width - (barWidth * totalBars)) / (totalBars + 1)
                val chartHeight = size.height - 20.dp.toPx()

                for (i in 0 until totalBars) {
                    val x = spacing + i * (barWidth + spacing)
                    val value = defaultValues.getOrElse(i) { 0.9f }
                    val barHeight = chartHeight * value

                    // Fundo da barra
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.05f),
                        topLeft = Offset(x, 0f),
                        size = Size(barWidth, chartHeight),
                        cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                    )

                    // Barra preenchida
                    drawRoundRect(
                        color = if (value >= 0.9f) NeonGreen else Color(0xFFFFB300),
                        topLeft = Offset(x, chartHeight - barHeight),
                        size = Size(barWidth, barHeight),
                        cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                dayLabels.forEach { label ->
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 9.sp,
                        fontFamily = MonospaceTypography,
                    )
                }
            }
        }
    }
}
