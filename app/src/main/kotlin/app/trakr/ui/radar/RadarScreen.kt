@file:Suppress("ktlint:standard:max-line-length", "MaxLineLength")

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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Navigation
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.trakr.R
import app.trakr.core.ble.OtaManager
import app.trakr.core.ble.OtaResult
import app.trakr.model.RadarReport
import app.trakr.model.Tool
import app.trakr.ui.components.StatusBadge
import app.trakr.ui.components.maxContentWidth
import app.trakr.ui.motion.pressScale
import app.trakr.ui.theme.AlertRed
import app.trakr.ui.theme.AmberWarn
import app.trakr.ui.theme.MonospaceTypography
import app.trakr.ui.theme.NeonGreen
import app.trakr.ui.theme.TrakrTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RadarScreen(
    modifier: Modifier = Modifier,
    pendingTargetId: String? = null,
    viewModel: RadarViewModel = viewModel(factory = RadarViewModel.Factory),
) {
    val devices by viewModel.devices.collectAsStateWithLifecycle()
    val activeReport by viewModel.activeReport.collectAsStateWithLifecycle()
    val liveReport by viewModel.liveReport.collectAsStateWithLifecycle()
    val multiReport by viewModel.multiReport.collectAsStateWithLifecycle()
    val tools by viewModel.tools.collectAsStateWithLifecycle(initialValue = emptyList())
    val targetId by viewModel.targetId.collectAsStateWithLifecycle()
    val running by viewModel.running.collectAsStateWithLifecycle()
    val simulationRunning by viewModel.simulationRunning.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()

    val isSearching = running || simulationRunning

    // Deep link de "Localizar": pré-seleciona o alvo.
    LaunchedEffect(pendingTargetId, tools, targetId) {
        val pending = pendingTargetId ?: return@LaunchedEffect
        if (tools.any { it.id == pending } && targetId != pending) {
            viewModel.selectTarget(pending)
        }
    }

    // Atualização de firmware (OTA)
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

    // Feedback háptico inteligente com cadência proporcional à proximidade
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(isSearching, activeReport?.present, activeReport?.rssi) {
        if (!isSearching || activeReport?.present != true) return@LaunchedEffect
        val rssi = activeReport?.rssi ?: -100
        val delayMs = ((100 + rssi).coerceIn(5, 65) * 12L).coerceIn(120L, 900L)
        while (isActive && isSearching) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            delay(delayMs)
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            stringResource(R.string.tab_radar),
                            fontWeight = FontWeight.Bold,
                        )
                        val badgeText =
                            when {
                                devices.isNotEmpty() -> "BLE ONLINE"
                                simulationRunning -> "SIMULADOR HUD"
                                else -> "PRONTO"
                            }
                        val badgeColor =
                            when {
                                devices.isNotEmpty() -> NeonGreen
                                simulationRunning -> AmberWarn
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        StatusBadge(text = badgeText, color = badgeColor)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                actions = {
                    if (devices.isNotEmpty()) {
                        IconButton(
                            onClick = { otaLauncher.launch(arrayOf("application/octet-stream", "application/zip", "*/*")) },
                            enabled = otaProgress == null,
                        ) {
                            Icon(
                                Icons.Filled.Refresh,
                                contentDescription = stringResource(R.string.ota_action),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
            )
        },
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
                        .fillMaxSize()
                        .maxContentWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // Painel de HUD do Radar
                RadarDisplayCard(
                    report = activeReport,
                    running = isSearching,
                    liveReport = liveReport,
                    multiReport = multiReport,
                )

                // Osciloscópio de Tendência RSSI em tempo real
                val rssiHistory by viewModel.rssiHistory.collectAsStateWithLifecycle()
                RssiOscilloscopeCard(
                    history = rssiHistory,
                    isSearching = isSearching,
                )

                // Seletor de ferramenta-alvo
                TargetPicker(
                    tools = tools,
                    targetId = targetId,
                    onSelect = viewModel::selectTarget,
                )

                // Botões de ação principais
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Button(
                        onClick = viewModel::start,
                        enabled = !isSearching,
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = NeonGreen,
                                contentColor = Color(0xFF003826),
                            ),
                        modifier =
                            Modifier
                                .weight(1f)
                                .height(48.dp)
                                .pressScale(),
                    ) {
                        Text(
                            if (devices.isEmpty()) "SIMULAR BUSCA" else stringResource(R.string.radar_start),
                            fontWeight = FontWeight.Bold,
                            fontFamily = MonospaceTypography,
                        )
                    }
                    OutlinedButton(
                        onClick = viewModel::stop,
                        enabled = isSearching,
                        colors =
                            ButtonDefaults.outlinedButtonColors(
                                contentColor = AlertRed,
                            ),
                        border = BorderStroke(1.dp, if (isSearching) AlertRed else MaterialTheme.colorScheme.outlineVariant),
                        modifier =
                            Modifier
                                .height(48.dp)
                                .pressScale(),
                    ) {
                        Text(
                            stringResource(R.string.radar_stop),
                            fontWeight = FontWeight.Bold,
                            fontFamily = MonospaceTypography,
                        )
                    }
                }

                // Modos avançados (Ao Vivo & Multi-Alvo)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedButton(
                        onClick = viewModel::startLive,
                        enabled = !isSearching,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.weight(1f).pressScale(),
                    ) {
                        Text(
                            "MODO AO VIVO",
                            fontFamily = MonospaceTypography,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                    OutlinedButton(
                        onClick = viewModel::startMulti,
                        enabled = !isSearching,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.weight(1f).pressScale(),
                    ) {
                        Text(
                            "MULTI-ALVO",
                            fontFamily = MonospaceTypography,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }

                message?.let {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                        border = BorderStroke(1.dp, AlertRed.copy(alpha = 0.5f)),
                    ) {
                        Text(
                            text = it.resolve(context),
                            style = MaterialTheme.typography.bodySmall,
                            color = AlertRed,
                            fontFamily = MonospaceTypography,
                            modifier = Modifier.padding(10.dp),
                        )
                    }
                }

                otaProgress?.let { progress ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        border = BorderStroke(1.dp, NeonGreen.copy(alpha = 0.4f)),
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
                                color = NeonGreen,
                            )
                        }
                    }
                }

                if (otaDone) {
                    Text(
                        text = stringResource(R.string.ota_done),
                        style = MaterialTheme.typography.bodySmall,
                        color = NeonGreen,
                        fontFamily = MonospaceTypography,
                    )
                }
                otaError?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = AlertRed,
                        fontFamily = MonospaceTypography,
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
    val normalized = if (present) ((rssi + 85).toFloat() / 55f).coerceIn(0f, 1f) else 0f

    val statusText =
        when {
            !running -> "STANDBY"
            report == null && liveReport == null && multiReport == null -> "SINCRONIZANDO..."
            liveReport != null -> "AO VIVO: ${liveReport.reads.size} TAGS"
            multiReport != null -> "MULTI: ${multiReport.ranking.size} ALVOS"
            !present -> "SEM SINAL"
            rssi > -45 -> "ALVO TRAVADO"
            else -> "SINAL DETECTADO"
        }

    val directionText =
        when (hint) {
            "continue" -> "+$delta dBm • APROXIMANDO"
            "turn_around" -> "$delta dBm • RETROCEDA"
            "hold" -> "SINAL ESTÁVEL"
            else -> "VARRENDO..."
        }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, if (running) NeonGreen.copy(alpha = 0.35f) else MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Radar visual HUD Canvas
            RadarSweep(
                running = running,
                present = present,
                normalized = normalized,
                modifier = Modifier.size(190.dp),
            )

            Spacer(Modifier.width(14.dp))

            // Telemetria e medidor em barras LED
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = "POTÊNCIA DE SINAL",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 0.8.sp,
                )

                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = if (present) "$rssi" else "--",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        fontFamily = MonospaceTypography,
                        color = if (present) NeonGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "dBm",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = MonospaceTypography,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }

                // Medidor Segmentado de Barras em LED
                SignalLedLadder(
                    normalized = normalized,
                    active = running && present,
                )

                // Pílula de Direção e Status
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        if (running && present) {
                            Icon(
                                Icons.Filled.Navigation,
                                contentDescription = null,
                                tint = NeonGreen,
                                modifier = Modifier.size(12.dp),
                            )
                            Text(
                                text = directionText,
                                style = MaterialTheme.typography.labelSmall,
                                color = NeonGreen,
                                fontFamily = MonospaceTypography,
                                fontWeight = FontWeight.Bold,
                            )
                        } else {
                            Text(
                                text = statusText,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontFamily = MonospaceTypography,
                            )
                        }
                    }
                }

                liveReport?.let { live ->
                    Text(
                        text = live.reads.take(2).joinToString("\n") { "${it.tag.takeLast(6)}: ${it.rssi} dBm" },
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = MonospaceTypography,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                multiReport?.let { multi ->
                    Text(
                        text = multi.ranking.take(2).joinToString("\n") { "${it.tag.takeLast(6)}: ${it.rssi} dBm" },
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = MonospaceTypography,
                        color = NeonGreen,
                    )
                }
            }
        }
    }
}

