package app.kite.child.enforce

import app.kite.core.rules.ChildRules

/**
 * Pure block/allow decisions (M5). No Android types, no clocks — everything the decision
 * needs is passed in, so the rules are unit-tested. Priority: explicit app block, then an
 * active schedule, then the per-app limit, then today's daily limit.
 */
object Enforcement {
    enum class BlockReason { AppBlocked, QuietHours, AppLimit, DailyLimit, RemoteLocked }

    sealed interface Verdict {
        data object Allow : Verdict

        data class Block(val reason: BlockReason) : Verdict
    }

    /**
     * [isoDayOfWeek] is 1 = Monday … 7 = Sunday (java.time convention). [dayBonusMinutes] /
     * [appBonusMinutes] are parent-granted extra minutes for today (for all apps / for this
     * app) — added on top of the respective limit.
     */
    fun verdict(
        rules: ChildRules,
        packageName: String,
        isoDayOfWeek: Int,
        minuteOfDay: Int,
        usedTodayMs: Long,
        usedAppTodayMs: Long,
        dayBonusMinutes: Int = 0,
        appBonusMinutes: Int = 0,
    ): Verdict {
        val appRule = rules.appRules[packageName]
        // «Доступны всегда» is never blocked — beats limits and schedules.
        if (appRule?.alwaysAllowed == true) return Verdict.Allow
        if (appRule?.blocked == true) return Verdict.Block(BlockReason.AppBlocked)
        if (rules.inQuietHours(isoDayOfWeek, minuteOfDay)) return Verdict.Block(BlockReason.QuietHours)
        appRule?.dailyLimitMinutes?.let { limit ->
            if (usedAppTodayMs >= (limit + appBonusMinutes) * 60_000L) return Verdict.Block(BlockReason.AppLimit)
        }
        rules.limitFor(isoDayOfWeek)?.let { limit ->
            if (usedTodayMs >= (limit + dayBonusMinutes) * 60_000L) return Verdict.Block(BlockReason.DailyLimit)
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
