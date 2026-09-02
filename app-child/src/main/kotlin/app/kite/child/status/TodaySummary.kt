package app.kite.child.status

import android.content.Context
import android.content.pm.PackageManager
import app.kite.child.enforce.BonusStore
import app.kite.child.enforce.RulesStore
import app.kite.child.usage.UsageCollector
import app.kite.core.design.components.UsageAppItem
import app.kite.core.usage.UsageDao
import java.time.LocalDate
import java.time.ZoneId

/**
 * What the child sees about its own time. Everything is read from the local Room table and
 * the cached rules, so «Моё время» works with no network — and the numbers are exactly the
 * ones enforcement used, not a server round-trip that might disagree with the block screen.
 *
 * The child seeing its own statistics is also the transparency Play policy asks for: no
 * hidden monitoring, the same figures the parent gets.
 */
class TodaySummary(
    private val context: Context,
    private val collector: UsageCollector,
    private val dao: UsageDao,
    private val rulesStore: RulesStore,
    private val bonusStore: BonusStore,
) {
    /** One local day: what was used, what the rule allows, and the breakdown. */
    data class Today(
        val usedMs: Long,
        val limitMinutes: Int?,
        val bonusMinutes: Int,
        val hourly: List<Long>,
        val apps: List<UsageAppItem>,
    ) {
        /** Minutes left of today's allowance; null when there is no limit at all. */
        val remainingMinutes: Int?
            get() = limitMinutes?.let { limit -> ((limit + bonusMinutes) - usedMs / 60_000L).coerceAtLeast(0L).toInt() }
    }

    /** Seven days back including today, for the «Неделя» segment. */
    data class Week(val labels: List<String>, val totals: List<Long>, val apps: List<UsageAppItem>) {
        val averageMs: Long get() = if (totals.isEmpty()) 0L else totals.sum() / totals.size
    }

    suspend fun today(): Today {
        // Bring Room up to `now` so the number matches what enforcement counts.
        runCatching { collector.collect() }
        val zone = ZoneId.systemDefault()
        val date = LocalDate.now(zone)
        val day = date.toString()
        val hourTotals = dao.hourTotals(day).associate { it.hour to it.totalMs }
        val rules = rulesStore.rules()
        return Today(
            usedMs = dao.dayTotals(day, day).firstOrNull()?.totalMs ?: 0L,
            limitMinutes = rules.limitFor(date.dayOfWeek.value),
            bonusMinutes = bonusStore.minutesFor(day),
            hourly = List(24) { hour -> hourTotals[hour] ?: 0L },
            apps = appItems(day, day),
        )
    }

    suspend fun week(): Week {
        runCatching { collector.collect() }
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val start = today.minusDays(6)
        val days = (0..6).map { start.plusDays(it.toLong()) }
        val totalsByDay = dao.dayTotals(start.toString(), today.toString()).associate { it.day to it.totalMs }
        return Week(
            labels = days.map { WEEKDAY_SHORT[it.dayOfWeek.value - 1] },
            totals = days.map { totalsByDay[it.toString()] ?: 0L },
            apps = appItems(start.toString(), today.toString()),
        )
    }

    private suspend fun appItems(fromDay: String, toDay: String): List<UsageAppItem> = dao.appTotals(fromDay, toDay)
        .map { row -> UsageAppItem(packageName = row.packageName, label = labelFor(row.packageName), totalMs = row.totalMs) }

    private fun labelFor(packageName: String): String = runCatching {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, PackageManager.GET_META_DATA)).toString()
    }.getOrDefault(packageName)

    private companion object {
        val WEEKDAY_SHORT = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")
    }
}
