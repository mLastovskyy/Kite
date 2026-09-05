package app.kite.child.status

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.kite.child.enforce.RulesStore
import app.kite.child.identity.ParentsStore
import app.kite.core.design.LocalAppColors
import app.kite.core.design.LocalAppTypography
import app.kite.core.design.components.AvatarPreset
import app.kite.core.design.components.BackHeader
import app.kite.core.design.components.InsetGroup
import app.kite.core.design.components.InsetGroupedList
import app.kite.core.design.components.KiteAvatar
import app.kite.core.design.components.RowIcon
import app.kite.core.rules.QuietInterval
import app.kite.core.tasks.ChildTask

/**
 * «Мои правила» on the child device: the same rules the parent set, in the child's words, and
 * the parent who set them. Nothing here is a surprise — the child can read every limit and
 * schedule that applies to this phone (CLAUDE.md: no hidden mode, ever).
 */
@Composable
fun ChildRulesScreen(rulesStore: RulesStore, parentsStore: ParentsStore, onBack: () -> Unit) {
    val colors = LocalAppColors.current
    val typography = LocalAppTypography.current
    val rules = remember { rulesStore.rules() }
    val author = remember { rulesStore.author()?.let { id -> parentsStore.parents().firstOrNull { it.userId == id } } }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.bgGrouped)
            .safeContentPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        BackHeader(title = "Мои правила", onBack = onBack)
        Spacer(Modifier.height(20.dp))

        val blocked = rules.appRules.count { it.value.blocked }
        val limited = rules.appRules.count { it.value.dailyLimitMinutes != null }
        val always = rules.appRules.count { it.value.alwaysAllowed }
        val schedules = rules.quietHours.filter { it.enabled }
        val empty = schedules.isEmpty() && blocked == 0 && limited == 0 && (1..7).all { rules.limitFor(it) == null }

        if (empty) {
            Text(
                text = "Ограничений пока нет.",
                style = typography.body,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
            )
            return@Column
        }

        InsetGroupedList {
            if (author != null) {
                InsetGroup {
                    row(
                        title = author.name,
                        value = "Настроил(а) правила",
                        icon = RowIcon(background = Color.Transparent) {
                            KiteAvatar(preset = AvatarPreset.byId(author.avatarKind), size = 29.dp, avatarUrl = author.avatarUrl)
                        },
                    )
                }
            }
            InsetGroup(header = "Время") {
                (1..7).forEach { day ->
                    val limit = rules.limitFor(day) ?: return@forEach
                    row(title = dayName(day), value = formatMinutes(limit))
                }
                if ((1..7).all { rules.limitFor(it) == null }) row(title = "Лимита на день нет")
            }

            if (schedules.isNotEmpty()) {
                InsetGroup(header = "Расписание") {
                    schedules.forEach { interval ->
                        row(
                            title = interval.name.ifBlank { "Расписание" },
                            value = "${clock(interval.startMinutes)}–${clock(interval.endMinutes)}",
                            subtitle = scheduleSubtitle(interval),
                        )
                    }
                }
            }

            InsetGroup(header = "Приложения", footer = "Звонки, сообщения, камера и файлы работают всегда.") {
                if (blocked > 0) row(title = "Закрыты совсем", value = "$blocked")
                if (limited > 0) row(title = "Со своим лимитом", value = "$limited")
                if (always > 0) row(title = "Доступны всегда", value = "$always")
                if (blocked == 0 && limited == 0 && always == 0) row(title = "Отдельных правил нет")
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}

private fun scheduleSubtitle(interval: QuietInterval): String {
    val days = daysSummary(interval.days)
    val apps = interval.packages.size
    return if (apps == 0) days else "$days · приложений: $apps"
}

private fun daysSummary(days: List<Int>): String = when {
    days.isEmpty() || days.size == 7 -> "Каждый день"
    days.sorted() == listOf(1, 2, 3, 4, 5) -> "Будни"
    days.sorted() == listOf(6, 7) -> "Выходные"
    else -> days.sorted().joinToString(", ") { ChildTask.WEEKDAY_SHORT.getOrElse(it - 1) { "" } }
}

private fun dayName(isoDay: Int): String = DAY_NAMES.getOrElse(isoDay - 1) { "" }

private val DAY_NAMES = listOf("Понедельник", "Вторник", "Среда", "Четверг", "Пятница", "Суббота", "Воскресенье")

private fun clock(minuteOfDay: Int): String = "%02d:%02d".format(minuteOfDay / 60, minuteOfDay % 60)

private fun formatMinutes(minutes: Int): String = when {
    minutes < 60 -> "$minutes мин"
    minutes % 60 == 0 -> "${minutes / 60} ч"
    else -> "${minutes / 60} ч ${minutes % 60} мин"
}