/** Escala de LEDs em barras horizontais (HUD). */
@Composable
private fun SignalLedLadder(
    normalized: Float,
    active: Boolean,
    segments: Int = 12,
    modifier: Modifier = Modifier,
) {
    val activeCount = if (active) (normalized * segments).toInt().coerceIn(1, segments) else 0

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        repeat(segments) { index ->
            val isLit = index < activeCount
            val segmentColor =
                when {
                    !isLit -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    index >= (segments * 0.75) -> NeonGreen
                    index >= (segments * 0.4) -> Color(0xFF4EE8B8)
                    else -> AmberWarn
                }
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .height(8.dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(segmentColor),
            )
        }
    }
}

/** Varredura circular tática: retículo angular, anéis graduados, feixe giratório e ondas de alvo. */
@Composable
private fun RadarSweep(
    running: Boolean,
    present: Boolean,
    normalized: Float,
    modifier: Modifier = Modifier,
) {
    val theme = MaterialTheme.colorScheme
    val pulseTransition = rememberInfiniteTransition(label = "pulse")

    val rippleScale by pulseTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1.8f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart),
        label = "rippleScale",
    )

    val rippleAlpha by pulseTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart),
        label = "rippleAlpha",
    )

    val rotation by rememberInfiniteTransition(label = "sweep").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec =
            infiniteRepeatable(
                tween(if (present) 1000 else 2200, easing = LinearEasing),
                RepeatMode.Restart,
            ),
        label = "sweepRotation",
    )

    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val outerRadius = size.minDimension / 2f - 14.dp.toPx()

        // Moldura HUD: cantoneiras táticas nos 4 cantos
        val bracket = 10.dp.toPx()
        val inset = 4.dp.toPx()
        val frame = theme.primary.copy(alpha = 0.5f)
        val stroke = 2f

        drawLine(frame, Offset(inset, inset + bracket), Offset(inset, inset), stroke)
        drawLine(frame, Offset(inset, inset), Offset(inset + bracket, inset), stroke)
        drawLine(frame, Offset(size.width - inset, inset + bracket), Offset(size.width - inset, inset), stroke)
        drawLine(frame, Offset(size.width - inset, inset), Offset(size.width - inset - bracket, inset), stroke)
        drawLine(frame, Offset(inset, size.height - inset - bracket), Offset(inset, size.height - inset), stroke)
        drawLine(frame, Offset(inset, size.height - inset), Offset(inset + bracket, size.height - inset), stroke)
        drawLine(frame, Offset(size.width - inset, size.height - inset - bracket), Offset(size.width - inset, size.height - inset), stroke)
        drawLine(frame, Offset(size.width - inset, size.height - inset), Offset(size.width - inset - bracket, size.height - inset), stroke)

        // Linhas de Mira (Eixo Cruzado)
        drawLine(
            color = theme.outline.copy(alpha = 0.25f),
            start = Offset(center.x - outerRadius, center.y),
            end = Offset(center.x + outerRadius, center.y),
            strokeWidth = 1f,
        )
        drawLine(
            color = theme.outline.copy(alpha = 0.25f),
            start = Offset(center.x, center.y - outerRadius),
            end = Offset(center.x, center.y + outerRadius),
            strokeWidth = 1f,
        )

        // Ticks Angulares a cada 30 graus
        for (deg in 0 until 360 step 30) {
            val rad = Math.toRadians(deg.toDouble())
            val tickLen = if (deg % 90 == 0) 8.dp.toPx() else 4.dp.toPx()
            val startX = center.x + (outerRadius - tickLen) * cos(rad).toFloat()
            val startY = center.y + (outerRadius - tickLen) * sin(rad).toFloat()
            val endX = center.x + outerRadius * cos(rad).toFloat()
            val endY = center.y + outerRadius * sin(rad).toFloat()
            drawLine(
                color = theme.outline.copy(alpha = 0.45f),
                start = Offset(startX, startY),
                end = Offset(endX, endY),
                strokeWidth = 1.5f,
            )
        }

        // Anéis Concêntricos de Alcance
        val rings = listOf(0.30f, 0.60f, 0.85f, 1f)
        rings.forEach { factor ->
            drawCircle(
                color = theme.outline.copy(alpha = 0.25f),
                radius = outerRadius * factor,
                center = center,
                style = Stroke(width = 1f),
            )
        }

        // Ponto focal central
        drawCircle(
            color = theme.primary.copy(alpha = 0.8f),
            radius = 3.dp.toPx(),
            center = center,
        )

        // Feixe de varredura giratório
        if (running) {
            rotate(rotation, center) {
                // Feixe com gradiente angular
                drawArc(
                    brush =
                        Brush.radialGradient(
                            colors = listOf(theme.primary.copy(alpha = 0.35f), Color.Transparent),
                            center = center,
                            radius = outerRadius,
                        ),
                    startAngle = -45f,
                    sweepAngle = 45f,
                    useCenter = true,
                    topLeft = Offset(center.x - outerRadius, center.y - outerRadius),
                    size = Size(outerRadius * 2f, outerRadius * 2f),
                )
                // Linha de varredura
                drawLine(
                    color = theme.primary,
                    start = center,
                    end = Offset(center.x + outerRadius, center.y),
                    strokeWidth = 2f,
                    cap = StrokeCap.Round,
                )

                // Blip e Ondas do Alvo
                if (present) {
                    val blipDist = outerRadius * (0.25f + 0.65f * (1f - normalized))
                    val blipPos = Offset(center.x + blipDist, center.y)

                    // Onda de choque / propagação RFID
                    val rippleR = 18.dp.toPx() * rippleScale
                    drawCircle(
                        color = theme.primary.copy(alpha = rippleAlpha * 0.7f),
                        radius = rippleR,
                        center = blipPos,
                        style = Stroke(width = 1.5f),
                    )

                    // Blip central brilhante
                    drawCircle(
                        color = theme.primary,
                        radius = 4.5.dp.toPx(),
                        center = blipPos,
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
    val selectedTool = tools.firstOrNull { it.id == targetId }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "ALVO DE RASTREAMENTO",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 0.8.sp,
            )
            Box {
                OutlinedButton(
                    onClick = { expanded = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.small,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                Icons.Filled.Build,
                                contentDescription = null,
                                tint = NeonGreen,
                                modifier = Modifier.size(16.dp),
                            )
                            val placeholder =
                                if (tools.isEmpty()) {
                                    "Nenhuma ferramenta cadastrada"
                                } else {
                                    "Selecione a ferramenta..."
                                }
                            Text(
                                text = selectedTool?.name ?: placeholder,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        if (selectedTool != null && selectedTool.epc.isNotBlank()) {
                            Text(
                                text = selectedTool.epc.takeLast(6),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontFamily = MonospaceTypography,
                            )
                        }
                    }
                }
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                ) {
                    tools.forEach { tool ->
                        DropdownMenuItem(
                            text = {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(tool.name, style = MaterialTheme.typography.bodyMedium)
                                    if (tool.epc.isNotBlank()) {
                                        Text(
                                            tool.epc.takeLast(6),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontFamily = MonospaceTypography,
                                        )
                                    }
                                }
                            },
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
}

@Composable
private fun RssiOscilloscopeCard(
    history: List<Int>,
    isSearching: Boolean,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, if (isSearching) NeonGreen.copy(alpha = 0.35f) else MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "OSCILOSCÓPIO RSSI // ÚLTIMOS 15S",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = MonospaceTypography,
                )
                val trend =
                    when {
                        history.size < 3 -> "CALIBRANDO"
                        history.takeLast(3).let { it[2] > it[0] + 3 } -> "▲ APROXIMANDO"
                        history.takeLast(3).let { it[2] < it[0] - 3 } -> "▼ AFASTANDO"
                        else -> "● SINAL ESTÁVEL"
                    }
                Text(
                    trend,
                    style = MaterialTheme.typography.labelSmall,
                    color =
                        if (trend.contains("▲")) {
                            NeonGreen
                        } else if (trend.contains("▼")) {
                            AlertRed
                        } else {
                            AmberWarn
                        },
                    fontFamily = MonospaceTypography,
                    fontWeight = FontWeight.Bold,
                )
            }

            Canvas(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(80.dp),
            ) {
                val width = size.width
                val height = size.height

                // Linhas de grade
                val gridLines = listOf(0.25f, 0.5f, 0.75f)
                gridLines.forEach { frac ->
                    drawLine(
                        color = Color.White.copy(alpha = 0.06f),
                        start = Offset(0f, height * frac),
                        end = Offset(width, height * frac),
                        strokeWidth = 1.dp.toPx(),
                    )
                }

                if (history.size > 1) {
                    val points =
                        history.mapIndexed { idx, rssi ->
                            val x = (idx.toFloat() / (history.size - 1).coerceAtLeast(1)) * width
                            val norm = ((rssi + 85).toFloat() / 55f).coerceIn(0f, 1f)
                            val y = height - (norm * height)
                            Offset(x, y)
                        }

                    val path =
                        androidx.compose.ui.graphics.Path().apply {
                            moveTo(points[0].x, points[0].y)
                            for (i in 1 until points.size) {
                                lineTo(points[i].x, points[i].y)
                            }
                        }

                    val fillPath =
                        androidx.compose.ui.graphics.Path().apply {
                            addPath(path)
                            lineTo(points.last().x, height)
                            lineTo(points.first().x, height)
                            close()
                        }

                    drawPath(
                        path = fillPath,
                        brush =
                            androidx.compose.ui.graphics.Brush.verticalGradient(
                                colors = listOf(NeonGreen.copy(alpha = 0.25f), Color.Transparent),
                                startY = 0f,
                                endY = height,
                            ),
                    )

                    drawPath(
                        path = path,
                        color = NeonGreen,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()),
                    )

                    drawCircle(
                        color = NeonGreen,
                        radius = 4.dp.toPx(),
                        center = points.last(),
                    )
                }
            }
        }
    }
}

// ---------------- Previews ----------------

@Preview(showBackground = true, backgroundColor = 0xFF080D0B)
@Composable
internal fun RadarDisplayCardPreview() {
    TrakrTheme(darkTheme = true) {
        RadarDisplayCard(
            report = RadarReport(tag = "E280116001", rssi = -48, present = true, hint = "continue", delta = 3),
            running = true,
        )
    }
}
