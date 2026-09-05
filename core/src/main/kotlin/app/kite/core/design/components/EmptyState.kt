package app.kite.core.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.kite.core.design.LocalAppColors
import app.kite.core.design.LocalAppTypography

/**
 * Nothing here yet, said quietly: a soft glyph on the background instead of an empty card.
 * A card with «пусто» inside reads as a broken row; a mark on the page reads as calm.
 */
@Composable
fun EmptyState(icon: Int, text: String, modifier: Modifier = Modifier) {
    val colors = LocalAppColors.current
    val typography = LocalAppTypography.current
    Column(
        modifier.fillMaxWidth().padding(vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(72.dp).clip(CircleShape).background(colors.fillQuaternary),
            contentAlignment = Alignment.Center,
        ) {
            AppIcon(icon = icon, tint = colors.textTertiary, size = 34.dp)
        }
        Spacer(Modifier.height(14.dp))
        Text(
            text = text,
            style = typography.subhead,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp),
        )
    }
}
