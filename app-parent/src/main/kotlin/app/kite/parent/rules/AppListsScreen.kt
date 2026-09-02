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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.kite.core.design.LocalAppColors
import app.kite.core.design.LocalAppTypography
import app.kite.core.design.components.AppIcon
import app.kite.core.design.components.AppSpinner
import app.kite.core.design.components.AppSwitch
import app.kite.core.design.components.AppTextField
import app.kite.core.design.components.InsetGroup
import app.kite.core.design.components.InsetGroupedList
import app.kite.core.design.components.KiteIcons
import app.kite.core.design.components.UsageAppItem
import app.kite.core.design.components.UsagePeriodSwitch
import app.kite.core.design.components.formatUsageMs
import app.kite.core.design.components.rowIcon
import app.kite.core.rules.AppRule
import app.kite.core.rules.ChildRules

/** The three Kids360 lists every app belongs to exactly one of. */
enum class AppListKind(val label: String, val icon: Int, val color: Color) {
    Pool("Контроль времени", KiteIcons.Hourglass, Color(0xFFFF9500)),
    Always("Доступны всегда", KiteIcons.LockOpen, Color(0xFF34C759)),
    Blocked("Всегда заблокированы", KiteIcons.Ban, Color(0xFFFF3B30)),
}

fun AppRule?.kind(): AppListKind = when {
    this?.alwaysAllowed == true -> AppListKind.Always
    this?.blocked == true -> AppListKind.Blocked
    else -> AppListKind.Pool
}

private const val STEP_MINUTES = 15
private const val MIN_APP_LIMIT = 15
private const val MAX_APP_LIMIT = 12 * 60
private const val DEFAULT_APP_LIMIT = 60

/**
 * «Приложения»: segmented control over the three lists, search, one row per app the child
 * has used (plus any app that already has a rule). Tapping a row opens the «Переместить в:»
 * sheet; in the pool the same sheet sets or removes a per-app limit («Лимит на приложение»).
 * [apps] carry today's usage for the subtitle. Essentials (calls, SMS, camera, clock,
 * Settings) are never blocked by the child device and are not listed here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppListsScreen(
    controller: RulesController,
    apps: List<UsageAppItem>,
    initialKind: AppListKind = AppListKind.Pool,
    onBack: () -> Unit,
) {
    val colors = LocalAppColors.current
    val typography = LocalAppTypography.current
    val rules = controller.rules
    var kind by remember { mutableStateOf(initialKind) }
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<UsageAppItem?>(null) }

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
        UsagePeriodSwitch(
            labels = AppListKind.entries.map { it.label },
            selectedIndex = kind.ordinal,
            onSelect = { kind = AppListKind.entries[it] },
        )
        Spacer(Modifier.height(12.dp))
        AppTextField(value = query, onValueChange = { query = it }, placeholder = "Поиск по названию")
        Spacer(Modifier.height(16.dp))

        if (rules == null) {
            Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                AppSpinner(color = colors.accent, size = 28.dp)
            }
            return@Column
        }

        val all = allApps(apps, rules)
        val shown =
            all.filter { rules.appRules[it.packageName].kind() == kind }
                .filter { query.isBlank() || it.label.contains(query.trim(), ignoreCase = true) }
                .sortedWith(compareByDescending<UsageAppItem> { it.totalMs }.thenBy { it.label.lowercase() })

        Text(
            text =
            when (kind) {
                AppListKind.Pool -> "Считаются в дневном лимите и блокируются по расписанию."
                AppListKind.Always -> "Работают всегда, даже когда время вышло. Звонки, сообщения, камера, часы и настройки — всегда здесь."
                AppListKind.Blocked -> "Ребёнок не может открыть их ни в какое время."
            },
            style = typography.footnote,
            color = colors.textSecondary,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(12.dp))

        if (shown.isEmpty()) {
            Text(
                text = if (all.isEmpty()) "Список появится, когда телефон ребёнка пришлёт статистику." else "Здесь пока пусто.",
                style = typography.body,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
            )
        } else {
            InsetGroupedList {
                InsetGroup {
                    shown.forEach { app ->
                        custom(separatorInset = 60.dp) {
                            AppRow(app = app, rule = rules.appRules[app.packageName], onClick = { selected = app })
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(32.dp))
    }

    selected?.let { app ->
        val rule = rules?.appRules?.get(app.packageName)
        ModalBottomSheet(
            onDismissRequest = { selected = null },
            containerColor = colors.bgGrouped,
            dragHandle = null,
        ) {
            AppSheet(
                app = app,
                rule = rule,
                onMove = { target ->
                    controller.update { r -> r.copy(appRules = r.appRules.moved(app.packageName, target)) }
                    selected = null
                },
                onLimit = { minutes ->
                    controller.update { r ->
                        val current = r.appRules[app.packageName] ?: AppRule()
                        r.copy(appRules = r.appRules + (app.packageName to current.copy(dailyLimitMinutes = minutes)))
                    }
                },
            )
        }
    }
}

/** Every app worth listing: used this week, or already carrying a rule. */
private fun allApps(apps: List<UsageAppItem>, rules: ChildRules): List<UsageAppItem> {
    val byPackage = apps.associateBy { it.packageName }.toMutableMap()
    rules.appRules.keys.forEach { pkg ->
        if (pkg !in byPackage) byPackage[pkg] = UsageAppItem(pkg, pkg.substringAfterLast('.').replaceFirstChar { it.uppercase() }, 0L)
    }
    return byPackage.values.toList()
}

