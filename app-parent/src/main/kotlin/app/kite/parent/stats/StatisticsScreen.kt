package app.kite.parent.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.kite.core.design.LocalAppColors
import app.kite.core.design.LocalAppTypography
import app.kite.core.design.components.AppButton
import app.kite.core.design.components.AppButtonStyle
import app.kite.core.design.components.HourBarsCard
import app.kite.core.design.components.ScreenLoading
import app.kite.core.design.components.UsageAppItem
import app.kite.core.design.components.UsageAppsCard
import app.kite.core.design.components.UsageLegend
import app.kite.core.design.components.UsagePeriodSwitch
import app.kite.core.design.components.UsageTotalHeader
import app.kite.core.design.components.WeekBarsCard
import app.kite.core.design.components.WeekStackPart
import app.kite.core.design.components.formatUsageMs
import app.kite.core.design.components.usageRankColor
import app.kite.core.family.FamilyMember
import app.kite.core.usage.UsageAppRow
import app.kite.core.usage.UsageDayRow
import app.kite.core.usage.UsageRemote
import app.kite.parent.home.ChildSwitcher
import app.kite.parent.rules.InstalledAppIcon
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/** Synced aggregates for the last seven days, shared by the tab and by Главная's hero card. */
class UsageWeek(val days: List<UsageDayRow>, val apps: List<UsageAppRow>, val from: LocalDate, val to: LocalDate) {
    fun dayTotal(day: LocalDate): Long = days.firstOrNull { it.day == day.toString() }?.totalMs ?: 0L

    fun hourly(day: LocalDate): List<Long> = days.firstOrNull { it.day == day.toString() }?.hourlyMs ?: List(24) { 0L }

    /** Ranked apps for one day, or for the whole range when [day] is null. */
    fun apps(day: LocalDate?): List<UsageAppItem> = apps
        .filter { day == null || it.day == day.toString() }
        .groupBy { it.packageName }
        .map { (pkg, rows) ->
            UsageAppItem(
                packageName = pkg,
                label = rows.firstOrNull { it.appLabel.isNotBlank() }?.appLabel ?: pkg,
                totalMs = rows.sumOf { it.foregroundMs },
            )
        }
        .sortedByDescending { it.totalMs }
}

/**
 * «Статистика» tab — the child-style layout the owner asked for: День/Неделя switch, total,
 * hourly bars (day) or a stacked week with the top-3 apps, then the ranked app list. Built
 * only from the shared [app.kite.core.design.components] charts; no actions here — those
 * live on Главная.
 */
@Composable
fun StatisticsScreen(
    children: List<FamilyMember>,
    selected: FamilyMember?,
    onSelectChild: (FamilyMember) -> Unit,
    usageRemote: UsageRemote,
    onLimitApp: (UsageAppItem) -> Unit = {},
) {
    val colors = LocalAppColors.current
    val typography = LocalAppTypography.current
    var period by remember { mutableIntStateOf(0) }
    var week by remember { mutableStateOf<UsageWeek?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var reloadKey by remember { mutableIntStateOf(0) }
    var detail by remember { mutableStateOf<UsageAppItem?>(null) }
    val today = remember { LocalDate.now() }

    LaunchedEffect(selected?.id, reloadKey) {
        val child = selected ?: return@LaunchedEffect
        week = null
        error = null
        loadUsageWeek(usageRemote, child.id, today)
            .onSuccess { week = it }
            .onFailure { error = it.message ?: "Ошибка загрузки" }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.bgGrouped)
            .safeContentPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(12.dp))
        Text(text = "Статистика", style = typography.largeTitle, color = colors.textPrimary)
        Spacer(Modifier.height(12.dp))
        if (selected == null) {
            Text(
                text = "Добавьте ребёнка — статистика появится здесь.",
                style = typography.body,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
            )
            return@Column
        }
        ChildSwitcher(children = children, selected = selected, onSelect = onSelectChild)
        Spacer(Modifier.height(16.dp))
        UsagePeriodSwitch(labels = listOf("День", "Неделя"), selectedIndex = period, onSelect = { period = it })
        Spacer(Modifier.height(16.dp))

        val data = week
        when {
            data == null && error == null -> ScreenLoading(caption = "Считаем экранное время…")
            data == null ->
                Column(Modifier.fillMaxWidth().padding(vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = error!!, style = typography.body, color = colors.textSecondary, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(12.dp))
                    AppButton(text = "Повторить", style = AppButtonStyle.Tinted, onClick = { reloadKey++ })
                }
            data.days.isEmpty() ->
                Text(
                    text = "Данных пока нет — они появятся, когда телефон ребёнка синхронизируется.",
                    style = typography.body,
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
                )
            period == 0 -> DayContent(data, today, onAppClick = {
                detail = it
            }, iconFor = { InstalledAppIcon(memberId = selected.id, packageName = it.packageName, label = it.label) })
            else -> WeekContent(data, today, onAppClick = {
                detail = it
            }, iconFor = { InstalledAppIcon(memberId = selected.id, packageName = it.packageName, label = it.label) })
        }
        Spacer(Modifier.height(32.dp))
    }

    val shownWeek = week
    detail?.let { app ->
        if (shownWeek != null) {
            AppDetailSheet(week = shownWeek, app = app, onDismiss = { detail = null }, onLimit = {
                detail = null
                onLimitApp(app)
            })
        }
    }
}

