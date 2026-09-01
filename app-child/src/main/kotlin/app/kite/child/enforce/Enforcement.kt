package app.kite.child.enforce

import app.kite.core.rules.ChildRules

/**
 * Pure block/allow decisions (M5). No Android types, no clocks — everything the decision
 * needs is passed in, so the rules are unit-tested. Priority: explicit app block, then
 * quiet hours, then the per-app limit, then the daily total limit.
 */
object Enforcement {
    enum class BlockReason { AppBlocked, QuietHours, AppLimit, DailyLimit }

    sealed interface Verdict {
        data object Allow : Verdict

        data class Block(val reason: BlockReason) : Verdict
    }

    fun verdict(rules: ChildRules, packageName: String, minuteOfDay: Int, usedTodayMs: Long, usedAppTodayMs: Long): Verdict {
        val appRule = rules.appRules[packageName]
        if (appRule?.blocked == true) return Verdict.Block(BlockReason.AppBlocked)
        if (rules.quietHours.any { it.contains(minuteOfDay) }) return Verdict.Block(BlockReason.QuietHours)
        appRule?.dailyLimitMinutes?.let { limit ->
            if (usedAppTodayMs >= limit * 60_000L) return Verdict.Block(BlockReason.AppLimit)
        }
        rules.dailyLimitMinutes?.let { limit ->
            if (usedTodayMs >= limit * 60_000L) return Verdict.Block(BlockReason.DailyLimit)
        }
        return Verdict.Allow
    }

    /**
     * Which warning threshold the remaining time is in: 1 or 15 minutes — the ONLY two
     * warnings the product ever shows (CLAUDE.md). Null = nothing to warn about.
     * The caller de-duplicates so each threshold fires once per day per scope.
     */
    fun warningThreshold(limitMinutes: Int?, usedMs: Long): Int? {
        limitMinutes ?: return null
        val remainingMs = limitMinutes * 60_000L - usedMs
        return when {
            remainingMs <= 0 -> null // already blocked, not a warning
            remainingMs <= 60_000L -> 1
            remainingMs <= 15 * 60_000L -> 15
            else -> null
        }
    }
}
