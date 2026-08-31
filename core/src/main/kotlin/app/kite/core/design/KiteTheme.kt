package app.kite.core.design

import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.node.DelegatableNode

/** Standard motion from DESIGN_SYSTEM.md: spring, dampingRatio 0.85, stiffness 380. */
fun <T> standardSpring(): SpringSpec<T> = spring(dampingRatio = 0.85f, stiffness = 380f)

/**
 * No ripple anywhere (DESIGN_SYSTEM.md). Interactive components use [pressEffect] instead.
 * Provided as the default indication so stray `clickable` calls stay ripple-free too.
 */
object NoIndication : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = object : Modifier.Node() {}

    override fun equals(other: Any?): Boolean = other === this

    override fun hashCode(): Int = -1
}

/** Press feedback: scale to 0.97 and opacity to 0.9, springing back on release. */
@Composable
fun Modifier.pressEffect(interactionSource: InteractionSource, enabled: Boolean = true): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val active = pressed && enabled
    val scale by animateFloatAsState(if (active) 0.97f else 1f, standardSpring(), label = "pressScale")
    val alpha by animateFloatAsState(if (active) 0.9f else 1f, standardSpring(), label = "pressAlpha")
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
        this.alpha = alpha
    }
}

/**
 * Root theme. Material3 is a base only: the color scheme below keeps M3 internals (content
 * colors, dialogs, text selection) coherent, but feature code must use [LocalAppColors] and
 * [LocalAppTypography], never MaterialTheme directly.
 */
@Composable
fun KiteTheme(accents: AccentColors = AccentColors.Parent, darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = if (darkTheme) darkAppColors(accents) else lightAppColors(accents)
    val typography = kiteTypography()
    val scheme =
        if (darkTheme) {
            darkColorScheme(
                primary = colors.accent,
                onPrimary = Color.White,
                background = colors.bgGrouped,
                onBackground = colors.textPrimary,
                surface = colors.bgBase,
                onSurface = colors.textPrimary,
                error = colors.danger,
            )
        } else {
            lightColorScheme(
                primary = colors.accent,
                onPrimary = Color.White,
                background = colors.bgGrouped,
                onBackground = colors.textPrimary,
                surface = colors.bgBase,
                onSurface = colors.textPrimary,
                error = colors.danger,
            )
        }
    MaterialTheme(colorScheme = scheme) {
        // Inside MaterialTheme on purpose: M3 provides its ripple via LocalIndication,
        // and this override must win.
        CompositionLocalProvider(
            LocalAppColors provides colors,
            LocalAppTypography provides typography,
            LocalIndication provides NoIndication,
        ) {
            ProvideTextStyle(typography.body, content)
        }
    }
}