suspend fun loadUsageWeek(usageRemote: UsageRemote, memberId: String, today: LocalDate): Result<UsageWeek> {
    val from = today.minusDays(6)
    val days = usageRemote.days(memberId, from.toString(), today.toString())
    val apps = usageRemote.apps(memberId, from.toString(), today.toString())
    return if (days.isSuccess && apps.isSuccess) {
        Result.success(UsageWeek(days.getOrThrow(), apps.getOrThrow(), from, today))
    } else {
        Result.failure(days.exceptionOrNull() ?: apps.exceptionOrNull() ?: IllegalStateException("Ошибка загрузки"))
    }
}

@Composable
private fun DayContent(week: UsageWeek, today: LocalDate, onAppClick: (UsageAppItem) -> Unit, iconFor: @Composable (UsageAppItem) -> Unit) {
    UsageTotalHeader(caption = "Сегодня", totalMs = week.dayTotal(today))
    Spacer(Modifier.height(12.dp))
    HourBarsCard(hourly = week.hourly(today))
    Spacer(Modifier.height(24.dp))
    UsageAppsCard(items = week.apps(today), onItemClick = onAppClick, iconFor = iconFor)
}

@Composable
private fun WeekContent(
    week: UsageWeek,
    today: LocalDate,
    onAppClick: (UsageAppItem) -> Unit,
    iconFor: @Composable (UsageAppItem) -> Unit,
) {
    val days = (0..6).map { week.from.plusDays(it.toLong()) }
    val totals = days.map(week::dayTotal)
    val average = totals.sum() / 7
    val top = week.apps(null).take(3)
    val stacks =
        days.map { day ->
            val perApp = week.apps(day).associateBy { it.packageName }
            val parts = top.mapIndexed { index, app -> WeekStackPart(usageRankColor(index), perApp[app.packageName]?.totalMs ?: 0L) }
            val rest = (week.dayTotal(day) - parts.sumOf { it.ms }).coerceAtLeast(0L)
            parts + WeekStackPart(app.kite.core.design.components.UsageRestColor, rest)
        }
    val labels = days.map { it.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale("ru")).replaceFirstChar { c -> c.uppercase() } }

    UsageTotalHeader(caption = "В среднем в день", totalMs = average, note = "За последние 7 дней")
    Spacer(Modifier.height(12.dp))
    WeekBarsCard(labels = labels, totals = totals, stacks = stacks, averageMs = average)
    Spacer(Modifier.height(10.dp))
    UsageLegend(items = top)
    Spacer(Modifier.height(24.dp))
    UsageAppsCard(items = week.apps(null), header = "Приложения за неделю", onItemClick = onAppClick, iconFor = iconFor)
}

/**
 * One app over the last seven days: total, average, a bar per day, and the shortcut to its
 * own limit («Лимит на это приложение» lands on Главная → Приложения with the app open).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppDetailSheet(week: UsageWeek, app: UsageAppItem, onDismiss: () -> Unit, onLimit: () -> Unit) {
    val colors = LocalAppColors.current
    val typography = LocalAppTypography.current
    val days = (0..6).map { week.from.plusDays(it.toLong()) }
    val totals = days.map { day ->
        week.apps.filter { it.packageName == app.packageName && it.day == day.toString() }.sumOf { it.foregroundMs }
    }
    val labels = days.map {
        it.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.forLanguageTag("ru")).replaceFirstChar { c -> c.uppercase() }
    }
    val total = totals.sum()
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = colors.bgGrouped, dragHandle = null) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 16.dp)) {
            Text(text = app.label, style = typography.title3, color = colors.textPrimary)
            Spacer(Modifier.height(12.dp))
            UsageTotalHeader(caption = "За 7 дней", totalMs = total, note = "В среднем ${formatUsageMs(total / 7)} в день")
            Spacer(Modifier.height(12.dp))
            WeekBarsCard(labels = labels, totals = totals, averageMs = total / 7)
            Spacer(Modifier.height(16.dp))
            AppButton(text = "Лимит на это приложение", onClick = onLimit)
        }
    }
}
