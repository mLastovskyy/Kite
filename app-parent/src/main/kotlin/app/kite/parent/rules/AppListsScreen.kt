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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.kite.core.apps.ChildAppsRemote
import app.kite.core.design.LocalAppColors
import app.kite.core.design.LocalAppTypography
import app.kite.core.design.components.AppButton
import app.kite.core.design.components.AppIcon
import app.kite.core.design.components.AppSpinner
import app.kite.core.design.components.AppSwitch
import app.kite.core.design.components.AppTextField
import app.kite.core.design.components.DurationWheel
import app.kite.core.design.components.InsetGroup
import app.kite.core.design.components.InsetGroupedList
import app.kite.core.design.components.KiteIcons
import app.kite.core.design.components.ScreenLoading
import app.kite.core.design.components.UsageAppItem
import app.kite.core.design.components.UsagePeriodSwitch
import app.kite.core.design.components.formatUsageMs
import app.kite.core.rules.AppRule
import app.kite.core.rules.Essentials

/** The three Kids360 lists every app belongs to exactly one of (kept for the rule model). */
enum class AppListKind(val label: String, val shortLabel: String, val icon: Int, val color: Color) {
    Pool("Контроль времени", "С лимитом", KiteIcons.Hourglass, Color(0xFFFF9500)),
    Always("Доступны всегда", "Всегда", KiteIcons.LockOpen, Color(0xFF34C759)),
    Blocked("Всегда заблокированы", "Запрещены", KiteIcons.Ban, Color(0xFFFF3B30)),
}

fun AppRule?.kind(): AppListKind = when {
    this?.alwaysAllowed == true -> AppListKind.Always
    this?.blocked == true -> AppListKind.Blocked
    else -> AppListKind.Pool
}

private const val MIN_APP_LIMIT = 15
private const val MAX_APP_HOURS = 12
private const val DEFAULT_APP_LIMIT = 60

private enum class AppFilter(val label: String) { All("Все"), Limited("С лимитом"), Blocked("Запрещены") }

