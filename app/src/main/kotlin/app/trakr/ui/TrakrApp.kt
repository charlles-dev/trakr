package app.trakr.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
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
import app.trakr.R
import app.trakr.ui.alerts.AlertListScreen
import app.trakr.ui.dashboard.DashboardScreen
import app.trakr.ui.tools.ToolListScreen

private enum class Section(
    val labelRes: Int,
    val icon: ImageVector,
) {
    Dashboard(R.string.tab_dashboard, Icons.Filled.Home),
    Tools(R.string.tab_tools, Icons.Filled.List),
    Alerts(R.string.tab_alerts, Icons.Filled.Notifications),
}

@Composable
fun TrakrApp() {
    var current by rememberSaveable { mutableStateOf(Section.Dashboard) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                Section.entries.forEach { section ->
                    NavigationBarItem(
                        selected = current == section,
                        onClick = { current = section },
                        icon = { Icon(section.icon, contentDescription = null) },
                        label = { Text(stringResource(section.labelRes)) },
                    )
                }
            }
        },
    ) { padding ->
        when (current) {
            Section.Dashboard -> DashboardScreen(Modifier.padding(padding))
            Section.Tools -> ToolListScreen(Modifier.padding(padding))
            Section.Alerts -> AlertListScreen(Modifier.padding(padding))
        }
    }
}