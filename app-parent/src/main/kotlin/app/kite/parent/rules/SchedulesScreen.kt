package app.kite.parent.rules

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.kite.core.apps.ChildAppsRemote
import app.kite.core.design.LocalAppColors
import app.kite.core.design.LocalAppTypography
import app.kite.core.design.components.AppButton
import app.kite.core.design.components.AppButtonStyle
import app.kite.core.design.components.AppIcon
import app.kite.core.design.components.AppSpinner
import app.kite.core.design.components.AppSwitch
import app.kite.core.design.components.AppTextField
import app.kite.core.design.components.ClockWheel
import app.kite.core.design.components.IconTile
import app.kite.core.design.components.InsetGroup
import app.kite.core.design.components.InsetGroupedList
import app.kite.core.design.components.KiteIcons
import app.kite.core.design.components.ScreenLoading
import app.kite.core.design.components.UsageAppItem
import app.kite.core.design.components.formatUsageMs
import app.kite.core.design.components.rowIcon
import app.kite.core.rules.ChildRules
import app.kite.core.rules.Essentials
import app.kite.core.rules.QuietInterval

/** Which schedule the editor is open on: an existing index, or a fresh one seeded from [initial]. */
private data class ScheduleEdit(val index: Int?, val initial: QuietInterval)

/**
 * «Расписание» (Kids360 «Блокировать по расписанию»): named cards — icon, name, time range,
 * days, how many apps, switch — plus «Добавить расписание». With nothing configured the two
 * presets «Сон» and «Учёба» are offered; they open the editor pre-filled, because a schedule
 * closes ONLY the apps the parent picks for it (owner, 04.09.2026) — never the whole phone.
 * Calls, messengers, camera and files are not even offered.
 */
