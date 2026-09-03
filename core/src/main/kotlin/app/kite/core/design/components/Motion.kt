package app.kite.core.design.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.kite.core.design.LocalAppColors
import app.kite.core.design.standardSpring

/**
 * Content that has just arrived (after a load) slides up a little and fades in on the
 * standard spring — the iOS "settle into place" feel. Wrap the loaded state, not the
 * placeholder: `AppearIn(visible = data != null) { … }`.
 */
@Composable
fun AppearIn(visible: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(standardSpring()) + slideInVertically(standardSpring()) { it / 8 },
        exit = fadeOut(standardSpring()),
    ) { content() }
}

/**
 * A number that rolls when it changes — the new value slides in from below (or above when
 * decreasing) while the old one slides out, like the iOS clock digits. Keep [text] short.
 */
@Composable
fun RollingText(text: String, style: TextStyle, color: Color, modifier: Modifier = Modifier) {
    var previous by remember { mutableStateOf(text) }
    val goingUp = text.length > previous.length || (text.length == previous.length && text > previous)
    LaunchedEffect(text) { previous = text }
    AnimatedContent(
        targetState = text,
        modifier = modifier,
        transitionSpec = {
            val direction = if (goingUp) 1 else -1
            (slideInVertically(standardSpring()) { direction * it / 2 } + fadeIn(standardSpring()))
                .togetherWith(slideOutVertically(standardSpring()) { -direction * it / 2 } + fadeOut(standardSpring()))
        },
        label = "rollingText",
    ) { value ->
        Text(text = value, style = style, color = color)
    }
}

/**
 * Success mark: a circle that pops in on a bouncy spring while the check is drawn stroke by
 * stroke. Shown for a moment after the parent confirms something (a task, a request) so the
 * action is felt, not just logged.
 */
@Composable
fun CheckmarkBurst(modifier: Modifier = Modifier, size: Dp = 56.dp, color: Color = LocalAppColors.current.success) {
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { started = true }
    val scale by animateFloatAsState(
        targetValue = if (started) 1f else 0.4f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 420f),
        label = "checkScale",
    )
    val progress by animateFloatAsState(
        targetValue = if (started) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.9f, stiffness = 180f),
        label = "checkProgress",
    )
    Box(
        modifier.size(size).graphicsLayer {
            scaleX = scale
            scaleY = scale
            alpha = scale.coerceIn(0f, 1f)
        },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(size)) {
            val radius = this.size.minDimension / 2f
            drawCircle(color = color, radius = radius)
            val stroke = radius * 0.16f
            val full =
                Path().apply {
                    moveTo(this@Canvas.size.width * 0.30f, this@Canvas.size.height * 0.52f)
                    lineTo(this@Canvas.size.width * 0.44f, this@Canvas.size.height * 0.66f)
                    lineTo(this@Canvas.size.width * 0.71f, this@Canvas.size.height * 0.37f)
                }
            val measure = PathMeasure().apply { setPath(full, false) }
            val partial = Path()
            measure.getSegment(0f, measure.length * progress, partial, true)
            drawPath(partial, Color.White, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

/** Convenience for drawing helpers that need an origin. */
internal fun Offset.shifted(dx: Float, dy: Float): Offset = Offset(x + dx, y + dy)
