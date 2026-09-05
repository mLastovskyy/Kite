package app.kite.parent.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.kite.core.design.LocalAppColors
import app.kite.core.design.LocalAppTypography
import app.kite.core.design.components.AvatarPreset
import app.kite.core.design.components.KiteAvatar
import app.kite.core.family.FamilyMember

/**
 * Which child the tab is about (Kids360 header: avatar + name). One child → a plain header
 * row; several → scrolling chips with avatars. The selection is shared across Главная,
 * Статистика and Задания (hoisted in MainTabs).
 */
@Composable
fun ChildSwitcher(
    children: List<FamilyMember>,
    selected: FamilyMember?,
    onSelect: (FamilyMember) -> Unit,
    modifier: Modifier = Modifier,
    badgeFor: (FamilyMember) -> Int = { 0 },
) {
    val colors = LocalAppColors.current
    val typography = LocalAppTypography.current
    if (children.size <= 1) {
        val child = selected ?: return
        Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            KiteAvatar(preset = AvatarPreset.byId(child.avatarKind), size = 36.dp, avatarUrl = child.avatarUrl)
            Spacer(Modifier.width(10.dp))
            Text(text = child.displayName.ifBlank { "Ребёнок" }, style = typography.headline, color = colors.textPrimary)
        }
        return
    }
    Row(
        modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        children.forEach { child ->
            val active = child.id == selected?.id
            Row(
                Modifier
                    .height(40.dp)
                    .clip(CircleShape)
                    .background(if (active) colors.accent else colors.bgBase)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onSelect(child) }
                    .padding(start = 4.dp, end = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                KiteAvatar(preset = AvatarPreset.byId(child.avatarKind), size = 32.dp, avatarUrl = child.avatarUrl)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = child.displayName.ifBlank { "Ребёнок" },
                    style = typography.subhead.copy(fontWeight = FontWeight.SemiBold),
                    color = if (active) Color.White else colors.textPrimary,
                )
                val badge = badgeFor(child)
                if (badge > 0) {
                    Spacer(Modifier.width(6.dp))
                    Box(
                        Modifier
                            .defaultMinSize(minWidth = 18.dp, minHeight = 18.dp)
                            .clip(CircleShape)
                            .background(if (active) Color.White else colors.danger)
                            .padding(horizontal = 5.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = badge.toString(),
                            style = typography.caption.copy(fontWeight = FontWeight.Bold),
                            color = if (active) colors.accent else Color.White,
                        )
                    }
                }
            }
        }
    }
}
