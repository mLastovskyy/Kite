package app.kite.child.usage

import android.content.Context
import app.kite.child.identity.MemberIdentity
import app.kite.core.usage.UsageAppRow
import app.kite.core.usage.UsageDao
import app.kite.core.usage.UsageDayRow
import app.kite.core.usage.UsageRemote
import java.time.LocalDate
import java.time.ZoneId

/**
 * Uploads DAILY aggregates (yesterday + today) computed from local usage_hour rows.
 * Upserts are idempotent, so re-running after offline gaps is safe; a sync failure never
 * blocks collection. App labels are resolved here — the parent device may not have the
 * child's apps installed.
 */
class UsageSyncer(
    private val context: Context,
    private val dao: UsageDao,
    private val remote: UsageRemote,
    private val identity: MemberIdentity,
) {
    suspend fun sync() {
        val familyId = identity.familyId() ?: return // not paired yet
        val memberId = identity.memberId() ?: return

        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        // The whole week the parent's chart draws, not just today and yesterday: Room is
        // backfilled from UsageStatsManager, so the child could see days the server never got
        // and the two screens disagreed (owner, 07.09.2026). Upserts, a few KB per sync.
        val days = (SYNC_DAYS - 1 downTo 0).map { today.minusDays(it.toLong()).toString() }

        val dayRows =
            days.mapNotNull { day ->
                val hours = dao.hourTotals(day)
                if (hours.isEmpty()) return@mapNotNull null
                val hourly = LongArray(24)
                hours.forEach { if (it.hour in 0..23) hourly[it.hour] = it.totalMs }
                UsageDayRow(memberId, familyId, day, hourly.sum(), hourly.toList())
            }
        if (dayRows.isEmpty()) return

        val appRows =
            days.flatMap { day ->
                dao.appTotals(day, day).map { app ->
                    UsageAppRow(
                        memberId = memberId,
                        familyId = familyId,
                        day = day,
                        packageName = app.packageName,
                        appLabel = labelFor(app.packageName),
                        foregroundMs = app.totalMs,
                    )
                }
            }

        remote.upsertDays(dayRows).getOrThrow()
        if (appRows.isNotEmpty()) remote.upsertApps(appRows).getOrThrow()
    }

    private fun labelFor(packageName: String): String = runCatching {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
    }.getOrDefault(packageName)

    private companion object {
        /** Same window the parent's «Неделя» draws. */
        const val SYNC_DAYS = 7
    }
}
