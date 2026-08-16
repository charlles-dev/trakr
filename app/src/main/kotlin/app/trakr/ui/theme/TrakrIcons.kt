package app.trakr.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

// Ícones proprietários do Trakr — desenhados à mão, sem Material.
// (As abas da navegação usam os ícones oficiais do Material Design.)
// Todos têm viewport 24x24 e são tintados pelo parâmetro `tint` do Icon.

private fun trakrIconBuilder(name: String): ImageVector.Builder =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    )

private fun PathBuilder.ring(
    cx: Float,
    cy: Float,
    r: Float,
) {
    moveTo(cx, cy - r)
    arcTo(r, r, 0f, true, true, cx, cy + r)
    arcTo(r, r, 0f, true, true, cx, cy - r)
    close()
}

/** Sol (tema claro). */
val TrakrSun: ImageVector =
    trakrIconBuilder("TrakrSun").apply {
        path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
        ) {
            ring(12f, 12f, 4.5f)
        }
        path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
        ) {
            moveTo(12f, 2f)
            lineTo(12f, 4f)
            moveTo(12f, 20f)
            lineTo(12f, 22f)
            moveTo(2f, 12f)
            lineTo(4f, 12f)
            moveTo(20f, 12f)
            lineTo(22f, 12f)
            moveTo(4.93f, 4.93f)
            lineTo(6.34f, 6.34f)
            moveTo(17.66f, 17.66f)
            lineTo(19.07f, 19.07f)
            moveTo(4.93f, 19.07f)
            lineTo(6.34f, 17.66f)
            moveTo(17.66f, 6.34f)
            lineTo(19.07f, 4.93f)
        }
    }.build()

/** Lua crescente (tema escuro). */
val TrakrMoon: ImageVector =
    trakrIconBuilder("TrakrMoon").apply {
        path(fill = SolidColor(Color.Black), pathFillType = PathFillType.NonZero) {
            moveTo(21f, 12.79f)
            arcTo(9f, 9f, 0f, true, true, 11.21f, 3f)
            arcTo(7f, 7f, 0f, false, false, 21f, 12.79f)
            close()
        }
    }.build()

/** Logo Trakr (landing/public/logo.svg): "T" de barras + ponto, em verde-teal. */
val TrakrLogo: ImageVector =
    ImageVector.Builder(
        name = "TrakrLogo",
        defaultWidth = 108.dp,
        defaultHeight = 108.dp,
        viewportWidth = 108f,
        viewportHeight = 108f,
    ).apply {
        group(
            scaleX = 0.113014f,
            scaleY = 0.113014f,
            translationX = 21f,
            translationY = 22.92f,
        ) {
            path(fill = SolidColor(Color(0xFF20B898)), pathFillType = PathFillType.NonZero) {
                moveTo(38f, 0f)
                lineTo(546f, 0f)
                arcTo(38f, 38f, 0f, false, true, 584f, 38f)
                lineTo(584f, 74f)
                arcTo(38f, 38f, 0f, false, true, 546f, 112f)
                lineTo(38f, 112f)
                arcTo(38f, 38f, 0f, false, true, 0f, 74f)
                lineTo(0f, 38f)
                arcTo(38f, 38f, 0f, false, true, 38f, 0f)
                close()
            }
            path(fill = SolidColor(Color(0xFF20B898)), pathFillType = PathFillType.NonZero) {
                moveTo(38f, 145f)
                lineTo(546f, 145f)
                arcTo(38f, 38f, 0f, false, true, 584f, 183f)
                lineTo(584f, 219f)
                arcTo(38f, 38f, 0f, false, true, 546f, 257f)
                lineTo(38f, 257f)
                arcTo(38f, 38f, 0f, false, true, 0f, 219f)
                lineTo(0f, 183f)
                arcTo(38f, 38f, 0f, false, true, 38f, 145f)
                close()
            }
            path(fill = SolidColor(Color(0xFF20B898)), pathFillType = PathFillType.NonZero) {
                moveTo(270f, 145f)
                lineTo(314f, 145f)
                arcTo(44f, 44f, 0f, false, true, 358f, 189f)
                lineTo(358f, 493f)
                arcTo(44f, 44f, 0f, false, true, 314f, 537f)
                lineTo(270f, 537f)
                arcTo(44f, 44f, 0f, false, true, 226f, 493f)
                lineTo(226f, 189f)
                arcTo(44f, 44f, 0f, false, true, 270f, 145f)
                close()
            }
            path(fill = SolidColor(Color(0xFFF7FAF9)), pathFillType = PathFillType.NonZero) {
                moveTo(480f, 500f)
                arcTo(50f, 50f, 0f, true, false, 580f, 500f)
                arcTo(50f, 50f, 0f, true, false, 480f, 500f)
                close()
            }
        }
    }.build()
