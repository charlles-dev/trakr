package app.trakr.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.trakr.R
import kotlinx.coroutines.delay

/**
 * Sequência de boot estilo terminal: reforça a persona "sistema de
 * monitoramento" na abertura do app.
 */
@Composable
fun BootOverlay(onDone: () -> Unit) {
    var step by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        delay(350)
        step = 1
        delay(380)
        step = 2
        delay(380)
        step = 3
        delay(1000)
        onDone()
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.CenterStart,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(14.dp)
                            .background(
                                MaterialTheme.colorScheme.primary,
                                CutCornerShape(4.dp),
                            ),
                )
                Text(
                    "TRAKR",
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    letterSpacing = 6.sp,
                )
                Text(
                    stringResource(R.string.boot_sys_tag),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 3.sp,
                )
            }

            Spacer(Modifier.height(6.dp))

            BootLine(stringResource(R.string.boot_sys_tag), stringResource(R.string.boot_line_kernel), 0, step)
            BootLine(stringResource(R.string.boot_ble_tag), stringResource(R.string.boot_line_scan), 1, step)
            BootLine(stringResource(R.string.boot_tracker_tag), stringResource(R.string.boot_line_sync), 2, step)
        }
    }
}

@Composable
private fun BootLine(
    module: String,
    message: String,
    index: Int,
    step: Int,
) {
    AnimatedVisibility(
        visible = step > index,
        enter = fadeIn(),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                stringResource(R.string.boot_prompt, module),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 1.5.sp,
            )
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
