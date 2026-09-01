package app.kite.child.enforce

import app.kite.core.rules.AppRule
import app.kite.core.rules.ChildRules
import app.kite.core.rules.QuietInterval
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EnforcementTest {
    private val minute = 60_000L

    @Test
    fun `no rules means allow`() {
        assertEquals(
            Enforcement.Verdict.Allow,
            Enforcement.verdict(ChildRules(), "com.app", minuteOfDay = 600, usedTodayMs = 0, usedAppTodayMs = 0),
        )
    }

    @Test
    fun `blocked app wins over everything`() {
        val rules = ChildRules(appRules = mapOf("com.app" to AppRule(blocked = true)))
        assertEquals(
            Enforcement.Verdict.Block(Enforcement.BlockReason.AppBlocked),
            Enforcement.verdict(rules, "com.app", 600, 0, 0),
        )
    }

    @Test
    fun `quiet hours block inside interval and allow outside`() {
        val rules = ChildRules(quietHours = listOf(QuietInterval(22 * 60, 7 * 60))) // wraps midnight
        assertEquals(
            Enforcement.Verdict.Block(Enforcement.BlockReason.QuietHours),
            Enforcement.verdict(rules, "com.app", 23 * 60, 0, 0),
        )
        assertEquals(
            Enforcement.Verdict.Block(Enforcement.BlockReason.QuietHours),
            Enforcement.verdict(rules, "com.app", 6 * 60, 0, 0),
        )
        assertEquals(
            Enforcement.Verdict.Allow,
            Enforcement.verdict(rules, "com.app", 12 * 60, 0, 0),
        )
    }

    @Test
    fun `per-app limit blocks that app only`() {
        val rules = ChildRules(appRules = mapOf("com.game" to AppRule(dailyLimitMinutes = 30)))
        assertEquals(
            Enforcement.Verdict.Block(Enforcement.BlockReason.AppLimit),
            Enforcement.verdict(rules, "com.game", 600, usedTodayMs = 31 * minute, usedAppTodayMs = 30 * minute),
        )
        assertEquals(
            Enforcement.Verdict.Allow,
            Enforcement.verdict(rules, "com.other", 600, usedTodayMs = 31 * minute, usedAppTodayMs = 0),
        )
    }

    @Test
    fun `daily limit blocks every app when exhausted`() {
        val rules = ChildRules(dailyLimitMinutes = 120)
        assertEquals(
            Enforcement.Verdict.Block(Enforcement.BlockReason.DailyLimit),
            Enforcement.verdict(rules, "com.any", 600, usedTodayMs = 120 * minute, usedAppTodayMs = 5 * minute),
        )
        assertEquals(
            Enforcement.Verdict.Allow,
            Enforcement.verdict(rules, "com.any", 600, usedTodayMs = 119 * minute, usedAppTodayMs = 5 * minute),
        )
    }

    @Test
    fun `warning thresholds are exactly 15 and 1`() {
        assertNull(Enforcement.warningThreshold(null, 0))
        assertNull(Enforcement.warningThreshold(120, usedMs = 100 * minute)) // 20 min left
        assertEquals(15, Enforcement.warningThreshold(120, usedMs = 105 * minute)) // 15 left
        assertEquals(15, Enforcement.warningThreshold(120, usedMs = 118 * minute)) // 2 left
        assertEquals(1, Enforcement.warningThreshold(120, usedMs = 119 * minute)) // 1 left
        assertNull(Enforcement.warningThreshold(120, usedMs = 120 * minute)) // exhausted → block, not warn
    }
}
