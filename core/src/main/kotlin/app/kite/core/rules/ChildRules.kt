package app.kite.core.rules

import kotlinx.serialization.Serializable

/**
 * Enforcement rules for one child device (M5). The parent edits them, the server stores
 * one jsonb row per member, the child caches the last fetched copy and enforces fully
 * OFFLINE — no network is ever needed for a block decision (CLAUDE.md).
 */
@Serializable
data class ChildRules(
    /** Total screen time allowed per local day, minutes; null = unlimited. */
    val dailyLimitMinutes: Int? = null,
    /** Quiet hours; several intervals allowed, each may wrap past midnight. */
    val quietHours: List<QuietInterval> = emptyList(),
    /** Per-package rules; absence means the app is unrestricted. */
    val appRules: Map<String, AppRule> = emptyMap(),
    /** Written by the parent app on save; the freshest copy wins on the child. */
    val updatedAtEpochSeconds: Long = 0,
)

/**
 * [startMinutes]/[endMinutes] are minutes from local midnight (0..1439). An interval with
 * end <= start wraps through midnight, e.g. 22:00–07:00.
 */
@Serializable
data class QuietInterval(val startMinutes: Int, val endMinutes: Int) {
    fun contains(minuteOfDay: Int): Boolean = if (startMinutes < endMinutes) {
        minuteOfDay in startMinutes until endMinutes
    } else {
        minuteOfDay >= startMinutes || minuteOfDay < endMinutes
    }
}

@Serializable
data class AppRule(
    /** Fully blocked, regardless of time. */
    val blocked: Boolean = false,
    /** Per-app daily limit, minutes; null = no per-app limit. */
    val dailyLimitMinutes: Int? = null,
    /**
     * Exception: this app is NEVER blocked — the daily limit and quiet hours do not apply to
     * it (e.g. a dialer, maps, a learning app). Wins over everything. Enforced offline.
     */
    val alwaysAllowed: Boolean = false,
)
