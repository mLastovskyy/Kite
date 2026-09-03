package app.kite.core.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.kite.core.design.LocalAppColors
import app.kite.core.design.pressEffect

/**
 * Round icon button that floats over content (Apple Maps' controls): a white disc with a
 * soft shadow and a tinted glyph, no label — the text lives in the sheet it opens. [loading]
 * swaps the glyph for a spinner and ignores taps.
 */
@Composable
fun CircleIconButton(
    icon: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    tint: Color = LocalAppColors.current.accent,
    container: Color = LocalAppColors.current.bgBase,
    loading: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier
            .size(size)
            .pressEffect(interaction, !loading)
            .shadow(
                elevation = 6.dp,
                shape = CircleShape,
                ambientColor = Color.Black.copy(alpha = 0.18f),
                spotColor = Color.Black.copy(alpha = 0.18f),
            )
            .clip(CircleShape)
            .background(container)
            .clickable(interactionSource = interaction, indication = null, enabled = !loading, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            AppSpinner(color = tint, size = size * 0.45f)
        } else {
            AppIcon(icon = icon, tint = tint, size = size * 0.5f)
        }
    }
}
