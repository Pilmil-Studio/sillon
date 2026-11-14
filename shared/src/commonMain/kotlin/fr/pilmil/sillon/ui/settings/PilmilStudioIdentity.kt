package fr.pilmil.sillon.ui.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Emblème « L'Îlot Hors-Ligne » de Pilmil Studio dessiné en vectoriel pur.
 */
@Composable
fun PilmilStudioMark(
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    darkTheme: Boolean = isSystemInDarkTheme(),
) {
    val bgColor = if (darkTheme) Color(0xFF1E1D1A) else Color(0xFFFAF8F4)
    val inkColor = if (darkTheme) Color(0xFFF3EFE6) else Color(0xFF1E1D1A)
    val archColor = if (darkTheme) Color(0xFF4D9A82) else Color(0xFF285D4D)
    val amberColor = if (darkTheme) Color(0xFFF59E0B) else Color(0xFFD97706)

    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val s = w / 256f
        val center = Offset(w / 2f, w / 2f)

        drawCircle(color = bgColor, radius = 114f * s, center = center)
        drawCircle(
            color = inkColor,
            radius = 114f * s,
            center = center,
            style = Stroke(width = 10f * s),
        )
        drawArc(
            color = archColor,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(62f * s, 80f * s),
            size = Size(132f * s, 132f * s),
            style = Stroke(width = 22f * s, cap = StrokeCap.Round),
        )
        drawLine(
            color = inkColor,
            start = Offset(56f * s, 176f * s),
            end = Offset(200f * s, 176f * s),
            strokeWidth = 14f * s,
            cap = StrokeCap.Round,
        )
        drawCircle(
            color = amberColor,
            radius = 22f * s,
            center = Offset(128f * s, 134f * s),
        )
    }
}
