package app.kite.parent.rules

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.kite.core.design.LocalAppColors
import app.kite.core.design.LocalAppTypography
import app.kite.core.design.components.AppIcon
import app.kite.core.design.components.KiteIcons
import app.kite.core.design.components.formatUsageMs
import app.kite.core.tasks.ChildTask

/** Shared bits of the rules sub-screens: header with back, stepper, weekday chips, formatting. */

/** Sub-screen header: «‹ Назад» on the left, an optional trailing action, then the large title. */
@Composable
internal fun SubScreenHeader(title: String, onBack: () -> Unit, trailing: (@Composable () -> Unit)? = null) {
    val colors = LocalAppColors.current
    val typography = LocalAppTypography.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onBack)
                .padding(top = 8.dp, bottom = 8.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppIcon(icon = KiteIcons.ChevronRight, tint = colors.accent, size = 22.dp, modifier = Modifier.rotate(180f))
            Text(text = "Назад", style = typography.body, color = colors.accent)
        }
        Spacer(Modifier.weight(1f))
        trailing?.invoke()
    }
    Spacer(Modifier.height(4.dp))
    Text(text = title, style = typography.title1, color = colors.textPrimary)
}

/** «− value +» row; the caller clamps. */
@Composable
internal fun StepperRow(label: String?, value: String, onMinus: () -> Unit, onPlus: () -> Unit, enabled: Boolean = true) {
    val colors = LocalAppColors.current
    val typography = LocalAppTypography.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        if (label != null) {
            Text(text = label, style = typography.body, color = if (enabled) colors.textPrimary else colors.textTertiary)
            Spacer(Modifier.weight(1f))
        }
        StepButton(text = "−", enabled = enabled, onClick = onMinus)
        Text(
            text = value,
            style = typography.headline,
            color = if (enabled) colors.textPrimary else colors.textTertiary,
            modifier = Modifier.width(96.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        StepButton(text = "+", enabled = enabled, onClick = onPlus)
        if (label == null) Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun StepButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    val typography = LocalAppTypography.current
    Box(
        Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(if (enabled) colors.accent.copy(alpha = 0.15f) else colors.fillQuaternary)
            .clickable(
                interactionSource = remember {
                    MutableInteractionSource()
                },
                indication = null,
                enabled = enabled,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = typography.title3.copy(fontWeight = FontWeight.SemiBold),
            color = if (enabled) colors.accent else colors.textTertiary,
        )
    }
}

/** Пн … Вс toggles; [selected] holds ISO weekdays 1..7. */
@Composable
internal fun DayChips(selected: Set<Int>, onToggle: (Int) -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalAppColors.current
    val typography = LocalAppTypography.current
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        (1..7).forEach { day ->
            val on = day in selected
            Box(
                Modifier
                    .weight(1f)
                    .height(36.dp)
                    .clip(CircleShape)
                    .background(if (on) colors.accent else colors.fillQuaternary)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onToggle(day) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = ChildTask.WEEKDAY_SHORT[day - 1],
                    style = typography.subhead.copy(fontWeight = FontWeight.SemiBold),
                    color = if (on) Color.White else colors.textPrimary,
                )
            }
        }
    }
}

internal val WEEKDAY_FULL = listOf("Понедельник", "Вторник", "Среда", "Четверг", "Пятница", "Суббота", "Воскресенье")

internal fun formatMinutesShort(minutes: Int): String = formatUsageMs(minutes * 60_000L)

internal fun formatClock(minutesOfDay: Int): String = "%d:%02d".format(minutesOfDay / 60, minutesOfDay % 60)

internal fun shiftClock(minutes: Int, delta: Int): Int = ((minutes + delta) % (24 * 60) + 24 * 60) % (24 * 60)

/** «Каждый день», «Пн–Пт», «Сб, Вс», «Пн, Ср, Пт». */
internal fun daysSummary(days: List<Int>): String {
    val sorted = days.filter { it in 1..7 }.distinct().sorted()
    return when {
        sorted.size == 7 -> "Каждый день"
        sorted == (1..5).toList() -> "Пн–Пт"
        sorted == listOf(6, 7) -> "Сб, Вс"
        sorted.isEmpty() -> "Дни не выбраны"
        else -> sorted.joinToString(", ") { ChildTask.WEEKDAY_SHORT[it - 1] }
    }
}
