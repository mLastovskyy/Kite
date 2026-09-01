package app.kite.child.usage

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import app.kite.child.permissions.ProtectionInspector
import app.kite.child.permissions.ProtectionRequirement
import app.kite.core.usage.ForegroundIntervals
import app.kite.core.usage.HourBuckets
import app.kite.core.usage.UsageDao
import app.kite.core.usage.UsageHourEntity
import java.time.LocalDate
import java.time.ZoneId

/**
 * Incremental screen-time collection: reads UsageStatsManager events since the last run,
 * pairs them into foreground intervals ([ForegroundIntervals]), splits them into local
 * hour buckets ([HourBuckets]) and accumulates into Room. Raw events only live in the
 * system for about a week, so this must run regularly (see [UsageCollectWorker]).
 */
class UsageCollector(private val context: Context, private val dao: UsageDao) {
    private val prefs = context.getSharedPreferences("usage_collector", Context.MODE_PRIVATE)
    private val inspector = ProtectionInspector(context)

    suspend fun collect(now: Long = System.currentTimeMillis()) {
        // Without Usage Access the system returns an EMPTY stream, not an error. Advancing
        // the watermark then would silently discard history that becomes readable once the
        // permission is granted — so bail out without touching state.
        if (!inspector.isSatisfied(ProtectionRequirement.USAGE_ACCESS, vendorAutostartConfirmed = false)) return

        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return
        val from = prefs.getLong(KEY_WATERMARK, 0L).takeIf { it in 1..now } ?: (now - DEFAULT_LOOKBACK_MS)
        val initial = prefs.getString(KEY_CARRY_PACKAGE, null)?.let { ForegroundIntervals.Carry(it, from) }

        val events = mutableListOf<ForegroundIntervals.Ev>()
        val systemEvents = usm.queryEvents(from, now)
        val event = UsageEvents.Event()
        while (systemEvents.hasNextEvent()) {
            systemEvents.getNextEvent(event)
            // ACTIVITY_RESUMED/PAUSED are the API 29 names of MOVE_TO_FOREGROUND/
            // MOVE_TO_BACKGROUND with the same values, so they match on API 26 too.
            // SCREEN_NON_INTERACTIVE (API 28) and DEVICE_SHUTDOWN (API 29) simply never
            // occur on older devices — sessions then close on the next PAUSED.
            val kind =
                when (event.eventType) {
                    UsageEvents.Event.ACTIVITY_RESUMED -> ForegroundIntervals.Kind.Resumed
                    UsageEvents.Event.ACTIVITY_PAUSED -> ForegroundIntervals.Kind.Paused
                    UsageEvents.Event.SCREEN_NON_INTERACTIVE,
                    UsageEvents.Event.DEVICE_SHUTDOWN,
                    -> ForegroundIntervals.Kind.ScreenOff
                    else -> null
                } ?: continue
            events += ForegroundIntervals.Ev(kind, event.packageName, event.timeStamp)
        }

        val outcome = ForegroundIntervals.reduce(events, initial, endAt = now)
        val zone = ZoneId.systemDefault()
        val rows =
            outcome.intervals
                .flatMap { interval ->
                    HourBuckets.split(interval.startMs, interval.endMs, zone).map { bucket ->
                        UsageHourEntity(bucket.day, bucket.hour, interval.packageName, bucket.ms)
                    }
                }
                // Same (day, hour, package) can appear from several intervals — merge before
                // the per-row upsert loop in accumulate().
                .groupBy { Triple(it.day, it.hour, it.packageName) }
                .map { (key, group) -> UsageHourEntity(key.first, key.second, key.third, group.sumOf { it.foregroundMs }) }

        if (rows.isNotEmpty()) dao.accumulate(rows)
        prefs.edit()
            .putLong(KEY_WATERMARK, now)
            .putString(KEY_CARRY_PACKAGE, outcome.carry?.packageName)
            .apply()

        dao.purgeBefore(LocalDate.now(zone).minusDays(RETENTION_DAYS).toString())
    }

    private companion object {
        const val KEY_WATERMARK = "last_event_time"
        const val KEY_CARRY_PACKAGE = "carry_package"

        /** First run looks back three days; system event retention is about a week. */
        const val DEFAULT_LOOKBACK_MS = 3L * 24 * 60 * 60 * 1000
        const val RETENTION_DAYS = 90L
    }
}
