package app.trakr.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val TrakrGreen = Color(0xFF2F6F4E)
private val TrakrGreenDark = Color(0xFF7BC2A0)

private val LightColors = lightColorScheme(
    primary = TrakrGreen,
    secondary = Color(0xFF4E7D5C),
    tertiary = Color(0xFFB98D4E),
)

private val DarkColors = darkColorScheme(
    primary = TrakrGreenDark,
    secondary = Color(0xFF8FC8B0),
    tertiary = Color(0xFFD9B983),
)

@Composable
fun TrakrTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}