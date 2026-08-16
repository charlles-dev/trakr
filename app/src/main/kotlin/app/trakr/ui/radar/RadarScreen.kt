package app.trakr.ui.radar

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.trakr.R
import app.trakr.core.ble.OtaManager
import app.trakr.core.ble.OtaResult
import app.trakr.model.RadarReport
import app.trakr.model.Tool
import app.trakr.ui.components.EmptyState
import app.trakr.ui.components.PulseDot
import app.trakr.ui.components.maxContentWidth
import app.trakr.ui.motion.pressScale
import app.trakr.ui.theme.AlertRed
import app.trakr.ui.theme.MonospaceTypography
import app.trakr.ui.theme.TrakrTheme
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RadarScreen(
    modifier: Modifier = Modifier,
    pendingTargetId: String? = null,
    viewModel: RadarViewModel = viewModel(factory = RadarViewModel.Factory),
) {
    val devices by viewModel.devices.collectAsStateWithLifecycle()
    val report by viewModel.radarReport.collectAsStateWithLifecycle()
    val liveReport by viewModel.liveReport.collectAsStateWithLifecycle()
    val multiReport by viewModel.multiReport.collectAsStateWithLifecycle()
    val tools by viewModel.tools.collectAsStateWithLifecycle(initialValue = emptyList())
    val targetId by viewModel.targetId.collectAsStateWithLifecycle()
    val running by viewModel.running.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()

    // Deep link de "Localizar": pré-seleciona o alvo sem substituir a escolha do usuário.
    LaunchedEffect(pendingTargetId, tools, targetId) {
        val pending = pendingTargetId ?: return@LaunchedEffect
        if (tools.any { it.id == pending } && targetId != pending) {
            viewModel.selectTarget(pending)
        }
    }

    val radarDevices = devices

    // Atualização de firmware (OTA): picker + confirmação + progresso local.
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingOtaUri by remember { mutableStateOf<Uri?>(null) }
    var otaProgress by remember { mutableStateOf<Int?>(null) }
    var otaError by remember { mutableStateOf<String?>(null) }
    var otaDone by remember { mutableStateOf(false) }
    val otaLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocument(),
        ) { uri ->
            if (uri != null) pendingOtaUri = uri
        }

    fun startOta(uri: Uri) {
        otaError = null
        otaDone = false
        otaProgress = 0
        scope.launch {
            val result = OtaManager.update(context, uri) { progress -> otaProgress = progress }
            otaProgress = null
            when (result) {
                is OtaResult.Ok -> otaDone = true
                is OtaResult.Error -> otaError = context.getString(R.string.ota_error, context.getString(result.messageRes))
            }
        }
    }

    // Vibração curta quando a tag é encontrada (transição sem sinal → sinal).
    val haptic = LocalHapticFeedback.current
    var wasPresent by remember { mutableStateOf(false) }
    LaunchedEffect(report?.present) {
        val found = report?.present == true
        if (found && !wasPresent) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
        wasPresent = found
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tab_radar)) },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                    ),
                actions = {
                    IconButton(
                        onClick = { otaLauncher.launch(arrayOf("application/octet-stream", "application/zip", "*/*")) },
                        enabled = radarDevices.isNotEmpty() && otaProgress == null,
                    ) {
                        Icon(
                            Icons.Filled.Refresh,
                            contentDescription = stringResource(R.string.ota_action),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        },
    ) { padding ->
        if (radarDevices.isEmpty()) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                EmptyState(
                    icon = Icons.Filled.Radar,
                    title = stringResource(R.string.radar_no_device),
                    hint = stringResource(R.string.radar_no_device_hint),
                )
            }
            return@Scaffold
        }

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
                        .fillMaxSize()
                        .maxContentWidth()
                        .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PulseDot(
                        color = if (running) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = radarDevices.first().name.uppercase(),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = MonospaceTypography,
                    )
                }

                RadarDisplayCard(report = report, running = running, liveReport = liveReport, multiReport = multiReport)

                TargetPicker(
                    tools = tools,
                    targetId = targetId,
                    onSelect = viewModel::selectTarget,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Button(
                        onClick = viewModel::start,
                        enabled = !running,
                        modifier =
                            Modifier
                                .weight(1f)
                                .pressScale(),
                    ) {
                        Text(stringResource(R.string.radar_start))
                    }
                    OutlinedButton(
                        onClick = viewModel::stop,
                        enabled = running,
                        colors =
                            ButtonDefaults.outlinedButtonColors(
                                contentColor = AlertRed,
                            ),
                        modifier = Modifier.pressScale(),
                    ) {
                        Text(stringResource(R.string.radar_stop))
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = viewModel::startLive,
                        enabled = !running,
                        modifier = Modifier.weight(1f).pressScale(),
                    ) { Text("Ao vivo") }
                    OutlinedButton(
                        onClick = viewModel::startMulti,
                        enabled = !running,
                        modifier = Modifier.weight(1f).pressScale(),
                    ) { Text("Multi-alvo") }
                }

                message?.let {
                    Text(
                        text = it.resolve(context),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                otaProgress?.let { progress ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.ota_progress, progress),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontFamily = MonospaceTypography,
                            )
                            LinearProgressIndicator(
                                progress = { progress / 100f },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }

                if (otaDone) {
                    Text(
                        text = stringResource(R.string.ota_done),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                otaError?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }

    pendingOtaUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingOtaUri = null },
            title = { Text(stringResource(R.string.ota_warning_title)) },
            text = { Text(stringResource(R.string.ota_warning_text)) },
            confirmButton = {
                TextButton(onClick = {
                    pendingOtaUri = null
                    startOta(uri)
                }) {
                    Text(stringResource(R.string.ota_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingOtaUri = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

/** Painel principal: varredura circular animada + leitura de RSSI + direção por passo + ao vivo + multi. */
@Composable
internal fun RadarDisplayCard(
    report: RadarReport?,
    running: Boolean,
    liveReport: app.trakr.model.LiveReport? = null,
    multiReport: app.trakr.model.MultiRadarReport? = null,
) {
    val present = report?.present == true
    val rssi = report?.rssi ?: -100
    val delta = report?.delta ?: 0
    val hint = report?.hint ?: "search"
    val normalized = if (present) ((rssi + 80).toFloat() / 50f).coerceIn(0f, 1f) else 0f

    val statusText =
        when {
            !running -> stringResource(R.string.radar_status_idle)
            report == null && liveReport == null && multiReport == null -> stringResource(R.string.radar_status_waiting)
            liveReport != null -> "Ao vivo: ${liveReport.reads.size} tags"
            multiReport != null -> "Multi: ${multiReport.ranking.size} alvos"
            !present -> stringResource(R.string.radar_status_no_signal)
            rssi > -45 -> stringResource(R.string.radar_status_found)
            else -> stringResource(R.string.radar_status_near)
        }

    val directionText =
        when (hint) {
            "continue" -> "+$delta dBm → continue"
            "turn_around" -> "$delta dBm → volte"
            "hold" -> "Sinal estável"
            else -> "Procurando…"
        }

    // Scan line: linha fina que varre o painel de cima a baixo durante a busca.
    val scanY by rememberInfiniteTransition(label = "scan").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3200, easing = LinearEasing), RepeatMode.Restart),
        label = "scanProgress",
    )
    val theme = MaterialTheme.colorScheme

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Box {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadarSweep(
                    running = running,
                    present = present,
                    normalized = normalized,
                    modifier = Modifier.size(190.dp),
                )
                Spacer(Modifier.width(16.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.radar_signal_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = if (present) "$rssi" else "—",
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Bold,
                        fontFamily = MonospaceTypography,
                        color = if (present) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = stringResource(R.string.radar_dbm_unit),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    LinearProgressIndicator(
                        progress = { normalized },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (running && present) {
                        Text(
                            text = directionText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontFamily = MonospaceTypography,
                        )
                    }
                    liveReport?.let { live ->
                        Text(
                            text = live.reads.take(3).joinToString("\n") { "${it.tag.takeLast(6)} ${it.rssi} dBm" },
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = MonospaceTypography,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    multiReport?.let { multi ->
                        Text(
                            text = multi.ranking.take(3).joinToString("\n") { "${it.tag.takeLast(6)} ${it.rssi} dBm" },
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = MonospaceTypography,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            if (running) {
                Canvas(Modifier.matchParentSize()) {
                    val y = size.height * scanY
                    drawLine(
                        color = theme.primary.copy(alpha = 0.16f),
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 2.dp.toPx(),
                    )
                }
            }
        }
    }
}

/** Varredura circular: anéis concêntricos, feixe girando e blip da tag. */
@Composable
private fun RadarSweep(
    running: Boolean,
    present: Boolean,
    normalized: Float,
    modifier: Modifier = Modifier,
) {
    val theme = MaterialTheme.colorScheme
    val blink by rememberInfiniteTransition(label = "blink").animateFloat(
        initialValue = 0.5f,
        targetValue = 1.6f,
        animationSpec = infiniteRepeatable(tween(650), RepeatMode.Reverse),
        label = "blinkScale",
    )

    val rotation by rememberInfiniteTransition(label = "sweep").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec =
            infiniteRepeatable(
                // Com sinal, o feixe acelera (sensação de "travamento" no alvo).
                tween(if (present) 900 else 2400, easing = LinearEasing),
                RepeatMode.Restart,
            ),
        label = "sweepRotation",
    )

    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        // Recuado para dar espaço à moldura HUD nos cantos.
        val outer = size.minDimension / 2f - 20.dp.toPx()

        // Moldura HUD: colchetes nos 4 cantos.
        val bracket = 12.dp.toPx()
        val inset = 8.dp.toPx()
        val frame = theme.primary.copy(alpha = 0.65f)
        val stroke = 2.5f
        drawLine(frame, Offset(inset, inset + bracket), Offset(inset, inset), stroke, cap = StrokeCap.Round)
        drawLine(frame, Offset(inset, inset), Offset(inset + bracket, inset), stroke, cap = StrokeCap.Round)
        drawLine(frame, Offset(size.width - inset, inset + bracket), Offset(size.width - inset, inset), stroke, cap = StrokeCap.Round)
        drawLine(frame, Offset(size.width - inset, inset), Offset(size.width - inset - bracket, inset), stroke, cap = StrokeCap.Round)
        drawLine(frame, Offset(inset, size.height - inset - bracket), Offset(inset, size.height - inset), stroke, cap = StrokeCap.Round)
        drawLine(frame, Offset(inset, size.height - inset), Offset(inset + bracket, size.height - inset), stroke, cap = StrokeCap.Round)
        drawLine(
            frame,
            Offset(size.width - inset, size.height - inset - bracket),
            Offset(size.width - inset, size.height - inset),
            stroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            frame,
            Offset(size.width - inset, size.height - inset),
            Offset(size.width - inset - bracket, size.height - inset),
            stroke,
            cap = StrokeCap.Round,
        )

        // Anéis concêntricos.
        listOf(0.33f, 0.66f, 1f).forEach { f ->
            drawCircle(
                color = theme.outline.copy(alpha = 0.35f),
                radius = outer * f,
                center = center,
                style = Stroke(width = 1.5f),
            )
        }
        drawCircle(
            color = theme.outline.copy(alpha = 0.6f),
            radius = 3.5.dp.toPx(),
            center = center,
        )

        if (running) {
            rotate(rotation, center) {
                // Feixe de varredura com cauda.
                drawArc(
                    color = theme.primary.copy(alpha = 0.30f),
                    startAngle = -55f,
                    sweepAngle = 55f,
                    useCenter = true,
                    topLeft = Offset(center.x - outer, center.y - outer),
                    size = androidx.compose.ui.geometry.Size(outer * 2f, outer * 2f),
                )
                // Linha de borda do feixe.
                drawLine(
                    color = theme.primary.copy(alpha = 0.9f),
                    start = center,
                    end = Offset(center.x + outer, center.y),
                    strokeWidth = 2.5f,
                    cap = StrokeCap.Round,
                )
                // Blip da tag: no feixe, raio proporcional ao sinal (perto = centro).
                if (present) {
                    val blipRadius = outer * (0.30f + 0.60f * (1f - normalized))
                    val blip = Offset(center.x + blipRadius, center.y)
                    // Glow radial pulsante atrás do blip.
                    val glowRadius = 16.dp.toPx() * blink
                    drawCircle(
                        brush =
                            Brush.radialGradient(
                                colors =
                                    listOf(
                                        theme.primary.copy(alpha = 0.50f),
                                        Color.Transparent,
                                    ),
                                center = blip,
                                radius = glowRadius,
                            ),
                        radius = glowRadius,
                        center = blip,
                    )
                    drawCircle(
                        color = theme.primary,
                        radius = 5.dp.toPx(),
                        center = blip,
                    )
                }
            }
        }
    }
}

@Composable
internal fun TargetPicker(
    tools: List<Tool>,
    targetId: String?,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val targetName = tools.firstOrNull { it.id == targetId }?.name

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.radar_target_label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box {
            OutlinedButton(
                onClick = { expanded = true },
                enabled = tools.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text =
                        targetName
                            ?: if (tools.isEmpty()) {
                                stringResource(R.string.radar_no_tools)
                            } else {
                                stringResource(R.string.radar_choose_tool)
                            },
                )
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                tools.forEach { tool ->
                    DropdownMenuItem(
                        text = { Text(tool.name) },
                        onClick = {
                            onSelect(tool.id)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

// ---------------- Previews ----------------

@Preview(showBackground = true, backgroundColor = 0xFF0B1210)
@Composable
internal fun RadarDisplayCardPreview() {
    TrakrTheme(darkTheme = true) {
        RadarDisplayCard(
            report = RadarReport(tag = "E28011606000020400000001", rssi = -52, present = true),
            running = true,
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0B1210)
@Composable
internal fun TargetPickerPreview() {
    TrakrTheme(darkTheme = true) {
        TargetPicker(
            tools =
                listOf(
                    Tool(id = "1", name = "Parafusadeira", epc = "E28011606000020400000001"),
                    Tool(id = "2", name = "Furadeira", epc = "E28011606000020400000002"),
                ),
            targetId = null,
            onSelect = {},
        )
    }
}
