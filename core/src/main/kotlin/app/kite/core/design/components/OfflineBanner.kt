package app.kite.core.design.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import app.kite.core.design.LocalAppColors
import app.kite.core.design.LocalAppTypography

@Composable
fun OfflineBanner(visible: Boolean, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalAppColors.current
    val typography = LocalAppTypography.current
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
        modifier = modifier,
    ) {
        key(visible) {
            val state = rememberSwipeToDismissBoxState()
            LaunchedEffect(state.currentValue) {
                if (state.currentValue != SwipeToDismissBoxValue.Settled) onDismiss()
            }
            SwipeToDismissBox(
                state = state,
                backgroundContent = { Box(Modifier.fillMaxSize().background(colors.bgBase)) },
            ) {
                Column(Modifier.fillMaxWidth().background(colors.bgBase)) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(start = 16.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AppIcon(icon = KiteIcons.WifiOff, tint = colors.textSecondary, size = 22.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(text = "Нет подключения к интернету", style = typography.subhead, color = colors.textPrimary)
                            Text(text = "Часть функций недоступна", style = typography.caption, color = colors.textSecondary)
                        }
                        Box(
                            Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = onDismiss,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            AppIcon(icon = KiteIcons.X, tint = colors.textSecondary, size = 18.dp)
                        }
                    }
                    Box(Modifier.fillMaxWidth().height(0.5.dp).background(colors.separator))
                }
            }
        }
    }
}
