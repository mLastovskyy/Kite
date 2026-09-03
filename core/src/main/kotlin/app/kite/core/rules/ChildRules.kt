package app.kite.core.rules

import kotlinx.serialization.Serializable

/**
 * Enforcement rules for one child device (M5, extended in M10 for Kids360 parity). The parent
 * edits them, the server stores one jsonb row per member, the child caches the last fetched
 * copy and enforces fully OFFLINE — no network is ever needed for a block decision (CLAUDE.md).
 *
 * Every field has a default, so a document written by an older app version still decodes.
 */
@Serializable
data class ChildRules(
    /**
     * Legacy single daily limit, minutes; null = unlimited. Used only while [weekdayLimits]
     * is empty (documents written before per-weekday limits existed).
     */
    val dailyLimitMinutes: Int? = null,
    /**
     * Per-weekday daily limit («Лимиты времени»): index 0 = Monday … 6 = Sunday, minutes;
     * null = no limit that day. Empty = not configured → [dailyLimitMinutes] applies.
     */
    val weekdayLimits: List<Int?> = emptyList(),
    /**
     * Named schedules («Сон», «Учёба», …). Each one blocks ONLY the apps the parent picked
     * for it ([QuietInterval.packages]) — never the whole phone (owner, 04.09.2026).
     */
    val quietHours: List<QuietInterval> = emptyList(),
    /** Per-package rules; absence means the app is in the time-controlled pool. */
    val appRules: Map<String, AppRule> = emptyMap(),
    /** Written by the parent app on save; the freshest copy wins on the child. */
    val updatedAtEpochSeconds: Long = 0,
) {
    /** The daily limit that applies on [isoDayOfWeek] (1 = Monday … 7 = Sunday); null = unlimited. */
    fun limitFor(isoDayOfWeek: Int): Int? = if (weekdayLimits.isEmpty()) dailyLimitMinutes else weekdayLimits.getOrNull(isoDayOfWeek - 1)

    /** Seven-entry weekday list for editing: the configured one, or the legacy value spread over the week. */
    fun weekdayLimitsForEditing(): List<Int?> = if (weekdayLimits.size == 7) weekdayLimits else List(7) { dailyLimitMinutes }

    /** True when any schedule is active at this moment, whatever apps it covers. */
    fun inQuietHours(isoDayOfWeek: Int, minuteOfDay: Int): Boolean = quietHours.any { it.isActive(isoDayOfWeek, minuteOfDay) }

    /** The active schedule that closes [packageName] right now, or null when none does. */
    fun scheduleBlocking(packageName: String, isoDayOfWeek: Int, minuteOfDay: Int): QuietInterval? =
        quietHours.firstOrNull { it.isActive(isoDayOfWeek, minuteOfDay) && it.blocks(packageName) }
}

/**
 * One schedule («Расписание»). [startMinutes]/[endMinutes] are minutes from local midnight
 * (0..1439). An interval with end <= start wraps through midnight, e.g. 22:00–07:00, and
 * belongs to the day it STARTS on: «Сон» on Friday covers Friday 22:00 → Saturday 07:00.
 * [days] are ISO weekdays 1 = Monday … 7 = Sunday; the defaults (every day, enabled, no name)
 * keep documents written before schedules had names and days meaningful.
 *
 * [packages] are the apps this schedule closes — the parent picks them from the child's
 * phone. A schedule with no apps blocks nothing: the owner's rule (04.09.2026) is that a
 * schedule never shuts the whole phone, so there is no «everything» mode. Documents written
 * before this field existed decode with an empty list and show up as «Приложения не выбраны».
 */
@Serializable
data class QuietInterval(
    val startMinutes: Int,
    val endMinutes: Int,
    val name: String = "",
    val days: List<Int> = ALL_DAYS,
    val enabled: Boolean = true,
    val packages: List<String> = emptyList(),
) {
    /** Whether this schedule covers [packageName] (time and days not considered). */
    fun blocks(packageName: String): Boolean = packageName in packages

    /** Time-of-day test only, ignoring days and [enabled] (legacy helper, used by tests). */
    fun contains(minuteOfDay: Int): Boolean = if (startMinutes < endMinutes) {
        minuteOfDay in startMinutes until endMinutes
    } else {
        minuteOfDay >= startMinutes || minuteOfDay < endMinutes
    }

    fun isActive(isoDayOfWeek: Int, minuteOfDay: Int): Boolean {
        if (!enabled) return false
        if (startMinutes < endMinutes) return isoDayOfWeek in days && minuteOfDay in startMinutes until endMinutes
        // Wraps midnight: the evening part on a listed day, the morning part on the day after one.
        val dayBefore = if (isoDayOfWeek == 1) 7 else isoDayOfWeek - 1
        return (isoDayOfWeek in days && minuteOfDay >= startMinutes) || (dayBefore in days && minuteOfDay < endMinutes)
    }

    val wrapsMidnight: Boolean get() = startMinutes >= endMinutes

    companion object {
        val ALL_DAYS: List<Int> = (1..7).toList()
        val WEEKDAYS: List<Int> = (1..5).toList()

        /** Kids360's two presets, offered when the child has no schedules yet. */
        val SLEEP = QuietInterval(startMinutes = 21 * 60, endMinutes = 7 * 60, name = "Сон", days = ALL_DAYS)
        val STUDY = QuietInterval(startMinutes = 8 * 60, endMinutes = 16 * 60, name = "Учёба", days = WEEKDAYS)
    }
}

/**
 * Per-app rule. Every app is in exactly one of three lists (Kids360 model): the default
 * time-controlled pool (no rule / both flags false), «Доступны всегда» ([alwaysAllowed]) or
 * «Всегда заблокированы» ([blocked]). A per-app limit nests inside the pool.
 */
@Serializable
data class AppRule(
    /** «Всегда заблокированы»: fully blocked, regardless of time. */
    val blocked: Boolean = false,
    /** «Лимит на приложение»: per-app daily limit, minutes; null = no per-app limit. */
    val dailyLimitMinutes: Int? = null,
    /**
     * «Доступны всегда»: this app is NEVER blocked — the daily limit, schedules and the remote
     * lock do not apply to it (e.g. a dialer, maps, a learning app). Wins over everything.
     */
    val alwaysAllowed: Boolean = false,
) {
    val inPool: Boolean get() = !blocked && !alwaysAllowed
}
