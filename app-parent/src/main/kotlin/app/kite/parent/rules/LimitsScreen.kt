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
import androidx.compose.ui.unit.dp
import app.kite.core.design.LocalAppColors
import app.kite.core.design.LocalAppTypography
import app.kite.core.design.components.AppSpinner
import app.kite.core.design.components.AppSwitch
import app.kite.core.design.components.InsetGroup
import app.kite.core.design.components.InsetGroupedList

private const val STEP_MINUTES = 15
private const val MIN_LIMIT = 15
private const val MAX_LIMIT = 12 * 60
private const val DEFAULT_LIMIT = 120

/**
 * «Лимиты времени» (Kids360): seven weekday rows, each with a switch (limit on that day or
 * not) and a value; tapping the value opens an inline ±15-min stepper. «Все дни» at the top
 * sets the whole week at once. Changes apply immediately (RulesController debounces the upload).
 */
@Composable
fun LimitsScreen(controller: RulesController, onBack: () -> Unit) {
    val colors = LocalAppColors.current
    val typography = LocalAppTypography.current
    val rules = controller.rules
    var editingDay by remember { mutableStateOf<Int?>(null) } // 0..6, or -1 for «Все дни»

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.bgGrouped)
            .safeContentPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        SubScreenHeader(title = "Лимиты времени", onBack = onBack, trailing = {
            if (controller.saving) AppSpinner(color = colors.accent, size = 18.dp)
        })
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Сколько экрана в день. Считаются все приложения, кроме списка «Доступны всегда».",
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

        val limits = rules.weekdayLimitsForEditing()
        val distinct = limits.distinct()
        val common = if (distinct.size == 1) distinct.first() else null

        fun write(updated: List<Int?>) {
            controller.update {
                it.copy(
                    weekdayLimits = updated,
                    // Legacy single value for child builds that predate weekday limits: the
                    // most common configured value, or none.
                    dailyLimitMinutes = updated.filterNotNull().groupingBy { v -> v }.eachCount().maxByOrNull { e -> e.value }?.key,
                )
            }
        }

        InsetGroupedList {
            InsetGroup(header = "Все дни", footer = "Одно значение на всю неделю; ниже можно уточнить по дням.") {
                custom {
                    DayRow(
                        title = "Все дни одинаково",
                        limit = common,
                        mixed = common == null && distinct.size > 1,
                        expanded = editingDay == -1,
                        onToggle = { on -> write(List(7) { if (on) (limits.firstOrNull { v -> v != null } ?: DEFAULT_LIMIT) else null }) },
                        onTap = { editingDay = if (editingDay == -1) null else -1 },
                        onMinus = { write(List(7) { ((common ?: DEFAULT_LIMIT) - STEP_MINUTES).coerceAtLeast(MIN_LIMIT) }) },
                        onPlus = { write(List(7) { ((common ?: DEFAULT_LIMIT) + STEP_MINUTES).coerceAtMost(MAX_LIMIT) }) },
                    )
                }
            }
            InsetGroup(
                header = "По дням недели",
                footer = "Звонки, сообщения, контакты, камера, часы и настройки не ограничиваются никогда.",
            ) {
                WEEKDAY_FULL.forEachIndexed { index, name ->
                    custom {
                        val value = limits[index]
                        DayRow(
                            title = name,
                            limit = value,
                            mixed = false,
                            expanded = editingDay == index,
                            onToggle = { on ->
                                write(
                                    limits.toMutableList().also { l ->
                                        l[index] =
                                            if (on) (common ?: DEFAULT_LIMIT) else null
                                    },
                                )
                            },
                            onTap = { if (value != null) editingDay = if (editingDay == index) null else index },
                            onMinus = {
                                write(
                                    limits.toMutableList().also { l ->
                                        l[index] =
                                            ((value ?: DEFAULT_LIMIT) - STEP_MINUTES).coerceAtLeast(MIN_LIMIT)
                                    },
                                )
                            },
                            onPlus = {
                                write(
                                    limits.toMutableList().also { l ->
                                        l[index] =
                                            ((value ?: DEFAULT_LIMIT) + STEP_MINUTES).coerceAtMost(MAX_LIMIT)
                                    },
                                )
                            },
                        )
                    }
                }
            }
        }
        controller.error?.let {
            Spacer(Modifier.height(12.dp))
            Text(text = it, style = typography.footnote, color = colors.danger)
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun DayRow(
    title: String,
    limit: Int?,
    mixed: Boolean,
    expanded: Boolean,
    onToggle: (Boolean) -> Unit,
    onTap: () -> Unit,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
) {
    val colors = LocalAppColors.current
    val typography = LocalAppTypography.current
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onTap)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = title, style = typography.body, color = colors.textPrimary, modifier = Modifier.weight(1f))
            Text(
                text =
                when {
                    mixed -> "Разное"
                    limit != null -> formatMinutesShort(limit)
                    else -> "Без лимита"
                },
                style = typography.body,
                color = if (limit != null || mixed) colors.accent else colors.textSecondary,
            )
            Spacer(Modifier.width(12.dp))
            AppSwitch(checked = limit != null || mixed, onCheckedChange = onToggle)
        }
        if (expanded && (limit != null || mixed)) {
            StepperRow(label = null, value = limit?.let(::formatMinutesShort) ?: "—", onMinus = onMinus, onPlus = onPlus)
        }
    }
}
