package app.kite.child.status

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.LaunchedEffect
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
import app.kite.core.design.components.AppButtonStyle
import app.kite.core.design.components.HourBarsCard
import app.kite.core.design.components.KiteLoader
import app.kite.core.design.components.UsageAppsCard
import app.kite.core.design.components.UsagePeriodSwitch
import app.kite.core.design.components.UsageTotalHeader
import app.kite.core.design.components.WeekBarsCard
import app.kite.core.design.components.formatUsageMs

/**
 * «Моё время» — the child sees its own screen time, the same День/Неделя layout the parent
 * gets, drawn from the local Room table (no network, no waiting for a sync). Play policy
 * requires the child to be able to see what is monitored; this is that screen with numbers.
 */
@Composable
fun ChildStatsScreen(summary: TodaySummary, onClose: () -> Unit) {
    val colors = LocalAppColors.current
    val typography = LocalAppTypography.current

    var period by remember { mutableIntStateOf(0) }
    var today by remember { mutableStateOf<TodaySummary.Today?>(null) }
    var week by remember { mutableStateOf<TodaySummary.Week?>(null) }

    LaunchedEffect(Unit) { today = summary.today() }
    LaunchedEffect(period) { if (period == 1 && week == null) week = summary.week() }

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
            Text(text = "Моё время", style = typography.largeTitle, color = colors.textPrimary, modifier = Modifier.weight(1f))
            AppButton(text = "Закрыть", style = AppButtonStyle.Plain, onClick = onClose)
        }
        Spacer(Modifier.height(12.dp))
        UsagePeriodSwitch(labels = listOf("День", "Неделя"), selectedIndex = period, onSelect = { period = it })
        Spacer(Modifier.height(16.dp))

        val day = today
        if (period == 0) {
            if (day == null) {
                Spinner()
            } else {
                UsageTotalHeader(
                    caption = "Сегодня",
                    totalMs = day.usedMs,
                    note = day.remainingMinutes?.let { left -> "Осталось ${formatUsageMs(left * 60_000L)} из дневного лимита" },
                )
                Spacer(Modifier.height(12.dp))
                HourBarsCard(hourly = day.hourly)
                Spacer(Modifier.height(20.dp))
                UsageAppsCard(items = day.apps.take(MAX_APPS))
                if (day.bonusMinutes > 0) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Из них +${day.bonusMinutes} мин добавлено за задания",
                        style = typography.footnote,
                        color = colors.textSecondary,
                        modifier = Modifier.padding(start = 16.dp),
                    )
                }
            }
        } else {
            val weekData = week
            if (weekData == null) {
                Spinner()
            } else {
                UsageTotalHeader(caption = "В среднем за день", totalMs = weekData.averageMs)
                Spacer(Modifier.height(12.dp))
                WeekBarsCard(labels = weekData.labels, totals = weekData.totals, averageMs = weekData.averageMs)
                Spacer(Modifier.height(20.dp))
                UsageAppsCard(items = weekData.apps.take(MAX_APPS))
            }
        }

        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun Spinner() {
    val colors = LocalAppColors.current
    Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
        KiteLoader(size = 64.dp)
    }
}

private const val MAX_APPS = 8
