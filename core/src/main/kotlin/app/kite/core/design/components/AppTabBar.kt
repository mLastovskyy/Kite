package app.kite.core.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import app.kite.core.design.LocalAppColors
import app.kite.core.design.LocalAppTypography

data class AppTab(val id: String, val label: String, val icon: Int, val badge: Boolean = false)

@Composable
fun AppTabBar(tabs: List<AppTab>, selectedId: String, onSelect: (String) -> Unit) {
    val colors = LocalAppColors.current
    val typography = LocalAppTypography.current
    Column(Modifier.fillMaxWidth().background(colors.bgBase)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.separator))
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(top = 6.dp, bottom = 8.dp),
        ) {
            tabs.forEach { tab ->
                val active = tab.id == selectedId
                val tint = if (active) colors.accent else colors.textTertiary
                Column(
                    Modifier
                        .weight(1f)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onSelect(tab.id) },
                        )
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box {
                        AppIcon(icon = tab.icon, tint = tint, size = 24.dp)
                        if (tab.badge) {
                            Box(
                                Modifier
                                    .align(Alignment.TopEnd)
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(colors.danger),
                            )
                        }
                    }
                    Spacer(Modifier.height(3.dp))
                    Text(text = tab.label, style = typography.caption, color = tint, maxLines = 1)
                }
            }
        }
    }
}