/**
 * «Приложения»: ONE list of everything installed on the child's phone (from `child_apps`,
 * merged with usage and existing rules), a switch per row — on = allowed, off = blocked —
 * and a sheet per app with «Доступно всегда» and its own daily limit on a drum. The owner's
 * ask: pick apps from the device, switch them on/off, limit each separately. Essentials
 * (calls, SMS, camera, clock, Settings) are never blocked by the child device.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppListsScreen(
    controller: RulesController,
    apps: List<UsageAppItem>,
    childAppsRemote: ChildAppsRemote,
    memberId: String,
    initialPackage: String? = null,
    onBack: () -> Unit,
) {
    val colors = LocalAppColors.current
    val typography = LocalAppTypography.current
    val rules = controller.rules
    var filter by remember { mutableStateOf(AppFilter.All) }
    var query by remember { mutableStateOf("") }
    var showAll by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<AppEntry?>(null) }
    val catalog = rememberAppCatalog(memberId, childAppsRemote)
    var focus by remember { mutableStateOf(initialPackage) }

    fun update(packageName: String, transform: (AppRule) -> AppRule) {
        controller.update { r ->
            val updated = transform(r.appRules[packageName] ?: AppRule())
            // An allowed app in the pool with no limit needs no rule row at all.
            r.copy(appRules = if (updated == AppRule()) r.appRules - packageName else r.appRules + (packageName to updated))
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
        SubScreenHeader(title = "Приложения", onBack = onBack, trailing = {
            if (controller.saving) AppSpinner(color = colors.accent, size = 18.dp)
        })
        Spacer(Modifier.height(16.dp))
        UsagePeriodSwitch(labels = AppFilter.entries.map { it.label }, selectedIndex = filter.ordinal, onSelect = {
            filter =
                AppFilter.entries[it]
        })
        Spacer(Modifier.height(12.dp))
        AppTextField(value = query, onValueChange = { query = it }, placeholder = "Поиск по названию")
        Spacer(Modifier.height(16.dp))

        if (rules == null) {
            ScreenLoading(caption = "Загружаем правила…", height = 160.dp)
            return@Column
        }

        val all = catalog.entries(apps, rules)
        // Arrived from Статистика with an app in hand: open its sheet once the list has it.
        LaunchedEffect(all.size, focus) {
            val pkg = focus ?: return@LaunchedEffect
            all.firstOrNull { it.packageName == pkg }?.let {
                selected = it
                focus = null
            }
        }
        val used = all.filter { it.used }
        val unusedCount = all.size - used.size
        val pool = if (showAll) all else used
        val shown =
            pool.filter { entry ->
                val rule = rules.appRules[entry.packageName]
                when (filter) {
                    AppFilter.All -> true
                    AppFilter.Limited -> rule?.dailyLimitMinutes != null
                    AppFilter.Blocked -> rule?.blocked == true
                }
            }
                .filter { query.isBlank() || it.label.contains(query.trim(), ignoreCase = true) }
                .sortedWith(compareByDescending<AppEntry> { it.todayMs }.thenBy { it.label.lowercase() })

        when {
            all.isEmpty() && catalog.loading -> ScreenLoading(
                caption = "Получаем список с телефона…",
                height = 160.dp,
            )
            shown.isEmpty() ->
                Text(
                    text =
                    when {
                        all.isEmpty() -> "Список появится, когда телефон ребёнка выйдет в сеть."
                        used.isEmpty() && !showAll -> "Ребёнок пока ничего не открывал."
                        filter == AppFilter.Limited -> "Лимитов пока нет."
                        filter == AppFilter.Blocked -> "Запрещённых нет."
                        else -> "Ничего не найдено."
                    },
                    style = typography.body,
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                )
            else -> {
                if (catalog.failed) {
                    Text(
                        text = "Показаны только открывавшиеся приложения.",
                        style = typography.footnote,
                        color = colors.textSecondary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                }
                InsetGroupedList {
                    InsetGroup(footer = "Звонки, мессенджеры, камера, файлы и часы работают всегда.") {
                        shown.forEach { entry ->
                            custom(separatorInset = 60.dp) {
                                AppRow(
                                    memberId = memberId,
                                    entry = entry,
                                    rule = rules.appRules[entry.packageName],
                                    onClick = { selected = entry },
                                    onAllowedChange = { allowed ->
                                        update(entry.packageName) {
                                            if (allowed) {
                                                it.copy(
                                                    blocked = false,
                                                )
                                            } else {
                                                it.copy(blocked = true, alwaysAllowed = false, dailyLimitMinutes = null)
                                            }
                                        }
                                    },
                                )
                            }
                        }
                        // Apps the child has never opened stay out of the way: the list is for
                        // what actually happens on the phone, not the full ROM inventory.
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
        }
        Spacer(Modifier.height(32.dp))
    }

    selected?.let { entry ->
        val rule = rules?.appRules?.get(entry.packageName)
        ModalBottomSheet(
            onDismissRequest = { selected = null },
            containerColor = colors.bgGrouped,
            dragHandle = null,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            AppSheet(
                entry = entry,
                rule = rule,
                onAllowed = { on ->
                    update(entry.packageName) {
                        if (on) it.copy(blocked = false) else it.copy(blocked = true, alwaysAllowed = false, dailyLimitMinutes = null)
                    }
                },
                onAlways = { on ->
                    update(entry.packageName) {
                        it.copy(alwaysAllowed = on, blocked = false, dailyLimitMinutes = if (on) null else it.dailyLimitMinutes)
                    }
                },
                onLimit = { minutes ->
                    update(entry.packageName) { it.copy(dailyLimitMinutes = minutes, blocked = false, alwaysAllowed = false) }
                },
                onDone = { selected = null },
            )
        }
    }
}

@Composable
private fun AppRow(memberId: String, entry: AppEntry, rule: AppRule?, onClick: () -> Unit, onAllowedChange: (Boolean) -> Unit) {
    val essentialTag = Essentials.essentialLabel(entry.packageName)
    val essential = essentialTag != null
    val colors = LocalAppColors.current
    val typography = LocalAppTypography.current
    val blocked = rule?.blocked == true
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember {
                    MutableInteractionSource()
                },
                indication = null,
                enabled = !essential,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        InstalledAppIcon(memberId = memberId, packageName = entry.packageName, label = entry.label, dimmed = blocked)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = entry.label,
                style = typography.body,
                color = if (blocked) colors.textSecondary else colors.textPrimary,
                maxLines = 1,
            )
            val limit = rule?.dailyLimitMinutes
            Text(
                text =
                when {
                    essential -> "$essentialTag · всегда доступно"
                    blocked -> "Запрещено"
                    rule?.alwaysAllowed == true -> "Доступно всегда"
                    else ->
                        buildString {
                            append(if (entry.todayMs > 0) "Сегодня ${formatUsageMs(entry.todayMs)}" else "Не открывалось")
                            if (limit != null) append(" · лимит ${formatMinutesShort(limit)}")
                        }
                },
                style = typography.footnote,
                color =
                when {
                    essential -> colors.success
                    blocked -> colors.danger
                    rule?.alwaysAllowed == true -> colors.success
                    limit != null -> colors.accent
                    else -> colors.textSecondary
                },
                maxLines = 1,
            )
        }
        Spacer(Modifier.width(8.dp))
        if (essential) {
            AppIcon(icon = KiteIcons.LockOpen, tint = colors.success, size = 20.dp)
        } else {
            AppSwitch(checked = !blocked, onCheckedChange = onAllowedChange)
        }
    }
}

@Composable
private fun AppSheet(
    entry: AppEntry,
    rule: AppRule?,
    onAllowed: (Boolean) -> Unit,
    onAlways: (Boolean) -> Unit,
    onLimit: (Int?) -> Unit,
    onDone: () -> Unit,
) {
    val colors = LocalAppColors.current
    val typography = LocalAppTypography.current
    val blocked = rule?.blocked == true
    val always = rule?.alwaysAllowed == true
    val limit = rule?.dailyLimitMinutes
    // «Готово» stays on screen: the settings above it scroll on their own once the day-limit
    // wheel is unfolded, instead of pushing the button below the fold.
    val maxContent = LocalConfiguration.current.screenHeightDp.dp * 0.62f
    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 16.dp)) {
        Column(Modifier.heightIn(max = maxContent).verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(16.dp))
            Text(text = entry.label, style = typography.title3, color = colors.textPrimary, modifier = Modifier.padding(horizontal = 32.dp))
            Text(
                text = entry.packageName,
                style = typography.caption,
                color = colors.textTertiary,
                modifier = Modifier.padding(horizontal = 32.dp),
                maxLines = 1,
            )
            Spacer(Modifier.height(16.dp))
            InsetGroupedList {
                InsetGroup {
                    custom { SwitchRow(title = "Разрешено", checked = !blocked, onChange = onAllowed) }
                }
                if (!blocked) {
                    InsetGroup(footer = "Не считается в лимите и не блокируется.") {
                        custom { SwitchRow(title = "Доступно всегда", checked = always, onChange = onAlways) }
                    }
                    if (!always) {
                        InsetGroup(header = "Лимит на день") {
                            custom {
                                SwitchRow(title = "Свой лимит", checked = limit != null, onChange = { on ->
                                    onLimit(if (on) DEFAULT_APP_LIMIT else null)
                                })
                            }
                            if (limit != null) {
                                custom {
                                    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                                        DurationWheel(
                                            totalMinutes = limit,
                                            onChange = { onLimit(it.coerceAtLeast(MIN_APP_LIMIT)) },
                                            maxHours = MAX_APP_HOURS,
                                            expand = true,
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Box(Modifier.padding(horizontal = 16.dp)) {
            AppButton(text = "Готово", onClick = onDone)
        }
    }
}

@Composable
private fun SwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val colors = LocalAppColors.current
    val typography = LocalAppTypography.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text = title, style = typography.body, color = colors.textPrimary, modifier = Modifier.weight(1f))
        AppSwitch(checked = checked, onCheckedChange = onChange)
    }
}
