package app.kite.core.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import app.kite.core.design.LocalAppColors
import app.kite.core.design.LocalAppTypography

/**
 * iOS-style alert: a compact centered card with a bold title, a short message, and one or
 * two stacked-then-side-by-side buttons split by hairlines. [destructive] tints the confirm
 * action red, like a system alert for a consequential action. Dismiss on scrim tap = cancel.
 */
@Composable
fun AppDialog(
    title: String,
    message: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    cancelText: String = "Отмена",
    destructive: Boolean = false,
) {
    val colors = LocalAppColors.current
    val typography = LocalAppTypography.current
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .width(300.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(colors.bgBase),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(20.dp))
            Text(
                text = title,
                style = typography.headline,
                color = colors.textPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = message,
                style = typography.subhead,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(18.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(colors.separator))
            // Like a system alert: side by side while both labels are short, otherwise stacked
            // (confirm on top, cancel below) — a long label («Подать сигнал») in a half-width
            // cell wrapped onto two lines and the letters piled up.
            val stacked = confirmText.length > MAX_SIDE_BY_SIDE || cancelText.length > MAX_SIDE_BY_SIDE
            if (stacked) {
                DialogButton(
                    text = confirmText,
                    color = if (destructive) colors.danger else colors.accent,
                    weight = FontWeight.SemiBold,
                    onClick = onConfirm,
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                )
                Box(Modifier.fillMaxWidth().height(1.dp).background(colors.separator))
                DialogButton(
                    text = cancelText,
                    color = colors.accent,
                    weight = FontWeight.Normal,
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                )
            } else {
                Row(Modifier.fillMaxWidth().height(46.dp)) {
                    DialogButton(
                        text = cancelText,
                        color = colors.accent,
                        weight = FontWeight.Normal,
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                    )
                    Box(Modifier.width(1.dp).height(46.dp).background(colors.separator))
                    DialogButton(
                        text = confirmText,
                        color = if (destructive) colors.danger else colors.accent,
                        weight = FontWeight.SemiBold,
                        onClick = onConfirm,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/** Longest label that still fits a half-width cell at a large font scale. */
private const val MAX_SIDE_BY_SIDE = 10

@Composable
private fun DialogButton(
    text: String,
    color: androidx.compose.ui.graphics.Color,
    weight: FontWeight,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val typography = LocalAppTypography.current
    Box(modifier.fillMaxWidth().clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        // Single line, shrinking before it would ever wrap or clip.
        FitText(
            text = text,
            style = typography.body.copy(fontWeight = weight),
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
    }
}
