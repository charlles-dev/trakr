package app.trakr.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.trakr.R
import app.trakr.ui.alerts.AlertListScreen
import app.trakr.ui.dashboard.DashboardScreen
import app.trakr.ui.motion.TrakrEase
import app.trakr.ui.radar.RadarScreen
import app.trakr.ui.settings.ConfigScreen
import app.trakr.ui.stats.StatsScreen
import app.trakr.ui.tools.ToolListScreen
import androidx.compose.material.icons.filled.BarChart

private enum class Section(
    val labelRes: Int,
    val icon: ImageVector,
) {
    Dashboard(R.string.tab_dashboard, Icons.Filled.Home),
    Tools(R.string.tab_tools, Icons.Filled.Build),
    Alerts(R.string.tab_alerts, Icons.Filled.Notifications),
    Radar(R.string.tab_radar, Icons.Filled.Radar),
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
    var radarTargetId by rememberSaveable { mutableStateOf<String?>(null) }
    var configOpen by rememberSaveable { mutableStateOf(false) }

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
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    tonalElevation = 0.dp,
                ) {
                    Section.entries.filter { it != Section.Config }.forEach { section ->
                        val selected = current == section
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                configOpen = false
                                current = section
                            },
                            icon = {
                                Icon(
                                    section.icon,
                                    contentDescription = null,
                                    tint =
                                        if (selected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                )
                            },
                            label = {
                                Text(
                                    stringResource(section.labelRes),
                                    color =
                                        if (selected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                )
                            },
                            colors =
                                NavigationBarItemDefaults.colors(
                                    indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                        )
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
                        )
                    Section.Tools ->
                        ToolListScreen(
                            Modifier.padding(padding),
                            pendingToolId = pendingToolId,
                            onPendingConsumed = { pendingToolId = null },
                            onLocate = { id ->
                                radarTargetId = id
                                pendingToolId = null
                                current = Section.Radar
                            },
                        )
                    Section.Alerts ->
                        AlertListScreen(
                            Modifier.padding(padding),
                            onOpenTool = { id ->
                                pendingToolId = id
                                current = Section.Tools
                            },
                        )
                    Section.Radar ->
                        RadarScreen(
                            Modifier.padding(padding),
                            pendingTargetId = radarTargetId,
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

        AnimatedVisibility(
            visible = !booted,
            exit = fadeOut(tween(500)),
        ) {
            BootOverlay(onDone = { booted = true })
        }
    }
}
