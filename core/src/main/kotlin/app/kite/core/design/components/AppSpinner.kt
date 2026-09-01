package app.kite.core.design.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.kite.core.design.LocalAppColors

private const val SPOKE_COUNT = 8

/**
 * iOS-like activity indicator: eight spokes, the bright one stepping around the circle.
 * Used by [AppButton]'s `loading` state and standalone next to in-progress rows.
 */
@Composable
fun AppSpinner(modifier: Modifier = Modifier, color: Color = LocalAppColors.current.textSecondary, size: Dp = 20.dp) {
    val transition = rememberInfiniteTransition(label = "appSpinner")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = SPOKE_COUNT.toFloat(),
        animationSpec = infiniteRepeatable(tween(durationMillis = 800, easing = LinearEasing)),
        label = "appSpinnerStep",
    )
    Canvas(modifier.size(size)) {
        val step = progress.toInt() % SPOKE_COUNT
        val radius = this.size.minDimension / 2f
        val stroke = radius / 4f
        for (spoke in 0 until SPOKE_COUNT) {
            val distance = (spoke - step + SPOKE_COUNT) % SPOKE_COUNT
            val alpha = 1f - 0.7f * (distance / (SPOKE_COUNT - 1f))
            rotate(degrees = spoke * (360f / SPOKE_COUNT)) {
                drawLine(
                    color = color.copy(alpha = color.alpha * alpha),
                    start = Offset(center.x, stroke / 2f),
                    end = Offset(center.x, radius * 0.55f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}
