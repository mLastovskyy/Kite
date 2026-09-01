package app.kite.parent.family

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.kite.core.design.LocalAppColors
import app.kite.core.design.LocalAppTypography
import app.kite.core.design.components.AppButton
import app.kite.core.design.components.AppButtonStyle
import app.kite.core.design.components.AppSpinner
import app.kite.core.family.FamilyMember
import app.kite.core.family.FamilyRepository
import app.kite.core.rules.RulesRemote
import app.kite.core.secure.SecureStore
import app.kite.core.usage.UsageAppRow
import app.kite.core.usage.UsageDayRow
import app.kite.core.usage.UsageRemote
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

// iOS Screen Time rank palette: #1 blue, #2 teal, #3 orange, the rest grey.
private val RANK_COLORS = listOf(Color(0xFF007AFF), Color(0xFF30B0C7), Color(0xFFFF9500))
private val RANK_GREY = Color(0xFF8E8E93)

private enum class UsageTab { Day, Week }

private sealed interface UsageState {
    data object Loading : UsageState

    data class Ready(val days: List<UsageDayRow>, val apps: List<UsageAppRow>) : UsageState

    data class Failed(val message: String) : UsageState
}

/**
 * iOS-style screen time for one child (memory spec m4-screen-time-spec): Day/Week segment,
 * bar chart, ranked app list with share bars. Reads the synced daily aggregates — the day
 * chart is the 24-slot hourly histogram (one color: per-app hourly data never leaves the
 * child device), the week chart stacks each day by the week's top-3 apps.
 */
@Composable
fun ChildUsageScreen(
    member: FamilyMember,
    usageRemote: UsageRemote,
    rulesRemote: RulesRemote,
    familyRepository: FamilyRepository,
    secureStore: SecureStore,
    onClose: () -> Unit,
) {
    val colors = LocalAppColors.current
    val typography = LocalAppTypography.current

    var tab by remember { mutableStateOf(UsageTab.Day) }
    var state by remember { mutableStateOf<UsageState>(UsageState.Loading) }
    var showCode by remember { mutableStateOf(false) }
    var showRules by remember { mutableStateOf(false) }
    var reloadKey by remember { mutableStateOf(0) }

    val today = remember { LocalDate.now() }
    val weekStart = remember { today.minusDays(6) }

    LaunchedEffect(member.id, reloadKey) {
        state = UsageState.Loading
        val days = usageRemote.days(member.id, weekStart.toString(), today.toString())
        val apps = usageRemote.apps(member.id, weekStart.toString(), today.toString())
        state =
            if (days.isSuccess && apps.isSuccess) {
                UsageState.Ready(days.getOrThrow(), apps.getOrThrow())
            } else {
                UsageState.Failed(
                    days.exceptionOrNull()?.message ?: apps.exceptionOrNull()?.message ?: "Ошибка загрузки",
                )
            }
    }

    if (showCode) {
        ApprovalCodeScreen(
            member = member,
            familyRepository = familyRepository,
            secureStore = secureStore,
            onClose = { showCode = false },
        )
        return
    }

    if (showRules) {
        val knownApps =
            (state as? UsageState.Ready)?.apps
                ?.let { rows -> aggregateApps(rows).map { it.packageName to it.label } }
                .orEmpty()
        RulesScreen(
            member = member,
            knownApps = knownApps,
            rulesRemote = rulesRemote,
            onClose = { showRules = false },
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
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = member.displayName.ifBlank { "Ребёнок" },
                style = typography.title1,
                color = colors.textPrimary,
                modifier = Modifier.weight(1f),
            )
            AppButton(text = "Закрыть", style = AppButtonStyle.Plain, onClick = onClose)
        }
        Spacer(Modifier.height(12.dp))
        SegmentedControl(
            selected = tab,
            onSelect = { tab = it },
        )
        Spacer(Modifier.height(16.dp))

        when (val s = state) {
            UsageState.Loading ->
                Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
                    AppSpinner(color = colors.accent, size = 28.dp)
                }

            is UsageState.Failed ->
                Column(Modifier.fillMaxWidth().padding(vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = s.message, style = typography.body, color = colors.textSecondary, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(12.dp))
                    AppButton(text = "Повторить", style = AppButtonStyle.Tinted, onClick = { reloadKey++ })
                }

            is UsageState.Ready ->
                if (s.days.isEmpty()) {
                    Text(
                        text = "Данных пока нет — они появятся, когда телефон ребёнка синхронизируется.",
                        style = typography.body,
                        color = colors.textSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
                    )
                } else {
                    when (tab) {
                        UsageTab.Day -> DayContent(s, today.toString())
                        UsageTab.Week -> WeekContent(s, weekStart, today)
                    }
                }
        }

        Spacer(Modifier.height(24.dp))
        AppButton(text = "Правила", onClick = { showRules = true })
        Spacer(Modifier.height(8.dp))
        AppButton(text = "Код подтверждения", style = AppButtonStyle.Tinted, onClick = { showCode = true })
        Spacer(Modifier.height(24.dp))
    }
}

