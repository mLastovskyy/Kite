package app.kite.core.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.kite.core.design.LocalAppColors
import app.kite.core.design.LocalAppTypography
import app.kite.core.design.pressEffect

enum class AppButtonStyle { Filled, Tinted, Plain, Destructive }

/**
 * iOS-like button (DESIGN_SYSTEM.md): 50dp height, 12dp radius, headline weight,
 * scale-down press effect, no ripple. Filled/Tinted/Destructive are full width;
 * Plain wraps its content.
 */
@Composable
fun AppButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: AppButtonStyle = AppButtonStyle.Filled,
    enabled: Boolean = true,
) {
    val colors = LocalAppColors.current
    val typography = LocalAppTypography.current
    val (container, contentColor) =
        when (style) {
            AppButtonStyle.Filled -> colors.accent to Color.White
            AppButtonStyle.Tinted -> colors.accent.copy(alpha = 0.15f) to colors.accent
            AppButtonStyle.Plain -> Color.Transparent to colors.accent
            AppButtonStyle.Destructive -> colors.danger to Color.White
        }
    val interaction = remember { MutableInteractionSource() }
    val widthModifier = if (style == AppButtonStyle.Plain) Modifier else Modifier.fillMaxWidth()
    Box(
        modifier
            .then(widthModifier)
            .height(50.dp)
            .pressEffect(interaction, enabled)
            .clip(RoundedCornerShape(12.dp))
            .background(if (enabled) container else container.copy(alpha = container.alpha * 0.4f))
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = typography.headline,
            color = if (enabled) contentColor else contentColor.copy(alpha = 0.4f),
            maxLines = 1,
        )
    }
}
