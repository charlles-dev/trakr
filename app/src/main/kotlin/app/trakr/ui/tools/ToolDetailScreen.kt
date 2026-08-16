package app.trakr.ui.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.trakr.R
import app.trakr.model.RssiSample
import app.trakr.model.Tool
import app.trakr.ui.components.SectionHeader
import app.trakr.ui.components.StatusBadge
import app.trakr.ui.components.maxContentWidth
import app.trakr.ui.theme.AlertRed
import app.trakr.ui.theme.AmberWarn
import app.trakr.ui.theme.MonospaceTypography
import app.trakr.ui.theme.NeonGreen
import app.trakr.ui.theme.TrakrRadar
import app.trakr.ui.theme.TrakrTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
private val dateTimeFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

/** Classifica o sinal para cor: forte (verde), médio (âmbar), fraco (vermelho). */
private fun rssiColor(rssi: Int): Color =
    when {
        rssi >= -60 -> NeonGreen
        rssi >= -80 -> AmberWarn
        else -> AlertRed
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolDetailScreen(
    tool: Tool,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onLocate: () -> Unit = {},
    viewModel: ToolDetailViewModel = viewModel(key = tool.id, factory = ToolDetailViewModel.Factory),
) {
    val samples by viewModel.samples.collectAsStateWithLifecycle(emptyList())

    LaunchedEffect(tool.epc) {
        viewModel.setEpc(tool.epc)
    }

    ToolDetailContent(
        tool = tool,
        samples = samples,
        onBack = onBack,
        onLocate = onLocate,
        modifier = modifier,
    )
}

/** Conteúdo da tela de detalhe (stateless: sem ViewModel, testável/previewável). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ToolDetailContent(
    tool: Tool,
    samples: List<RssiSample>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onLocate: () -> Unit = {},
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
    ) {
        TopAppBar(
            title = { Text(stringResource(R.string.title_details)) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.action_back),
                    )
                }
            },
            colors =
                TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
        )

        LazyColumn(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .maxContentWidth()
                    .fillMaxHeight()
                    .align(Alignment.CenterHorizontally),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier =
                            Modifier
                                .size(42.dp)
                                .clip(MaterialTheme.shapes.small)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.Build,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = tool.name,
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            text = tool.epc.ifBlank { "—" },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = MonospaceTypography,
                        )
                    }
                    StatusBadge(
                        text =
                            if (tool.present) {
                                stringResource(R.string.status_detected)
                            } else {
                                stringResource(R.string.status_absent)
                            },
                        color = if (tool.present) NeonGreen else AlertRed,
                    )
                }
            }

            item {
                Button(
                    onClick = onLocate,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        TrakrRadar,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.action_locate))
                }
            }

            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        DetailRow(
                            stringResource(R.string.detail_last_seen),
                            tool.lastSeenAt?.let { dateTimeFormat.format(Date(it)) }
                                ?: stringResource(R.string.detail_never),
                        )
                        DetailRow(
                            stringResource(R.string.detail_rssi),
                            tool.rssi?.let { stringResource(R.string.detail_dbm, it) }
                                ?: stringResource(R.string.detail_rssi_none),
                            tool.rssi?.let { rssiColor(it) },
                        )
                        DetailRow(
                            stringResource(R.string.detail_status),
                            if (tool.present) {
                                stringResource(R.string.detail_present)
                            } else {
                                stringResource(R.string.detail_absent)
                            },
                        )
                    }
                }
            }

            item {
                SectionHeader(stringResource(R.string.detail_history_title))
            }

            if (samples.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.detail_history_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(samples, key = { it.id }) { sample ->
                    RssiRow(sample)
                }
            }
        }
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    valueColor: Color? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = valueColor ?: MaterialTheme.colorScheme.onSurface,
            fontFamily = MonospaceTypography,
        )
    }
}

@Composable
private fun RssiRow(sample: RssiSample) {
    val fraction = ((sample.rssi + 100).coerceIn(0, 40) / 40f)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(rssiColor(sample.rssi)),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = timeFormat.format(Date(sample.ts)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = MonospaceTypography,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(R.string.detail_dbm, sample.rssi),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = rssiColor(sample.rssi),
                fontFamily = MonospaceTypography,
            )
            Spacer(Modifier.width(10.dp))
            Box(
                modifier =
                    Modifier
                        .height(6.dp)
                        .width(64.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            ) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth(fraction)
                            .height(6.dp)
                            .clip(CircleShape)
                            .background(rssiColor(sample.rssi)),
                )
            }
        }
    }
}

// ---------------- Previews ----------------

@Preview(showBackground = true, backgroundColor = 0xFF0B1210)
@Composable
internal fun ToolDetailContentPreview() {
    TrakrTheme(darkTheme = true) {
        ToolDetailContent(
            tool =
                Tool(
                    id = "1",
                    name = "Parafusadeira",
                    epc = "E28011606000020400000001",
                    present = true,
                    rssi = -52,
                    lastSeenAt = System.currentTimeMillis(),
                ),
            samples =
                listOf(
                    RssiSample(id = 1, epc = "E28011606000020400000001", rssi = -52),
                    RssiSample(id = 2, epc = "E28011606000020400000001", rssi = -64),
                ),
            onBack = {},
            modifier = Modifier.fillMaxSize(),
        )
    }
}
