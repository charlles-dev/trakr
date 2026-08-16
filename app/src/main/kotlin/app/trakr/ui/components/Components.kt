package app.trakr.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.trakr.R
import app.trakr.model.Tool
import app.trakr.ui.theme.AlertRed
import app.trakr.ui.theme.AmberWarn
import app.trakr.ui.theme.MonospaceTypography
import app.trakr.ui.theme.NeonGreen
import app.trakr.ui.theme.TrakrTheme

/** Ponto pulsante: sinaliza conexão ao vivo / presença. */
@Composable
fun PulseDot(
    color: Color,
    size: Dp = 10.dp,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "pulseAlpha",
    )
    Box(
        modifier =
            modifier
                .size(size)
                .alpha(alpha)
                .clip(CircleShape)
                .background(color),
    )
}

/** Pílula de status (ex: "DETECTADO", "AUSENTE", "LIVE"). */
@Composable
fun StatusBadge(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .clip(RoundedCornerShape(50))
                .background(color.copy(alpha = 0.14f))
                .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PulseDot(color = color, size = 6.dp)
            Spacer(Modifier.width(6.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = color,
            )
        }
    }
}

/** Card de ferramenta com avatar, nome e slot para ações/badges. */
@Composable
fun ToolCard(
    tool: Tool,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit = {},
) {
    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .then(
                    if (onClick != null) {
                        Modifier.clickable(onClick = onClick)
                    } else {
                        Modifier
                    },
                ),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
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
                    style = MaterialTheme.typography.titleMedium,
                )
                if (tool.epc.isNotBlank()) {
                    Text(
                        text = tool.epc.takeLast(8),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = MonospaceTypography,
                        maxLines = 1,
                    )
                }
            }
            trailing()
        }
    }
}

/** Botão de ação de card (ex: excluir ferramenta). */
@Composable
fun CardActionButton(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    IconButton(onClick = onClick, modifier = modifier) {
        Icon(
            Icons.Filled.Delete,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Estado vazio ilustrado: ícone em selo + título + dica. */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    hint: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(88.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(40.dp),
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = hint,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Tile de estatística (painel superior do dashboard). */
@Composable
fun StatTile(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                color = valueColor,
                fontFamily = MonospaceTypography,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Limita a largura do conteúdo (tablets/landscape) mantendo legibilidade. */
fun Modifier.maxContentWidth(): Modifier = this.widthIn(max = 640.dp)

/** Banner de estado de conexão com o rastreador. */
@Composable
fun ConnectionBanner(
    deviceName: String?,
    connected: Boolean,
    modifier: Modifier = Modifier,
    scanning: Boolean = false,
) {
    val (dot, badge, text) =
        when {
            connected ->
                Triple(
                    MaterialTheme.colorScheme.primary,
                    stringResource(R.string.connection_live),
                    deviceName ?: stringResource(R.string.connection_connected),
                )
            scanning ->
                Triple(
                    MaterialTheme.colorScheme.primary,
                    stringResource(R.string.connection_scanning),
                    stringResource(R.string.dashboard_scanning),
                )
            else ->
                Triple(
                    MaterialTheme.colorScheme.error,
                    stringResource(R.string.connection_offline),
                    stringResource(R.string.connection_none),
                )
        }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color =
            if (connected) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
            } else {
                MaterialTheme.colorScheme.error.copy(alpha = 0.06f)
            },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PulseDot(color = dot)
            Spacer(Modifier.width(10.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            StatusBadge(text = badge, color = dot)
        }
    }
}

/** Wordmark da marca: quadrado neon cortado + TRAKR com tracking largo. */
@Composable
fun TrakrWordmark(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(12.dp)
                    .background(MaterialTheme.colorScheme.primary, CutCornerShape(3.dp)),
        )
        Text(
            text = "TRAKR",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            letterSpacing = 4.sp,
        )
    }
}

/** Título de seção com filete decorativo. */
@Composable
fun SectionHeader(
    text: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(width = 4.dp, height = 16.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.primary),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

// ---------------- Previews ----------------

@Preview(showBackground = true, backgroundColor = 0xFF0B1210)
@Composable
private fun ToolCardPreview() {
    TrakrTheme(darkTheme = true) {
        ToolCard(
            tool =
                Tool(
                    id = "1",
                    name = "Parafusadeira",
                    epc = "E28011606000020400000001",
                    present = true,
                    rssi = -52,
                ),
            onClick = {},
            trailing = { StatusBadge(text = "DETECTADO", color = NeonGreen) },
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0B1210)
@Composable
private fun StatusBadgePreview() {
    TrakrTheme(darkTheme = true) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusBadge(text = "DETECTADO", color = NeonGreen)
            StatusBadge(text = "AUSENTE", color = AlertRed)
            StatusBadge(text = "LIVE", color = AmberWarn)
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0B1210)
@Composable
private fun EmptyStatePreview() {
    TrakrTheme(darkTheme = true) {
        EmptyState(
            icon = Icons.Filled.Build,
            title = "Nenhuma ferramenta",
            hint = "Adicione sua primeira ferramenta para começar",
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0B1210)
@Composable
private fun StatTilePreview() {
    TrakrTheme(darkTheme = true) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile(label = "FERRAMENTAS", value = "12")
            StatTile(label = "PRESENTES", value = "9", valueColor = NeonGreen)
            StatTile(label = "AUSENTES", value = "3", valueColor = AlertRed)
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0B1210)
@Composable
private fun ConnectionBannerPreview() {
    TrakrTheme(darkTheme = true) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ConnectionBanner(deviceName = "TRK-FINDER-01", connected = true)
            ConnectionBanner(deviceName = null, connected = false)
        }
    }
}