private fun Map<String, AppRule>.moved(packageName: String, target: AppListKind): Map<String, AppRule> {
    val current = this[packageName] ?: AppRule()
    val updated =
        when (target) {
            AppListKind.Pool -> current.copy(blocked = false, alwaysAllowed = false)
            AppListKind.Always -> current.copy(blocked = false, alwaysAllowed = true, dailyLimitMinutes = null)
            AppListKind.Blocked -> current.copy(blocked = true, alwaysAllowed = false, dailyLimitMinutes = null)
        }
    // An app back in the pool with no limit needs no row at all.
    return if (updated == AppRule()) this - packageName else this + (packageName to updated)
}

@Composable
private fun AppRow(app: UsageAppItem, rule: AppRule?, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    val typography = LocalAppTypography.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(32.dp).clip(CircleShape).background(colors.accent.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = app.label.take(1).uppercase(), style = typography.headline, color = colors.accent)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(text = app.label, style = typography.body, color = colors.textPrimary, maxLines = 1)
            val limit = rule?.dailyLimitMinutes
            Text(
                text =
                buildString {
                    append(if (app.totalMs > 0) "Сегодня ${formatUsageMs(app.totalMs)}" else "Сегодня не открывалось")
                    if (limit != null) append(" · лимит ${formatMinutesShort(limit)}")
                },
                style = typography.footnote,
                color = if (limit != null) colors.accent else colors.textSecondary,
            )
        }
        Spacer(Modifier.width(8.dp))
        AppIcon(icon = KiteIcons.ChevronRight, tint = colors.textTertiary, size = 18.dp)
    }
}

@Composable
private fun AppSheet(app: UsageAppItem, rule: AppRule?, onMove: (AppListKind) -> Unit, onLimit: (Int?) -> Unit) {
    val colors = LocalAppColors.current
    val typography = LocalAppTypography.current
    val kind = rule.kind()
    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 16.dp)) {
        Spacer(Modifier.height(16.dp))
        Text(
            text = app.label,
            style = typography.title3,
            color = colors.textPrimary,
            modifier = Modifier.padding(horizontal = 32.dp),
        )
        Text(
            text = kind.label,
            style = typography.subhead,
            color = colors.textSecondary,
            modifier = Modifier.padding(horizontal = 32.dp),
        )
        Spacer(Modifier.height(16.dp))
        InsetGroupedList {
            InsetGroup(header = "Переместить в:") {
                AppListKind.entries.filter { it != kind }.forEach { target ->
                    row(title = target.label, icon = rowIcon(target.icon, target.color), showChevron = true, onClick = { onMove(target) })
                }
            }
            if (kind == AppListKind.Pool) {
                val limit = rule?.dailyLimitMinutes
                InsetGroup(
                    header = "Лимит на приложение",
                    footer = "Отдельный лимит внутри дневного: закончится раньше — приложение закроется, остальные работают.",
                ) {
                    custom {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(text = "Свой лимит", style = typography.body, color = colors.textPrimary, modifier = Modifier.weight(1f))
                            AppSwitch(
                                checked = limit != null,
                                onCheckedChange = { on: Boolean -> onLimit(if (on) DEFAULT_APP_LIMIT else null) },
                            )
                        }
                    }
                    if (limit != null) {
                        custom {
                            StepperRow(
                                label = null,
                                value = formatMinutesShort(limit),
                                onMinus = { onLimit((limit - STEP_MINUTES).coerceAtLeast(MIN_APP_LIMIT)) },
                                onPlus = { onLimit((limit + STEP_MINUTES).coerceAtMost(MAX_APP_LIMIT)) },
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.bgGrouped).clip(RoundedCornerShape(1.dp)))
    }
}