@Composable
fun SchedulesScreen(
    controller: RulesController,
    apps: List<UsageAppItem>,
    childAppsRemote: ChildAppsRemote,
    memberId: String,
    onBack: () -> Unit,
) {
    val colors = LocalAppColors.current
    val typography = LocalAppTypography.current
    val rules = controller.rules
    val catalog = rememberAppCatalog(memberId, childAppsRemote)
    var editing by remember { mutableStateOf<ScheduleEdit?>(null) }
    BackHandler(enabled = editing != null) { editing = null }

    editing?.let { edit ->
        val current = rules ?: ChildRules()
        ScheduleEditor(
            initial = edit.initial,
            isNew = edit.index == null,
            entries = catalog.entries(apps, current),
            catalogLoading = catalog.loading,
            rules = current,
            memberId = memberId,
            onSave = { interval ->
                controller.update { r ->
                    val list = r.quietHours.toMutableList()
                    val index = edit.index
                    if (index == null || index !in list.indices) list += interval else list[index] = interval
                    r.copy(quietHours = list)
                }
                editing = null
            },
            onDelete = {
                edit.index?.let { index ->
                    controller.update { r -> r.copy(quietHours = r.quietHours.filterIndexed { i, _ -> i != index }) }
                }
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
        Spacer(Modifier.height(20.dp))

        if (rules == null) {
            ScreenLoading(caption = "Загружаем правила…", height = 160.dp)
            return@Column
        }

        InsetGroupedList {
            if (rules.quietHours.isNotEmpty()) {
                InsetGroup(footer = "Каждое расписание закрывает только выбранные приложения.") {
                    rules.quietHours.forEachIndexed { index, interval ->
                        custom(separatorInset = 57.dp) {
                            ScheduleRow(
                                interval = interval,
                                onClick = { editing = ScheduleEdit(index, interval) },
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
                            icon = rowIcon(KiteIcons.Moon, Color(0xFF5856D6)),
                            onClick = { editing = ScheduleEdit(null, QuietInterval.SLEEP) },
                        )
                    }
                    if (!hasStudy) {
                        row(
                            title = "Учёба · 08:00 – 16:00, Пн–Пт",
                            value = "Добавить",
                            icon = rowIcon(KiteIcons.BookOpen, Color(0xFF007AFF)),
                            onClick = { editing = ScheduleEdit(null, QuietInterval.STUDY) },
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        AppButton(
            text = "Добавить расписание",
            style = AppButtonStyle.Tinted,
            onClick = {
                val blank = QuietInterval(startMinutes = 20 * 60, endMinutes = 8 * 60, name = "", days = QuietInterval.ALL_DAYS)
                editing = ScheduleEdit(null, blank)
            },
        )
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
    val noApps = interval.packages.isEmpty()
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
            Text(
                text = if (noApps) "Приложения не выбраны" else pluralApps(interval.packages.size),
                style = typography.footnote,
                color = if (noApps) colors.warning else colors.accent,
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

/**
 * Name, two clock drums, weekday chips, and the apps this schedule closes. Save needs at
 * least one day and at least one app — a schedule with nothing to close is not saved.
 */
@Composable
private fun ScheduleEditor(
    initial: QuietInterval,
    isNew: Boolean,
    entries: List<AppEntry>,
    catalogLoading: Boolean,
    rules: ChildRules,
    memberId: String,
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
    var packages by remember { mutableStateOf(initial.packages.toSet()) }
    var picking by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    BackHandler(enabled = picking) { picking = false }

    if (picking) {
        ScheduleAppsPicker(
            entries = entries,
            loading = catalogLoading,
            rules = rules,
            memberId = memberId,
            selected = packages,
            onToggle = { pkg ->
                packages = if (pkg in packages) packages - pkg else packages + pkg
                error = null
            },
            onDone = { picking = false },
        )
        return
    }

    val byPackage = remember(entries) { entries.associateBy { it.packageName } }
    val chosen = packages.map { pkg -> byPackage[pkg] ?: AppEntry(pkg, fallbackLabel(pkg), 0L, false) }

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
                // Two drums side by side (iOS date picker), not ±15-minute steppers: setting
                // 21:00 → 07:00 took dozens of taps before.
                custom {
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = "Начало", style = typography.footnote, color = colors.textSecondary)
                            ClockWheel(minutesOfDay = start, onChange = { start = it })
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = "Конец", style = typography.footnote, color = colors.textSecondary)
                            ClockWheel(minutesOfDay = end, onChange = { end = it })
                        }
                    }
                }
            }
            InsetGroup(header = "Дни недели") {
                custom {
                    Box(Modifier.padding(horizontal = 12.dp, vertical = 12.dp)) {
                        DayChips(selected = days, onToggle = { d -> days = if (d in days) days - d else days + d })
                    }
                }
            }
            InsetGroup(
                header = "Приложения",
                footer = "Закрываются только выбранные. Звонки, мессенджеры, камера и файлы работают всегда.",
            ) {
                custom {
                    ChosenAppsRow(chosen = chosen, memberId = memberId, onClick = { picking = true })
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
                when {
                    days.isEmpty() -> error = "Выберите хотя бы один день"
                    packages.isEmpty() -> error = "Выберите хотя бы одно приложение"
                    else ->
                        onSave(
                            QuietInterval(
                                startMinutes = start,
                                endMinutes = end,
                                name = name.trim(),
                                days = days.sorted(),
                                enabled = initial.enabled,
                                packages = packages.sorted(),
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

/** «Какие закрывать › » with a strip of the chosen apps' icons, or «Выбрать» when none yet. */
@Composable
private fun ChosenAppsRow(chosen: List<AppEntry>, memberId: String, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    val typography = LocalAppTypography.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = "Какие закрывать", style = typography.body, color = colors.textPrimary, modifier = Modifier.weight(1f))
        if (chosen.isEmpty()) {
            Text(text = "Выбрать", style = typography.body, color = colors.accent)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                chosen.take(MAX_ICONS_IN_ROW).forEach { entry ->
                    InstalledAppIcon(memberId = memberId, packageName = entry.packageName, label = entry.label, size = 24.dp)
                }
                if (chosen.size > MAX_ICONS_IN_ROW) {
                    Text(text = "+${chosen.size - MAX_ICONS_IN_ROW}", style = typography.subhead, color = colors.textSecondary)
                }
            }
        }
        Spacer(Modifier.width(6.dp))
        AppIcon(icon = KiteIcons.ChevronRight, tint = colors.textTertiary, size = 18.dp)
    }
}

private const val MAX_ICONS_IN_ROW = 4

/**
 * Full-screen picker: every app on the child's phone with a checkmark. Essentials (calls,
 * messengers, camera, files) and the parent's «Доступно всегда» apps are shown but cannot
 * be picked — the child device would never close them anyway.
 */
@Composable
private fun ScheduleAppsPicker(
    entries: List<AppEntry>,
    loading: Boolean,
    rules: ChildRules,
    memberId: String,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    onDone: () -> Unit,
) {
    val colors = LocalAppColors.current
    val typography = LocalAppTypography.current
    var query by remember { mutableStateOf("") }
    var showAll by remember { mutableStateOf(false) }
    val used = entries.filter { it.used || it.packageName in selected }
    val unusedCount = entries.size - used.size
    val shown =
        (if (showAll) entries else used)
            .filter { query.isBlank() || it.label.contains(query.trim(), ignoreCase = true) }
            .sortedWith(compareByDescending<AppEntry> { it.todayMs }.thenBy { it.label.lowercase() })

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
        SubScreenHeader(title = "Приложения", onBack = onDone)
        Spacer(Modifier.height(12.dp))
        AppTextField(value = query, onValueChange = { query = it }, placeholder = "Поиск по названию")
        Spacer(Modifier.height(16.dp))

        when {
            entries.isEmpty() && loading -> ScreenLoading(caption = "Получаем список с телефона…", height = 160.dp)
            shown.isEmpty() ->
                Text(
                    text = if (entries.isEmpty()) "Список появится, когда телефон ребёнка выйдет в сеть." else "Ничего не найдено.",
                    style = typography.body,
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                )
            else ->
                InsetGroupedList {
                    InsetGroup(footer = "Звонки, мессенджеры, камера и файлы работают всегда.") {
                        shown.forEach { entry ->
                            custom(separatorInset = 60.dp) {
                                PickerRow(
                                    memberId = memberId,
                                    entry = entry,
                                    essentialTag = Essentials.essentialLabel(entry.packageName),
                                    alwaysAllowed = rules.appRules[entry.packageName]?.alwaysAllowed == true,
                                    checked = entry.packageName in selected,
                                    onToggle = { onToggle(entry.packageName) },
                                )
                            }
                        }
                        if (unusedCount > 0) {
                            row(
                                title = if (showAll) "Скрыть неиспользуемые" else "Показать все приложения",
                                value = if (showAll) null else "$unusedCount",
                                onClick = { showAll = !showAll },
                            )
                        }
                    }
                }
        }
        Spacer(Modifier.height(20.dp))
        AppButton(text = if (selected.isEmpty()) "Готово" else "Готово · ${selected.size}", onClick = onDone)
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun PickerRow(
    memberId: String,
    entry: AppEntry,
    essentialTag: String?,
    alwaysAllowed: Boolean,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    val colors = LocalAppColors.current
    val typography = LocalAppTypography.current
    val locked = essentialTag != null || alwaysAllowed
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = !locked,
                onClick = onToggle,
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        InstalledAppIcon(memberId = memberId, packageName = entry.packageName, label = entry.label, dimmed = locked)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = entry.label,
                style = typography.body,
                color = if (locked) colors.textSecondary else colors.textPrimary,
                maxLines = 1,
            )
            Text(
                text =
                when {
                    essentialTag != null -> "$essentialTag · всегда доступно"
                    alwaysAllowed -> "Доступно всегда"
                    entry.todayMs > 0 -> "Сегодня ${formatUsageMs(entry.todayMs)}"
                    else -> "Не открывалось"
                },
                style = typography.footnote,
                color = if (locked) colors.success else colors.textSecondary,
                maxLines = 1,
            )
        }
        Spacer(Modifier.width(8.dp))
        when {
            locked -> AppIcon(icon = KiteIcons.LockOpen, tint = colors.success, size = 20.dp)
            checked -> AppIcon(icon = KiteIcons.CircleCheck, tint = colors.accent, size = 24.dp)
            else -> Box(Modifier.width(24.dp).height(24.dp))
        }
    }
}
