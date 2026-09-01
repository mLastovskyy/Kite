package app.kite.core.design.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import app.kite.core.design.LocalAppColors
import app.kite.core.design.LocalAppTypography

/** Tone of a [NoticeCard] — success uses a calm checkmark, info a neutral dot. */
enum class NoticeTone { Success, Info }

/**
 * Calm iOS-style inline notice: a soft tinted card with a small glyph and text — used for
 * confirmations instead of a bright green line. Deliberately muted (low-alpha tint, no
 * saturated fill), the way system confirmations read on iOS.
 */
@Composable
fun NoticeCard(text: String, modifier: Modifier = Modifier, tone: NoticeTone = NoticeTone.Success) {
    val colors = LocalAppColors.current
    val typography = LocalAppTypography.current
    val accent = if (tone == NoticeTone.Success) colors.success else colors.accent
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(accent.copy(alpha = 0.12f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CheckBadge(color = accent)
        Spacer(Modifier.width(10.dp))
        Text(text = text, style = typography.subhead, color = colors.textPrimary)
    }
}

@Composable
private fun CheckBadge(color: Color) {
    Canvas(Modifier.size(20.dp)) {
        val w = size.width
        drawCircle(color = color, radius = w / 2f)
        // White check inside the filled circle.
        val check =
            Path().apply {
                moveTo(w * 0.28f, w * 0.52f)
                lineTo(w * 0.44f, w * 0.68f)
                lineTo(w * 0.74f, w * 0.34f)
            }
        drawPath(check, Color.White, style = Stroke(width = w * 0.1f, cap = StrokeCap.Round))
    }
}
