package app.trakr.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
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
