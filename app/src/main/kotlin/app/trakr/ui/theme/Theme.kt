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

// Cores da marca: verde-neon tático "ops room" sobre grafite obsidiana profundo.
val NeonGreen = Color(0xFF00F5B4)
val AmberWarn = Color(0xFFFFB300)
val AlertRed = Color(0xFFFF453A)

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
        onPrimary = Color(0xFF003826),
        primaryContainer = Color(0xFF00523A),
        onPrimaryContainer = Color(0xFF74FFD1),
        secondary = Color(0xFF4EE8B8),
        onSecondary = Color(0xFF003826),
        secondaryContainer = Color(0xFF0D3D2F),
        onSecondaryContainer = Color(0xFF86F8CF),
        tertiary = AmberWarn,
        onTertiary = Color(0xFF452B00),
        tertiaryContainer = Color(0xFF633F00),
        onTertiaryContainer = Color(0xFFFFDF9E),
        error = AlertRed,
        onError = Color(0xFF410002),
        errorContainer = Color(0xFF5C0007),
        onErrorContainer = Color(0xFFFFDAD6),
        background = Color(0xFF080D0B),
        onBackground = Color(0xFFF0F5F2),
        surface = Color(0xFF0E1613),
        onSurface = Color(0xFFF0F5F2),
        surfaceVariant = Color(0xFF141F1A),
        onSurfaceVariant = Color(0xFF90A89C),
        surfaceContainer = Color(0xFF0D1411),
        surfaceContainerHigh = Color(0xFF15221C),
        surfaceContainerHighest = Color(0xFF1D2E26),
        outline = Color(0xFF2D4238),
        outlineVariant = Color(0xFF1C2C24),
        inverseSurface = Color(0xFFF0F5F2),
        inverseOnSurface = Color(0xFF080D0B),
        inversePrimary = Color(0xFF00A878),
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
