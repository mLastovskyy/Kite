package app.kite.parent.rules

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.kite.core.design.LocalAppColors
import app.kite.core.design.LocalAppTypography
import app.kite.core.design.components.AppButton
import app.kite.core.design.components.AppButtonStyle
import app.kite.core.design.components.AppSpinner
import app.kite.core.design.components.AppSwitch
import app.kite.core.design.components.AppTextField
import app.kite.core.design.components.IconTile
import app.kite.core.design.components.InsetGroup
import app.kite.core.design.components.InsetGroupedList
import app.kite.core.design.components.KiteIcons
import app.kite.core.rules.QuietInterval

private const val STEP_MINUTES = 15

/**
 * «Расписание» (Kids360 «Блокировать по расписанию»): named cards — icon, name, time range,
 * days, switch — plus «Добавить расписание». With nothing configured the two presets «Сон»
 * and «Учёба» are offered as one-tap suggestions. Tapping a card opens the editor.
 */
@Composable
fun SchedulesScreen(controller: RulesController, onBack: () -> Unit) {
    val colors = LocalAppColors.current
    val typography = LocalAppTypography.current
    val rules = controller.rules
    var editing by remember { mutableStateOf<Int?>(null) } // index into quietHours, or -1 for a new one

    editing?.let { index ->
        val existing = rules?.quietHours?.getOrNull(index)
        ScheduleEditor(
            initial = existing ?: QuietInterval(startMinutes = 20 * 60, endMinutes = 8 * 60, name = "", days = QuietInterval.ALL_DAYS),
            isNew = existing == null,
            onSave = { interval ->
                controller.update { r ->
                    val list = r.quietHours.toMutableList()
                    if (existing == null) list += interval else list[index] = interval
                    r.copy(quietHours = list)
                }
                editing = null
            },
            onDelete = {
                controller.update { r -> r.copy(quietHours = r.quietHours.filterIndexed { i, _ -> i != index }) }
                editing = null
            },
            onCancel = { editing = null },
        )
        return
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.bgGrouped)
            .safeContentPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        SubScreenHeader(title = "Расписание", onBack = onBack, trailing = {
            if (controller.saving) AppSpinner(color = colors.accent, size = 18.dp)
        })
        Spacer(Modifier.height(6.dp))
        Text(
            text = "В это время приложения из «Контроля времени» заблокированы. «Доступны всегда» работают.",
            style = typography.subhead,
            color = colors.textSecondary,
        )
        Spacer(Modifier.height(20.dp))

        if (rules == null) {
            Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                AppSpinner(color = colors.accent, size = 28.dp)
            }
            return@Column
        }

        InsetGroupedList {
            if (rules.quietHours.isNotEmpty()) {
                InsetGroup {
                    rules.quietHours.forEachIndexed { index, interval ->
                        custom(separatorInset = 57.dp) {
                            ScheduleRow(
                                interval = interval,
                                onClick = { editing = index },
                                onToggle = { on ->
                                    controller.update { r ->
                                        r.copy(quietHours = r.quietHours.mapIndexed { i, q -> if (i == index) q.copy(enabled = on) else q })
                                    }
                                },
                            )
                        }
                    }
                }
            }
            val hasSleep = rules.quietHours.any { it.name == QuietInterval.SLEEP.name }
            val hasStudy = rules.quietHours.any { it.name == QuietInterval.STUDY.name }
            if (!hasSleep || !hasStudy) {
                InsetGroup(header = "Предложения") {
                    if (!hasSleep) {
                        row(
                            title = "Сон · 21:00 – 07:00",
                            value = "Добавить",
                            icon = app.kite.core.design.components.rowIcon(KiteIcons.Moon, Color(0xFF5856D6)),
                            onClick = { controller.update { r -> r.copy(quietHours = r.quietHours + QuietInterval.SLEEP) } },
                        )
                    }
                    if (!hasStudy) {
                        row(
                            title = "Учёба · 08:00 – 16:00, Пн–Пт",
                            value = "Добавить",
                            icon = app.kite.core.design.components.rowIcon(KiteIcons.BookOpen, Color(0xFF007AFF)),
                            onClick = { controller.update { r -> r.copy(quietHours = r.quietHours + QuietInterval.STUDY) } },
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        AppButton(text = "Добавить расписание", style = AppButtonStyle.Tinted, onClick = { editing = -1 })
        controller.error?.let {
            Spacer(Modifier.height(12.dp))
            Text(text = it, style = typography.footnote, color = colors.danger)
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun ScheduleRow(interval: QuietInterval, onClick: () -> Unit, onToggle: (Boolean) -> Unit) {
    val colors = LocalAppColors.current
    val typography = LocalAppTypography.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(icon = scheduleIcon(interval), background = scheduleColor(interval))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = interval.name.ifBlank { "Без названия" },
                style = typography.body,
                color = if (interval.enabled) colors.textPrimary else colors.textSecondary,
            )
            Text(
                text = "${formatClock(interval.startMinutes)} – ${formatClock(interval.endMinutes)} · ${daysSummary(interval.days)}",
                style = typography.footnote,
                color = colors.textSecondary,
            )
        }
        Spacer(Modifier.width(12.dp))
        AppSwitch(checked = interval.enabled, onCheckedChange = onToggle)
    }
}

internal fun scheduleIcon(interval: QuietInterval): Int = when {
    interval.name.equals(QuietInterval.SLEEP.name, ignoreCase = true) -> KiteIcons.Moon
    interval.name.equals(QuietInterval.STUDY.name, ignoreCase = true) -> KiteIcons.BookOpen
    else -> KiteIcons.CalendarClock
}

internal fun scheduleColor(interval: QuietInterval): Color = when {
    interval.name.equals(QuietInterval.SLEEP.name, ignoreCase = true) -> Color(0xFF5856D6)
    interval.name.equals(QuietInterval.STUDY.name, ignoreCase = true) -> Color(0xFF007AFF)
    else -> Color(0xFF30B0C7)
}

/** Name, start/end steppers (15-min steps), weekday chips. Save requires at least one day. */
@Composable
private fun ScheduleEditor(
    initial: QuietInterval,
    isNew: Boolean,
    onSave: (QuietInterval) -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
) {
    val colors = LocalAppColors.current
    val typography = LocalAppTypography.current
    var name by remember { mutableStateOf(initial.name) }
    var start by remember { mutableStateOf(initial.startMinutes) }
    var end by remember { mutableStateOf(initial.endMinutes) }
    var days by remember { mutableStateOf(initial.days.toSet()) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.bgGrouped)
            .safeContentPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        SubScreenHeader(title = if (isNew) "Новое расписание" else "Расписание", onBack = onCancel)
        Spacer(Modifier.height(20.dp))

        InsetGroupedList {
            InsetGroup(header = "Название") {
                custom {
                    Box(Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                        AppTextField(value = name, onValueChange = { name = it.take(30) }, placeholder = "Например: тренировка или секция")
                    }
                }
            }
            InsetGroup(
                header = "Время",
                footer = if (start >= end) "Заканчивается на следующий день." else null,
            ) {
                custom {
                    StepperRow(label = "Начало", value = formatClock(start), onMinus = {
                        start = shiftClock(start, -STEP_MINUTES)
                    }, onPlus = {
                        start =
                            shiftClock(start, STEP_MINUTES)
                    })
                }
                custom {
                    StepperRow(label = "Конец", value = formatClock(end), onMinus = { end = shiftClock(end, -STEP_MINUTES) }, onPlus = {
                        end =
                            shiftClock(end, STEP_MINUTES)
                    })
                }
            }
            InsetGroup(header = "Дни недели") {
                custom {
                    Box(Modifier.padding(horizontal = 12.dp, vertical = 12.dp)) {
                        DayChips(selected = days, onToggle = { d -> days = if (d in days) days - d else days + d })
                    }
                }
            }
        }
        if (error != null) {
            Spacer(Modifier.height(12.dp))
            Text(text = error!!, style = typography.subhead, color = colors.danger)
        }
        Spacer(Modifier.height(24.dp))
        AppButton(
            text = "Сохранить",
            onClick = {
                if (days.isEmpty()) {
                    error = "Выберите хотя бы один день"
                } else {
                    onSave(
                        QuietInterval(
                            startMinutes = start,
                            endMinutes = end,
                            name = name.trim(),
                            days = days.sorted(),
                            enabled = initial.enabled,
                        ),
                    )
                }
            },
        )
        if (!isNew) {
            Spacer(Modifier.height(8.dp))
            AppButton(text = "Удалить расписание", style = AppButtonStyle.Plain, onClick = onDelete)
        }
        Spacer(Modifier.height(32.dp))
    }
}
