package app.kite.core.design.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import app.kite.core.design.LocalAppColors
import app.kite.core.design.LocalAppTypography

/**
 * Apple-style offline notice: a slim full-width bar that slides in when the device loses
 * internet, with a wi-fi-with-slash glyph and a plain-Russian line telling the user some
 * features are unavailable. Enforcement keeps working offline — this is informational.
 */
@Composable
fun OfflineBanner(online: Boolean, modifier: Modifier = Modifier) {
    val colors = LocalAppColors.current
    val typography = LocalAppTypography.current
    AnimatedVisibility(
        visible = !online,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
        modifier = modifier,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(colors.textSecondary.copy(alpha = 0.14f))
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WifiSlashIcon(color = colors.textSecondary)
            Spacer(Modifier.width(10.dp))
            Column {
                Text(text = "Нет подключения к интернету", style = typography.subhead, color = colors.textPrimary)
                Text(text = "Часть функций недоступна", style = typography.caption, color = colors.textSecondary)
            }
        }
    }
}

@Composable
private fun WifiSlashIcon(color: Color) {
    Canvas(Modifier.size(22.dp)) {
        val w = size.width
        val h = size.height
        val stroke = w * 0.09f
        // Three wi-fi arcs of increasing radius, centered on the bottom dot.
        val cx = w / 2f
        val baseY = h * 0.82f
        listOf(0.5f, 0.34f, 0.18f).forEach { fraction ->
            val r = w * fraction
            drawArc(
                color = color,
                startAngle = 200f,
                sweepAngle = 140f,
                useCenter = false,
                topLeft = Offset(cx - r, baseY - r),
                size = Size(r * 2, r * 2),
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        // The dot.
        drawArc(
            color = color,
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = true,
            topLeft = Offset(cx - stroke, baseY - stroke),
            size = Size(stroke * 2, stroke * 2),
        )
        // Diagonal slash across the glyph.
        val slash =
            Path().apply {
                moveTo(w * 0.15f, h * 0.12f)
                lineTo(w * 0.85f, h * 0.88f)
            }
        drawPath(slash, color, style = Stroke(width = stroke * 1.1f, cap = StrokeCap.Round))
    }
}
