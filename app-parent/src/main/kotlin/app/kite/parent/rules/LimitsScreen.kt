package app.kite.parent.rules

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.kite.core.design.LocalAppColors
import app.kite.core.design.LocalAppTypography
import app.kite.core.design.components.AppButton
import app.kite.core.design.components.AppSpinner
import app.kite.core.design.components.AppSwitch
import app.kite.core.design.components.DurationWheel
import app.kite.core.design.components.InsetGroup
import app.kite.core.design.components.InsetGroupedList
import app.kite.core.design.components.RollingText
import app.kite.core.design.components.ScreenLoading
import app.kite.core.design.components.UsagePeriodSwitch

private const val MIN_LIMIT = 15
private const val MAX_HOURS = 12
private const val DEFAULT_LIMIT = 120

/**
 * «Лимит на день» (Kids360 «Лимиты времени»): seven weekday rows, each with the day's limit
 * and a switch. Tapping a row opens a sheet with an hours/minutes drum and «Для всех дней»;
 * nothing else to learn. Changes apply immediately (RulesController debounces the upload).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LimitsScreen(controller: RulesController, onBack: () -> Unit) {
    val colors = LocalAppColors.current
    val typography = LocalAppTypography.current
    val rules = controller.rules
    var editingDay by remember { mutableStateOf<Int?>(null) } // 0 = Monday … 6 = Sunday

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

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.bgGrouped)
            .safeContentPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        SubScreenHeader(title = "Лимит на день", onBack = onBack, trailing = {
            if (controller.saving) AppSpinner(color = colors.accent, size = 18.dp)
        })
        Spacer(Modifier.height(20.dp))

        if (rules == null) {
            ScreenLoading(caption = "Загружаем правила…", height = 160.dp)
            return@Column
        }

        val limits = rules.weekdayLimitsForEditing()

        InsetGroupedList {
            InsetGroup(header = "Дни недели") {
                WEEKDAY_FULL.forEachIndexed { index, name ->
                    val value = limits[index]
                    row(
                        title = name,
                        value = value?.let(::formatMinutesShort) ?: "Без лимита",
                        showChevron = true,
                        onClick = { editingDay = index },
                    )
                }
            }
        }
        controller.error?.let {
            Spacer(Modifier.height(12.dp))
            Text(text = it, style = typography.footnote, color = colors.danger)
        }
        Spacer(Modifier.height(32.dp))
    }

    editingDay?.let { index ->
        LimitSheet(
            dayName = WEEKDAY_FULL[index],
            initialMinutes = rules?.weekdayLimitsForEditing()?.get(index),
            onApply = { minutes, allDays ->
                val current = rules?.weekdayLimitsForEditing() ?: List(7) { null }
                write(if (allDays) List(7) { minutes } else current.toMutableList().also { it[index] = minutes })
                editingDay = null
            },
            onDismiss = { editingDay = null },
        )
    }
}

/** Drum picker for one day's limit, with «Для всех дней». Minimum is [MIN_LIMIT] minutes. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LimitSheet(dayName: String, initialMinutes: Int?, onApply: (minutes: Int?, allDays: Boolean) -> Unit, onDismiss: () -> Unit) {
    val colors = LocalAppColors.current
    val typography = LocalAppTypography.current
    var minutes by remember { mutableIntStateOf(initialMinutes ?: DEFAULT_LIMIT) }
    var unlimited by remember { mutableStateOf(initialMinutes == null) }
    var allDays by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = colors.bgGrouped, dragHandle = null) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = dayName, style = typography.title3, color = colors.textPrimary)
            Spacer(Modifier.height(2.dp))
            Spacer(Modifier.height(10.dp))
            // «Без лимита» is a choice here, not a switch on every row (owner, 04.09.2026).
            UsagePeriodSwitch(labels = listOf("Лимит", "Без лимита"), selectedIndex = if (unlimited) 1 else 0, onSelect = {
                unlimited =
                    it == 1
            })
            Spacer(Modifier.height(10.dp))
            if (unlimited) {
                Text(
                    text = "В этот день время не ограничено.",
                    style = typography.subhead,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            } else {
                RollingText(
                    text = if (minutes < MIN_LIMIT) "Минимум ${formatMinutesShort(MIN_LIMIT)}" else formatMinutesShort(minutes),
                    style = typography.subhead,
                    color = colors.textSecondary,
                )
                Spacer(Modifier.height(8.dp))
                DurationWheel(totalMinutes = minutes, onChange = { minutes = it }, maxHours = MAX_HOURS)
            }
            Spacer(Modifier.height(8.dp))
            InsetGroupedList {
                InsetGroup {
                    custom {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Для всех дней",
                                style = typography.body,
                                color = colors.textPrimary,
                                modifier = Modifier.weight(1f),
                            )
                            AppSwitch(checked = allDays, onCheckedChange = { allDays = it })
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            AppButton(text = "Готово", onClick = { onApply(if (unlimited) null else minutes.coerceAtLeast(MIN_LIMIT), allDays) })
        }
    }
}