// ── Day ─────────────────────────────────────────────────────────────────────
@Composable
private fun DayContent(state: UsageState.Ready, today: String) {
    val colors = LocalAppColors.current
    val typography = LocalAppTypography.current
    val dayRow = state.days.lastOrNull { it.day == today }
    val total = dayRow?.totalMs ?: 0L

    Text(text = "Сегодня", style = typography.subhead, color = colors.textSecondary)
    Text(text = formatMs(total), style = typography.largeTitle, color = colors.textPrimary)
    Spacer(Modifier.height(12.dp))
    HourBarChart(hourly = dayRow?.hourlyMs.orEmpty())
    Spacer(Modifier.height(20.dp))
    AppList(rows = state.apps.filter { it.day == today })
}

@Composable
private fun HourBarChart(hourly: List<Long>) {
    val colors = LocalAppColors.current
    val typography = LocalAppTypography.current
    val accent = colors.accent
    val separator = colors.separator
    val bars = List(24) { hourly.getOrElse(it) { 0L } }
    val max = (bars.maxOrNull() ?: 0L).coerceAtLeast(1L)

    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(colors.bgBase).padding(16.dp)) {
        Canvas(Modifier.fillMaxWidth().height(120.dp)) {
            val slot = size.width / 24f
            val barWidth = slot * 0.62f
            bars.forEachIndexed { index, value ->
                val h = (value.toFloat() / max) * (size.height - 2f)
                if (h > 0f) {
                    drawRoundRect(
                        color = accent,
                        topLeft = Offset(index * slot + (slot - barWidth) / 2f, size.height - h),
                        size = Size(barWidth, h),
                        cornerRadius = CornerRadius(barWidth / 2.5f, barWidth / 2.5f),
                    )
                }
            }
            drawLine(
                color = separator,
                start = Offset(0f, size.height),
                end = Offset(size.width, size.height),
                strokeWidth = 1f,
            )
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth()) {
            Text(text = "00", style = typography.caption, color = colors.textSecondary, modifier = Modifier.weight(1f))
            Text(
                text = "12",
                style = typography.caption,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "24",
                style = typography.caption,
                color = colors.textSecondary,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

// ── Week ────────────────────────────────────────────────────────────────────
@Composable
private fun WeekContent(state: UsageState.Ready, weekStart: LocalDate, today: LocalDate) {
    val colors = LocalAppColors.current
    val typography = LocalAppTypography.current

    val weekDays = (0..6).map { weekStart.plusDays(it.toLong()) }
    val totalsByDay = state.days.associate { it.day to it.totalMs }
    val average = weekDays.sumOf { totalsByDay[it.toString()] ?: 0L } / 7

    // Week's top-3 apps color the stacked bars, everything else is grey.
    val weekApps = aggregateApps(state.apps)
    val topPackages = weekApps.take(3).map { it.packageName }

    Text(text = "В среднем за день", style = typography.subhead, color = colors.textSecondary)
    Text(text = formatMs(average), style = typography.largeTitle, color = colors.textPrimary)
    Spacer(Modifier.height(12.dp))
    WeekBarChart(
        days = weekDays,
        totalsByDay = totalsByDay,
        appsByDay = state.apps.groupBy { it.day },
        topPackages = topPackages,
        averageMs = average,
    )
    Spacer(Modifier.height(12.dp))
    Legend(weekApps.take(3))
    Spacer(Modifier.height(20.dp))
    AppList(rows = state.apps)
}

@Composable
private fun WeekBarChart(
    days: List<LocalDate>,
    totalsByDay: Map<String, Long>,
    appsByDay: Map<String, List<UsageAppRow>>,
    topPackages: List<String>,
    averageMs: Long,
) {
    val colors = LocalAppColors.current
    val typography = LocalAppTypography.current
    val separator = colors.separator
    val max = (totalsByDay.values.maxOrNull() ?: 0L).coerceAtLeast(1L)

    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(colors.bgBase).padding(16.dp)) {
        Canvas(Modifier.fillMaxWidth().height(140.dp)) {
            val slot = size.width / 7f
            val barWidth = slot * 0.5f
            days.forEachIndexed { index, date ->
                val key = date.toString()
                val total = totalsByDay[key] ?: 0L
                if (total <= 0L) return@forEachIndexed
                val fullHeight = (total.toFloat() / max) * (size.height - 2f)
                val x = index * slot + (slot - barWidth) / 2f
                // Stack: top-3 apps bottom-up in rank order, remainder grey on top.
                val perApp = appsByDay[key].orEmpty().associate { it.packageName to it.foregroundMs }
                var bottom = size.height
                topPackages.forEachIndexed { rank, pkg ->
                    val ms = perApp[pkg] ?: 0L
                    val h = (ms.toFloat() / total) * fullHeight
                    if (h > 0f) {
                        drawRect(color = RANK_COLORS[rank], topLeft = Offset(x, bottom - h), size = Size(barWidth, h))
                        bottom -= h
                    }
                }
                val rest = fullHeight - (size.height - bottom)
                if (rest > 0f) {
                    drawRect(color = RANK_GREY, topLeft = Offset(x, bottom - rest), size = Size(barWidth, rest))
                }
            }
            // Dashed average line, iOS-style.
            if (averageMs > 0) {
                val y = size.height - (averageMs.toFloat() / max) * (size.height - 2f)
                drawLine(
                    color = separator,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 2f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)),
                )
            }
            drawLine(
                color = separator,
                start = Offset(0f, size.height),
                end = Offset(size.width, size.height),
                strokeWidth = 1f,
            )
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth()) {
            days.forEach { date ->
                Text(
                    text = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale("ru")),
                    style = typography.caption,
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun Legend(top: List<AppTotalUi>) {
    val colors = LocalAppColors.current
    val typography = LocalAppTypography.current
    if (top.isEmpty()) return
    Row(Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp)) {
        top.forEachIndexed { index, app ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(RANK_COLORS[index]))
                Spacer(Modifier.size(4.dp))
                Text(text = app.label, style = typography.caption, color = colors.textSecondary, maxLines = 1)
            }
        }
    }
}

// ── App list ────────────────────────────────────────────────────────────────
private data class AppTotalUi(val packageName: String, val label: String, val totalMs: Long)

private fun aggregateApps(rows: List<UsageAppRow>): List<AppTotalUi> = rows
    .groupBy { it.packageName }
    .map { (pkg, group) ->
        AppTotalUi(
            packageName = pkg,
            label = group.firstOrNull { it.appLabel.isNotBlank() }?.appLabel ?: pkg,
            totalMs = group.sumOf { it.foregroundMs },
        )
    }
    .sortedByDescending { it.totalMs }

@Composable
private fun AppList(rows: List<UsageAppRow>) {
    val colors = LocalAppColors.current
    val typography = LocalAppTypography.current
    val apps = aggregateApps(rows)
    if (apps.isEmpty()) return
    val maxMs = apps.first().totalMs.coerceAtLeast(1L)

    Text(
        text = "Приложения",
        style = typography.footnote,
        color = colors.textSecondary,
        modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
    )
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(colors.bgBase)) {
        apps.forEachIndexed { index, app ->
            val rankColor = RANK_COLORS.getOrElse(index) { RANK_GREY }
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(32.dp).clip(CircleShape).background(rankColor.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = app.label.take(1).uppercase(),
                            style = typography.headline,
                            color = rankColor,
                        )
                    }
                    Spacer(Modifier.size(12.dp))
                    Text(
                        text = app.label,
                        style = typography.body,
                        color = colors.textPrimary,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                    Text(text = formatMs(app.totalMs), style = typography.subhead, color = colors.textSecondary)
                }
                Spacer(Modifier.height(6.dp))
                // Thin share bar under the row, iOS-style.
                Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(colors.separator)) {
                    Box(
                        Modifier
                            .fillMaxWidth(fraction = (app.totalMs.toFloat() / maxMs).coerceIn(0f, 1f))
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(rankColor),
                    )
                }
            }
            if (index < apps.lastIndex) {
                Box(Modifier.padding(start = 60.dp).fillMaxWidth().height(1.dp).background(colors.separator))
            }
        }
    }
}

// ── Shared bits ─────────────────────────────────────────────────────────────
@Composable
private fun SegmentedControl(selected: UsageTab, onSelect: (UsageTab) -> Unit) {
    val colors = LocalAppColors.current
    val typography = LocalAppTypography.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(9.dp))
            .background(colors.separator.copy(alpha = 0.5f))
            .padding(2.dp),
    ) {
        listOf(UsageTab.Day to "День", UsageTab.Week to "Неделя").forEach { (tab, label) ->
            val active = tab == selected
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(7.dp))
                    .background(if (active) colors.bgBase else Color.Transparent)
                    .clickable { onSelect(tab) }
                    .padding(vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = typography.subhead,
                    color = if (active) colors.textPrimary else colors.textSecondary,
                )
            }
        }
    }
}

private fun formatMs(ms: Long): String {
    val totalMinutes = ms / 60_000
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours > 0 && minutes > 0 -> "$hours ч $minutes мин"
        hours > 0 -> "$hours ч"
        else -> "$minutes мин"
    }
}
