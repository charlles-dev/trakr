package app.trakr.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import app.trakr.core.ble.BleDeviceInfo
import app.trakr.ui.components.maxContentWidth
import app.trakr.ui.theme.TrakrMoon
import app.trakr.ui.theme.TrakrSun

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    darkTheme: Boolean = true,
    onToggleTheme: () -> Unit = {},
    absenceAlerts: Boolean = true,
    onAbsenceAlertsChange: (Boolean) -> Unit = {},
    viewModel: ConfigViewModel = viewModel(factory = ConfigViewModel.Factory),
) {
    val devices by viewModel.devices.collectAsStateWithLifecycle()
    val config by viewModel.config.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it.resolve(context))
            viewModel.consumeMessage()
        }
    }

    val versionName =
        remember(context) {
            try {
                context.packageManager
                    .getPackageInfo(context.packageName, 0)
                    .versionName
                    ?: ""
            } catch (e: Exception) {
                ""
            }
        }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tab_config)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                    ),
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
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .maxContentWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                SectionHeader(stringResource(R.string.settings_section_app))

                SettingSwitchRow(
                    icon = if (darkTheme) TrakrMoon else TrakrSun,
                    title = stringResource(R.string.settings_theme),
                    hint =
                        if (darkTheme) {
                            stringResource(R.string.settings_theme_dark)
                        } else {
                            stringResource(R.string.settings_theme_light)
                        },
                    checked = darkTheme,
                    onCheckedChange = { onToggleTheme() },
                )

                SettingSwitchRow(
                    icon = null,
                    title = stringResource(R.string.settings_absence_alerts),
                    hint = stringResource(R.string.settings_absence_alerts_hint),
                    checked = absenceAlerts,
                    onCheckedChange = onAbsenceAlertsChange,
                )

                HorizontalDivider(Modifier.padding(vertical = 8.dp))

                SectionHeader(stringResource(R.string.settings_section_tracker))

                val connectedDevice: BleDeviceInfo? = devices.firstOrNull()
                val trackerAvailable = config != null && connectedDevice != null

                Text(
                    stringResource(R.string.settings_tracker_connection),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Text(
                    text =
                        connectedDevice?.name
                            ?: stringResource(R.string.settings_tracker_disconnected),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                SettingSwitchRow(
                    icon = null,
                    title = stringResource(R.string.settings_beep),
                    hint = stringResource(R.string.settings_beep_hint),
                    checked = config?.beep ?: false,
                    enabled = trackerAvailable,
                    onCheckedChange = viewModel::setBeep,
                )

                val listenOptions = listOf(15_000, 30_000, 60_000)
                val listenLabel =
                    stringResource(
                        R.string.settings_seconds,
                        (config?.listenMs ?: 30_000) / 1000,
                    )
                DurationDropdown(
                    title = stringResource(R.string.settings_listen_ms),
                    hint = stringResource(R.string.settings_listen_ms_hint),
                    valueLabel = listenLabel,
                    enabled = trackerAvailable,
                    options =
                        listenOptions.map { ms ->
                            ms to stringResource(R.string.settings_seconds, ms / 1000)
                        },
                    onSelect = viewModel::setListenMs,
                )

                val radarOptions = listOf(60_000, 120_000, 180_000)
                val radarLabel =
                    stringResource(
                        R.string.settings_minutes,
                        (config?.radarMs ?: 120_000) / 60_000,
                    )
                DurationDropdown(
                    title = stringResource(R.string.settings_radar_ms),
                    hint = stringResource(R.string.settings_radar_ms_hint),
                    valueLabel = radarLabel,
                    enabled = trackerAvailable,
                    options =
                        radarOptions.map { ms ->
                            ms to stringResource(R.string.settings_minutes, ms / 60_000)
                        },
                    onSelect = viewModel::setRadarMs,
                )

                if (!trackerAvailable) {
                    Text(
                        stringResource(R.string.settings_tracker_unavailable),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                } else {
                    OutlinedButton(
                        onClick = viewModel::loadConfig,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp),
                    ) {
                        Text(stringResource(R.string.action_reload))
                    }
                }

                HorizontalDivider(Modifier.padding(vertical = 8.dp))

                SectionHeader(stringResource(R.string.settings_section_about))
                Text(
                    text = stringResource(R.string.settings_about_hint, versionName),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
    )
}

@Composable
private fun SettingSwitchRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    title: String,
    hint: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color =
                    if (enabled) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
            )
            Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DurationDropdown(
    title: String,
    hint: String,
    valueLabel: String,
    enabled: Boolean,
    options: List<Pair<Int, String>>,
    onSelect: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Column(Modifier.padding(top = 6.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = hint,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
            modifier = Modifier.padding(top = 4.dp),
        ) {
            OutlinedTextField(
                value = valueLabel,
                onValueChange = {},
                readOnly = true,
                enabled = enabled,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier =
                    Modifier
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth(),
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                options.forEach { (value, label) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            expanded = false
                            onSelect(value)
                        },
                    )
                }
            }
        }
    }
}
