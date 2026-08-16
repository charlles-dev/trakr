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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.trakr.R
import app.trakr.core.ble.BleDeviceInfo
import app.trakr.core.ble.BleStatus
import app.trakr.ui.components.maxContentWidth
import app.trakr.ui.theme.TrakrLogo
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
    val bleStatus by viewModel.bleStatus.collectAsStateWithLifecycle()
    val config by viewModel.config.collectAsStateWithLifecycle()
    val sensors by viewModel.sensors.collectAsStateWithLifecycle()
    val addons by viewModel.addons.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val backupJson by viewModel.backupJson.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(backupJson) {
        backupJson?.let { json ->
            val sendIntent = android.content.Intent().apply {
                action = android.content.Intent.ACTION_SEND
                putExtra(android.content.Intent.EXTRA_TEXT, json)
                type = "text/plain"
            }
            val chooser = android.content.Intent.createChooser(sendIntent, "Backup Trakr")
            context.startActivity(chooser)
            viewModel.consumeBackup()
        }
    }

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
                val isConnected = connectedDevice != null

                // Conexão BLE detalhada
                Text(
                    stringResource(R.string.settings_connection),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Text(
                    text = when (bleStatus) {
                        is BleStatus.Connected -> "${stringResource(R.string.settings_connected)}: ${(bleStatus as BleStatus.Connected).deviceName}"
                        BleStatus.Scanning -> "Scanning…"
                        BleStatus.Disabled -> stringResource(R.string.settings_disconnected) + " (BLE off)"
                        else -> if (isConnected) stringResource(R.string.settings_connected) else stringResource(R.string.settings_disconnected)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (connectedDevice != null) {
                    Text(
                        text = connectedDevice.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (config != null && config!!.hasPin) {
                    Text(
                        text = if (config!!.authed) stringResource(R.string.settings_authed) + " — ${config!!.authExpiresMs / 1000}s" else stringResource(R.string.settings_not_authed),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (config!!.authed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }

                // Ações: Varrer agora + Recarregar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                ) {
                    OutlinedButton(
                        onClick = viewModel::rescan,
                        enabled = isConnected,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.action_rescan))
                    }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(
                        onClick = viewModel::loadConfig,
                        enabled = isConnected,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.action_reload))
                    }
                }
                Text(
                    stringResource(R.string.settings_rescan_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )

                // Diagnóstico de add-ons
                if (isConnected) {
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    Text("Add-ons detectados", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    Text(
                        text = if (addons.isEmpty() && sensors == null) "Carregando..." else if (addons.isEmpty()) "Nenhum add-on opcional detectado — base: LED, buzzer, radar, bateria (sim), etc."
                        else "Detectados: ${addons.joinToString(", ")}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    sensors?.let { s ->
                        Column(modifier = Modifier.padding(top = 4.dp)) {
                            Text("TX ${s.txPowerDbm} dBm, offset ${s.rssiOffset} dB, env ${s.env}", style = MaterialTheme.typography.bodySmall)
                            Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                                if (s.hasOled) Text("OLED", style = MaterialTheme.typography.labelSmall)
                                if (s.hasIna219) Text("INA219", style = MaterialTheme.typography.labelSmall)
                                if (s.hasBme280) Text("BME280", style = MaterialTheme.typography.labelSmall)
                                if (s.hasMpu) Text("MPU6050", style = MaterialTheme.typography.labelSmall)
                                if (s.hasVib) Text("VIB", style = MaterialTheme.typography.labelSmall)
                                if (s.hasBtn2) Text("BTN2", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                    OutlinedButton(onClick = { viewModel.loadSensors() }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp), enabled = isConnected) {
                        Text("Recarregar diagnóstico")
                    }
                }

                if (!trackerAvailable && !isConnected) {
                    Text(
                        stringResource(R.string.settings_tracker_unavailable),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }

                // RF e calibração
                SectionHeader("RF e calibração")
                val txOptions = listOf(10, 18, 22, 26, 30)
                DurationDropdown(
                    title = "Potência TX YRM100",
                    hint = "Ajuste dBm por cenário (prédio x canteiro)",
                    valueLabel = "${config?.txPowerDbm ?: sensors?.txPowerDbm ?: 26} dBm",
                    enabled = trackerAvailable,
                    options = txOptions.map { it to "$it dBm" },
                    onSelect = viewModel::setTxPower,
                )
                val offsetOptions = listOf(-10, -5, 0, 5, 10)
                DurationDropdown(
                    title = "Offset RSSI",
                    hint = "Calibração por ambiente (campo, galpão metálico)",
                    valueLabel = "${config?.rssiOffset ?: sensors?.rssiOffset ?: 0} dB (env: ${config?.envProfile ?: sensors?.env ?: "default"})",
                    enabled = trackerAvailable,
                    options = offsetOptions.map { it to "${it} dB" },
                    onSelect = viewModel::setRssiOffset,
                )
                val thOptions = listOf(-80, -70, -60, -50, -40)
                DurationDropdown(
                    title = "Limiar de encontro",
                    hint = "RSSI mínimo para considerar tag próxima",
                    valueLabel = "${config?.rssiThreshold ?: -70} dBm",
                    enabled = trackerAvailable,
                    options = thOptions.map { it to "$it dBm" },
                    onSelect = viewModel::setRssiThreshold,
                )

                // Alertas configuráveis por ferramenta/rastreador
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                SectionHeader("Alertas configuráveis")
                val tools by viewModel.tools.collectAsStateWithLifecycle(initialValue = emptyList())
                val toolSettings by viewModel.allToolSettings.collectAsStateWithLifecycle(initialValue = emptyList())
                val trackerMutes by viewModel.trackerMutes.collectAsStateWithLifecycle(initialValue = emptyList())

                if (tools.isEmpty()) {
                    Text("Nenhuma ferramenta cadastrada", style = MaterialTheme.typography.bodySmall)
                } else {
                    tools.take(10).forEach { tool ->
                        val setting = toolSettings.find { it.toolId == tool.id }
                        SettingSwitchRow(
                            icon = null,
                            title = tool.name,
                            hint = if (setting?.muted == true) "Silenciada" else "Alerta ativo (${tool.epc.takeLast(6)})",
                            checked = setting?.muted != true,
                            onCheckedChange = { enabled -> viewModel.setToolMuted(tool.id, !enabled) },
                        )
                    }
                }
                if (devices.isNotEmpty()) {
                    Text("Rastreadores", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
                    devices.forEach { dev ->
                        val mute = trackerMutes.find { it.address == dev.address }
                        SettingSwitchRow(
                            icon = null,
                            title = dev.name,
                            hint = dev.address,
                            checked = mute?.muted != true,
                            onCheckedChange = { enabled -> viewModel.setTrackerMuted(dev.address, !enabled) },
                        )
                    }
                }

                // Configurações básicas
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

                HorizontalDivider(Modifier.padding(vertical = 12.dp))

                // Seção PIN de acesso
                SectionHeader(stringResource(R.string.settings_pin))
                Text(
                    stringResource(R.string.settings_pin_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
                Text(
                    text = if (config?.hasPin == true) stringResource(R.string.settings_pin_set) else stringResource(R.string.settings_pin_not_set),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (config?.hasPin == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )

                var authPin by remember { mutableStateOf("") }
                var newPin by remember { mutableStateOf("") }
                var confirmPin by remember { mutableStateOf("") }
                var pinError by remember { mutableStateOf<String?>(null) }

                // Auth se já tem PIN e não está autenticado
                if (config?.hasPin == true && config?.authed == false) {
                    OutlinedTextField(
                        value = authPin,
                        onValueChange = { authPin = it; pinError = null },
                        label = { Text(stringResource(R.string.settings_pin_auth)) },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                    )
                    Button(
                        onClick = {
                            if (authPin.length < 4) {
                                pinError = context.getString(R.string.settings_pin_too_short)
                            } else {
                                viewModel.auth(authPin)
                                authPin = ""
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        enabled = isConnected,
                    ) {
                        Text(stringResource(R.string.action_auth))
                    }
                }

                // Novo PIN / troca de PIN
                OutlinedTextField(
                    value = newPin,
                    onValueChange = { newPin = it; pinError = null },
                    label = { Text(stringResource(R.string.settings_pin_new)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                )
                OutlinedTextField(
                    value = confirmPin,
                    onValueChange = { confirmPin = it; pinError = null },
                    label = { Text(stringResource(R.string.settings_pin_confirm)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                )
                pinError?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                ) {
                    Button(
                        onClick = {
                            if (newPin.length < 4 || newPin.length > 32) {
                                pinError = context.getString(R.string.settings_pin_too_short)
                                return@Button
                            }
                            if (newPin != confirmPin) {
                                pinError = context.getString(R.string.settings_pin_mismatch)
                                return@Button
                            }
                            viewModel.setPin(newPin)
                            newPin = ""
                            confirmPin = ""
                        },
                        modifier = Modifier.weight(1f),
                        enabled = isConnected && (config?.let { !it.hasPin || it.authed } ?: true),
                    ) {
                        Text(stringResource(R.string.settings_pin_save))
                    }
                    if (config?.hasPin == true) {
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(
                            onClick = { viewModel.clearPin() },
                            modifier = Modifier.weight(1f),
                            enabled = isConnected && config!!.authed,
                        ) {
                            Text(stringResource(R.string.settings_pin_clear))
                        }
                    }
                }

                HorizontalDivider(Modifier.padding(vertical = 12.dp))

                // Backup e restore local
                SectionHeader(stringResource(R.string.settings_backup))
                Text(
                    stringResource(R.string.settings_backup_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )

                var importJson by remember { mutableStateOf("") }

                LaunchedEffect(isConnected) {
                    if (isConnected) viewModel.loadSensors()
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { viewModel.exportBackup() }, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.action_export_backup))
                    }
                    OutlinedButton(
                        onClick = {
                            if (importJson.isNotBlank()) {
                                viewModel.importBackup(importJson)
                                importJson = ""
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = importJson.isNotBlank(),
                    ) {
                        Text(stringResource(R.string.action_import_backup))
                    }
                }
                OutlinedTextField(
                    value = importJson,
                    onValueChange = { importJson = it },
                    label = { Text("Cole JSON de backup aqui") },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    minLines = 3,
                )

                HorizontalDivider(Modifier.padding(vertical = 12.dp))

                SectionHeader(stringResource(R.string.settings_section_about))
                Icon(
                    imageVector = TrakrLogo,
                    contentDescription = null,
                    modifier =
                        Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(top = 8.dp)
                            .size(56.dp),
                )
                Text(
                    text = stringResource(R.string.settings_about_hint, versionName),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
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
