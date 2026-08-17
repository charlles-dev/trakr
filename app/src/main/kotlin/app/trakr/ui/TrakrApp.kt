@file:Suppress("ktlint:standard:max-line-length", "MaxLineLength")

package app.trakr.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.trakr.R
import app.trakr.ui.alerts.AlertListScreen
import app.trakr.ui.dashboard.DashboardScreen
import app.trakr.ui.motion.TrakrEase
import app.trakr.ui.radar.RadarScreen
import app.trakr.ui.settings.ConfigScreen
import app.trakr.ui.stats.StatsScreen
import app.trakr.ui.theme.AlertRed
import app.trakr.ui.tools.ToolListScreen

private enum class Section(
    val labelRes: Int,
    val icon: ImageVector,
) {
    Dashboard(R.string.tab_dashboard, Icons.Filled.Home),
    Tools(R.string.tab_tools, Icons.Filled.Build),
    Kits(R.string.tab_kits, Icons.Filled.Checklist),
    Alerts(R.string.tab_alerts, Icons.Filled.Notifications),
    Stats(R.string.tab_stats, Icons.Filled.BarChart),

    // Não aparece na barra inferior: aberto pelo botão de engrenagem
    // do Dashboard e fechado pelo botão de voltar.
    Config(R.string.tab_config, Icons.Filled.Settings),
}

@Composable
fun TrakrApp(
    darkTheme: Boolean = true,
    onToggleTheme: () -> Unit = {},
    absenceAlerts: Boolean = true,
    onAbsenceAlertsChange: (Boolean) -> Unit = {},
    initialTargetId: String? = null,
    onTargetConsumed: () -> Unit = {},
) {
    var current by rememberSaveable { mutableStateOf(Section.Dashboard) }
    var booted by rememberSaveable { mutableStateOf(false) }
    var pendingToolId by rememberSaveable { mutableStateOf<String?>(null) }
    var configOpen by rememberSaveable { mutableStateOf(false) }

    val motionAlert by app.trakr.core.motion.MovementAlertManager.movementAlert.collectAsStateWithLifecycle()

    androidx.activity.compose.BackHandler(enabled = configOpen || current != Section.Dashboard) {
        if (configOpen) {
            configOpen = false
        } else {
            current = Section.Dashboard
        }
    }

    // Deep link de notificação: abre a aba Ferramentas no detalhe da ferramenta.
    LaunchedEffect(initialTargetId) {
        val id = initialTargetId ?: return@LaunchedEffect
        pendingToolId = id
        current = Section.Tools
        onTargetConsumed()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    NavigationBar(
                        containerColor = Color.Transparent,
                        tonalElevation = 0.dp,
                    ) {
                        Section.entries.filter { it != Section.Config }.forEach { section ->
                            val selected = current == section && !configOpen
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    configOpen = false
                                    current = section
                                },
                                alwaysShowLabel = true,
                                icon = {
                                    Icon(
                                        section.icon,
                                        contentDescription = null,
                                    )
                                },
                                label = {
                                    Text(
                                        text = stringResource(section.labelRes),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                        maxLines = 1,
                                    )
                                },
                                colors =
                                    NavigationBarItemDefaults.colors(
                                        indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                                        selectedIconColor = MaterialTheme.colorScheme.primary,
                                        selectedTextColor = MaterialTheme.colorScheme.primary,
                                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    ),
                            )
                        }
                    }
                }
            },
        ) { padding ->
            AnimatedContent(
                targetState = if (configOpen) Section.Config else current,
                transitionSpec = {
                    (
                        fadeIn(tween(220, easing = TrakrEase)) +
                            slideInVertically(tween(220, easing = TrakrEase)) { it / 24 }
                    ) togetherWith
                        (fadeOut(tween(120)) + slideOutVertically(tween(120)) { -it / 24 })
                },
                label = "tab",
            ) { section ->
                when (section) {
                    Section.Dashboard ->
                        DashboardScreen(
                            modifier = Modifier.padding(padding),
                            onOpenTools = { current = Section.Tools },
                            onOpenConfig = { configOpen = true },
                            onOpenKits = { current = Section.Kits },
                        )
                    Section.Tools ->
                        ToolListScreen(
                            Modifier.padding(padding),
                            pendingToolId = pendingToolId,
                            onPendingConsumed = { pendingToolId = null },
                        )
                    Section.Kits ->
                        app.trakr.ui.kits.JobKitsScreen(
                            modifier = Modifier.padding(padding),
                            onBack = { current = Section.Dashboard },
                        )
                    Section.Alerts ->
                        AlertListScreen(
                            Modifier.padding(padding),
                            onOpenTool = { id ->
                                pendingToolId = id
                                current = Section.Tools
                            },
                        )
                    Section.Stats ->
                        StatsScreen(
                            modifier = Modifier.padding(padding),
                        )
                    Section.Config ->
                        ConfigScreen(
                            modifier = Modifier.padding(padding),
                            onBack = { configOpen = false },
                            darkTheme = darkTheme,
                            onToggleTheme = onToggleTheme,
                            absenceAlerts = absenceAlerts,
                            onAbsenceAlertsChange = onAbsenceAlertsChange,
                        )
                }
            }
        }

        // Overlay de Alarme Anti-Esquecimento em Movimento (> 15 km/h)
        motionAlert?.let { alertText ->
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { app.trakr.core.motion.MovementAlertManager.clearAlert() },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Warning, contentDescription = null, tint = AlertRed)
                        Spacer(Modifier.padding(horizontal = 4.dp))
                        Text("ALERTA DE DESLOCAMENTO", color = AlertRed, fontWeight = FontWeight.Bold)
                    }
                },
                text = { Text(alertText) },
                confirmButton = {
                    androidx.compose.material3.TextButton(
                        onClick = { app.trakr.core.motion.MovementAlertManager.clearAlert() },
                    ) {
                        Text("Entendido")
                    }
                },
            )
        }

        AnimatedVisibility(
            visible = !booted,
            exit = fadeOut(tween(500)),
        ) {
            BootOverlay(onDone = { booted = true })
        }
    }
}
