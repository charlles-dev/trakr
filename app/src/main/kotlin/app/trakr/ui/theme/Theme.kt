package app.trakr.ui.theme

import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.trakr.R

// Cores da marca: verde neon "ops room" sobre grafite profundo.
val NeonGreen = Color(0xFF2EE69B)
val AmberWarn = Color(0xFFF0AE4E)
val AlertRed = Color(0xFFFF6B6B)

// ---------------- Tipografia da marca ----------------
// Space Grotesk: títulos e interface (industrial, geométrica).
// JetBrains Mono: leituras de painel (RSSI, contadores, clocks).

private val GroteskFont =
    FontFamily(
        Font(R.font.space_grotesk_var, FontWeight.Normal),
        Font(R.font.space_grotesk_var, FontWeight.Medium),
        Font(R.font.space_grotesk_var, FontWeight.SemiBold),
        Font(R.font.space_grotesk_var, FontWeight.Bold),
    )

private val MonoFont =
    FontFamily(
        Font(R.font.jetbrains_mono_var, FontWeight.Normal),
        Font(R.font.jetbrains_mono_var, FontWeight.Bold),
    )

/** Família de display da marca (cabeçalhos, títulos, labels). */
val TrakrDisplay: FontFamily = GroteskFont

/** Família monoespaçada da marca (dados/leituras). */
val MonospaceTypography: FontFamily = MonoFont

// ---------------- Tema escuro (padrão do produto) ----------------

private val DarkColors =
    darkColorScheme(
        primary = NeonGreen,
        onPrimary = Color(0xFF06231A),
        primaryContainer = Color(0xFF0E3B2A),
        onPrimaryContainer = Color(0xFF9BF5CF),
        secondary = Color(0xFF5BC9A4),
        onSecondary = Color(0xFF06231A),
        secondaryContainer = Color(0xFF11402F),
        onSecondaryContainer = Color(0xFFA8EFD3),
        tertiary = AmberWarn,
        onTertiary = Color(0xFF3A2A08),
        tertiaryContainer = Color(0xFF4A3810),
        onTertiaryContainer = Color(0xFFFFE2AE),
        error = AlertRed,
        onError = Color(0xFF3A0B0B),
        errorContainer = Color(0xFF4A1F1F),
        onErrorContainer = Color(0xFFFFD3D3),
        background = Color(0xFF0B1210),
        onBackground = Color(0xFFE4EDE8),
        surface = Color(0xFF111A16),
        onSurface = Color(0xFFE4EDE8),
        surfaceVariant = Color(0xFF1B2621),
        onSurfaceVariant = Color(0xFF9FB3A9),
        surfaceContainer = Color(0xFF0F1814),
        surfaceContainerHigh = Color(0xFF151F1A),
        surfaceContainerHighest = Color(0xFF1B2621),
        outline = Color(0xFF3A4A42),
        outlineVariant = Color(0xFF26332D),
        inverseSurface = Color(0xFFE4EDE8),
        inverseOnSurface = Color(0xFF0B1210),
        inversePrimary = Color(0xFF0E7A52),
        scrim = Color(0xFF000000),
        surfaceTint = NeonGreen,
    )

// ---------------- Tema claro (variante de acessibilidade) ----------------

private val LightColors =
    lightColorScheme(
        primary = Color(0xFF0E7A52),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFB8F3D4),
        onPrimaryContainer = Color(0xFF00281B),
        secondary = Color(0xFF2E7D59),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFD1F6DE),
        onSecondaryContainer = Color(0xFF032B18),
        tertiary = Color(0xFF9A6B1F),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFFFDFA8),
        onTertiaryContainer = Color(0xFF312000),
        error = Color(0xFFBA1A1A),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),
        background = Color(0xFFF4F7F5),
        onBackground = Color(0xFF171D1A),
        surface = Color(0xFFFFFFFF),
        onSurface = Color(0xFF171D1A),
        surfaceVariant = Color(0xFFE1EAE4),
        onSurfaceVariant = Color(0xFF404943),
        outline = Color(0xFF707973),
        outlineVariant = Color(0xFFC4CEC7),
        scrim = Color(0xFF000000),
    )

// ---------------- Tipografia ----------------
// Monofamília Space Grotesk na interface (identidade forte); valores de
// painel em JetBrains Mono (via MonospaceTypography).

private val TrakrTypography =
    Typography(
        displayLarge = Typography().displayLarge.copy(fontFamily = GroteskFont, fontWeight = FontWeight.Bold),
        displayMedium = Typography().displayMedium.copy(fontFamily = GroteskFont, fontWeight = FontWeight.Bold),
        displaySmall = Typography().displaySmall.copy(fontFamily = GroteskFont, fontWeight = FontWeight.Bold),
        headlineLarge =
            Typography().headlineLarge.copy(
                fontFamily = GroteskFont,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5).sp,
            ),
        headlineMedium =
            Typography().headlineMedium.copy(
                fontFamily = GroteskFont,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5).sp,
            ),
        headlineSmall =
            Typography().headlineSmall.copy(
                fontFamily = GroteskFont,
                fontWeight = FontWeight.SemiBold,
            ),
        titleLarge =
            Typography().titleLarge.copy(
                fontFamily = GroteskFont,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.2).sp,
            ),
        titleMedium =
            Typography().titleMedium.copy(
                fontFamily = GroteskFont,
                fontWeight = FontWeight.SemiBold,
            ),
        titleSmall =
            Typography().titleSmall.copy(
                fontFamily = GroteskFont,
                fontWeight = FontWeight.SemiBold,
            ),
        bodyLarge = Typography().bodyLarge.copy(fontFamily = GroteskFont),
        bodyMedium = Typography().bodyMedium.copy(fontFamily = GroteskFont),
        bodySmall = Typography().bodySmall.copy(fontFamily = GroteskFont),
        labelLarge =
            Typography().labelLarge.copy(
                fontFamily = GroteskFont,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.6.sp,
            ),
        labelMedium =
            Typography().labelMedium.copy(
                fontFamily = GroteskFont,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.8.sp,
            ),
        labelSmall =
            Typography().labelSmall.copy(
                fontFamily = GroteskFont,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.0.sp,
            ),
    )

// Cantos cortados em 45°: a assinatura geométrica do Trakr (estilo industrial).

private val TrakrShapes =
    Shapes(
        extraSmall = CutCornerShape(6.dp),
        small = CutCornerShape(10.dp),
        medium = CutCornerShape(14.dp),
        large = CutCornerShape(20.dp),
        extraLarge = CutCornerShape(28.dp),
    )

@Composable
fun TrakrTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = TrakrTypography,
        shapes = TrakrShapes,
        content = content,
    )
}
