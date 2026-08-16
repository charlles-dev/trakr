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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import app.trakr.ui.theme.TrakrAlert
import app.trakr.ui.theme.TrakrOps
import app.trakr.ui.theme.TrakrRadar
import app.trakr.ui.theme.TrakrToolbox
import app.trakr.ui.tools.ToolListScreen

private enum class Section(
    val labelRes: Int,
    val icon: ImageVector,
) {
    Dashboard(R.string.tab_dashboard, TrakrOps),
    Tools(R.string.tab_tools, TrakrToolbox),
    Alerts(R.string.tab_alerts, TrakrAlert),
    Radar(R.string.tab_radar, TrakrRadar),
}

@Composable
fun TrakrApp(
    darkTheme: Boolean = true,
    onToggleTheme: () -> Unit = {},
) {
    var current by rememberSaveable { mutableStateOf(Section.Dashboard) }
    var booted by rememberSaveable { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    tonalElevation = 0.dp,
                ) {
                    Section.entries.forEach { section ->
                        val selected = current == section
                        NavigationBarItem(
                            selected = selected,
                            onClick = { current = section },
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
                targetState = current,
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
                            darkTheme = darkTheme,
                            onToggleTheme = onToggleTheme,
                        )
                    Section.Tools -> ToolListScreen(Modifier.padding(padding))
                    Section.Alerts -> AlertListScreen(Modifier.padding(padding))
                    Section.Radar -> RadarScreen(Modifier.padding(padding))
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
