package app.kite.core.design.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.kite.core.design.LocalAppColors
import app.kite.core.design.LocalAppTypography
import kotlin.math.PI
import kotlin.math.sin

/**
 * The kite from the app icon, flying: it rides a slow figure-of-eight, leans into the turn,
 * and its tail ripples behind it. A screen-level "loading" that is worth a glance — the
 * small [AppSpinner] stays for inline rows and buttons. Everything is Canvas + one infinite
 * transition; no image assets, no library.
 */
@Composable
fun KiteLoader(modifier: Modifier = Modifier, size: Dp = 72.dp, caption: String? = null) {
    val colors = LocalAppColors.current
    val typography = LocalAppTypography.current
    val transition = rememberInfiniteTransition(label = "kiteLoader")
    // One phase drives everything, so the parts stay in sync: 0..1 over 2.6 s.
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 2600, easing = LinearEasing)),
        label = "kitePhase",
    )
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(size), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(size)) {
                val w = this.size.width
                val h = this.size.height
                val t = phase * 2f * PI.toFloat()
                // Figure-of-eight flight path, small amplitude.
                val dx = sin(t) * w * 0.10f
                val dy = sin(2f * t) * h * 0.06f
                // Lean into the horizontal motion.
                val tilt = sin(t) * 14f
                val accent = colors.accent
                val stroke = w * 0.075f
                val line = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
                translate(left = dx, top = dy) {
                    rotate(degrees = tilt, pivot = Offset(w * 0.5f, h * 0.45f)) {
                        val body =
                            Path().apply {
                                moveTo(w * 0.5f, h * 0.08f)
                                lineTo(w * 0.80f, h * 0.42f)
                                lineTo(w * 0.5f, h * 0.72f)
                                lineTo(w * 0.20f, h * 0.42f)
                                close()
                            }
                        drawPath(body, accent.copy(alpha = 0.16f))
                        drawPath(body, accent, style = line)
                        drawLine(accent, Offset(w * 0.5f, h * 0.08f), Offset(w * 0.5f, h * 0.72f), stroke, StrokeCap.Round)
                        drawLine(accent, Offset(w * 0.20f, h * 0.42f), Offset(w * 0.80f, h * 0.42f), stroke, StrokeCap.Round)
                        // Tail: a ribbon that lags behind the kite's sideways motion and
                        // settles into a soft wave. Its phase advances by whole turns per loop —
                        // an odd multiple made the ribbon snap back every time the cycle wrapped.
                        val tail = Path()
                        val segments = 24
                        val drift = -sin(t) * w * 0.06f // opposite to the lean, like a real tail
                        for (i in 0..segments) {
                            val f = i / segments.toFloat()
                            val wave = sin(f * 1.6f * PI.toFloat() - t * 2f) * w * 0.055f * f
                            val x = w * 0.5f + drift * f * f + wave
                            val y = h * 0.72f + f * h * 0.24f
                            if (i == 0) tail.moveTo(x, y) else tail.lineTo(x, y)
                        }
                        drawPath(
                            tail,
                            accent.copy(alpha = 0.75f),
                            style = Stroke(width = stroke * 0.6f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                        )
                        // Two small bows as filled dots on the ribbon.
                        for (k in listOf(0.4f, 0.8f)) {
                            val wave = sin(k * 1.6f * PI.toFloat() - t * 2f) * w * 0.055f * k
                            val x = w * 0.5f + drift * k * k + wave
                            val y = h * 0.72f + k * h * 0.24f
                            drawCircle(color = accent, radius = stroke * 0.55f, center = Offset(x, y))
                        }
                    }
                }
                // Soft shadow on the ground that breathes with the height.
                val shadowScale = 1f - (dy / (h * 0.06f)) * 0.25f
                drawOval(
                    color = Color.Black.copy(alpha = 0.06f),
                    topLeft = Offset(w * 0.5f - w * 0.22f * shadowScale, h * 0.93f),
                    size = androidx.compose.ui.geometry.Size(w * 0.44f * shadowScale, h * 0.05f),
                )
            }
        }
        if (caption != null) {
            Spacer(Modifier.height(10.dp))
            Text(text = caption, style = typography.footnote, color = colors.textSecondary)
        }
    }
}

/** Centered [KiteLoader] filling the width, for the loading state of a whole screen. */
@Composable
fun ScreenLoading(caption: String? = null, modifier: Modifier = Modifier, height: Dp = 220.dp) {
    Box(modifier.fillMaxWidth().height(height).padding(16.dp), contentAlignment = Alignment.Center) {
        KiteLoader(caption = caption)
    }
}
