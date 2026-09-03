package app.kite.child.transparency

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.kite.core.design.LocalAppColors
import app.kite.core.design.LocalAppTypography

private data class VisibilityItem(val text: String, val visible: Boolean)

/**
 * «Что видит родитель» — honest list with check/cross marks. Required by Play policy and
 * the best defence against the child looking for bypasses (CLAUDE.md). No hidden mode ever.
 */
@Composable
fun TransparencyScreen() {
    val colors = LocalAppColors.current
    val typography = LocalAppTypography.current

    val visible =
        listOf(
            VisibilityItem("Какие приложения установлены на телефоне", true),
            VisibilityItem("Сколько времени в каждом приложении", true),
            VisibilityItem("Когда действуют лимиты и тихие часы", true),
            VisibilityItem("Где находится телефон на карте", true),
            VisibilityItem("Места: когда ты приходишь и уходишь из мест, которые сохранил родитель", true),
            VisibilityItem("Маршрут за день — где телефон был в течение дня", true),
            VisibilityItem("Заряд батареи телефона", true),
            VisibilityItem("Запросы на дополнительное время и снятие блокировки", true),
        )
    val hidden =
        listOf(
            VisibilityItem("Содержимое экрана и скриншоты", false),
            VisibilityItem("Переписки и сообщения", false),
            VisibilityItem("Звонки и их запись", false),
            VisibilityItem("Микрофон и окружающий звук", false),
            VisibilityItem("Пароли и набранный текст", false),
        )

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.bgGrouped)
            .safeContentPadding()
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Spacer(Modifier.height(12.dp))
        Text(text = "Что видит родитель", style = typography.largeTitle, color = colors.textPrimary)
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Честный список. Kite Jr работает открыто — скрытого режима нет.",
            style = typography.subhead,
            color = colors.textSecondary,
        )
        Spacer(Modifier.height(20.dp))

        SectionCard(header = "Видно родителю", items = visible)
        Spacer(Modifier.height(24.dp))
        SectionCard(header = "Никогда не видно", items = hidden)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SectionCard(header: String, items: List<VisibilityItem>) {
    val colors = LocalAppColors.current
    val typography = LocalAppTypography.current
    Text(
        text = header,
        style = typography.footnote,
        color = colors.textSecondary,
        modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
    )
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(colors.bgBase),
    ) {
        items.forEachIndexed { index, item ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Mark(item.visible)
                Text(text = item.text, style = typography.body, color = colors.textPrimary)
            }
            if (index < items.lastIndex) {
                Box(
                    Modifier
                        .padding(start = 44.dp)
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(colors.separator),
                )
            }
        }
    }
}

@Composable
private fun Mark(visible: Boolean) {
    val colors = LocalAppColors.current
    Box(
        Modifier
            .size(20.dp)
            .clip(CircleShape)
            .background(if (visible) colors.success else colors.danger),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (visible) "✓" else "✕",
            style = LocalAppTypography.current.caption,
            color = Color.White,
        )
    }
}
