package app.kite.child.enforce

import app.kite.core.rules.AppRule
import app.kite.core.rules.ChildRules
import app.kite.core.rules.QuietInterval
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EnforcementTest {
    private val minute = 60_000L
    private val monday = 1
    private val friday = 5
    private val saturday = 6
    private val sunday = 7

    private fun verdict(
        rules: ChildRules,
        pkg: String,
        day: Int = monday,
        minuteOfDay: Int = 600,
        usedTodayMs: Long = 0,
        usedAppTodayMs: Long = 0,
        dayBonus: Int = 0,
        appBonus: Int = 0,
    ) = Enforcement.verdict(rules, pkg, day, minuteOfDay, usedTodayMs, usedAppTodayMs, dayBonus, appBonus)

    @Test
    fun `no rules means allow`() {
        assertEquals(Enforcement.Verdict.Allow, verdict(ChildRules(), "com.app"))
    }

    @Test
    fun `blocked app wins over everything`() {
        val rules = ChildRules(appRules = mapOf("com.app" to AppRule(blocked = true)))
        assertEquals(Enforcement.Verdict.Block(Enforcement.BlockReason.AppBlocked), verdict(rules, "com.app"))
    }

    @Test
    fun `quiet hours block inside interval and allow outside`() {
        // Wraps midnight, every day, covers com.app only.
        val rules = ChildRules(quietHours = listOf(QuietInterval(22 * 60, 7 * 60, packages = listOf("com.app"))))
        assertEquals(Enforcement.Verdict.Block(Enforcement.BlockReason.QuietHours), verdict(rules, "com.app", minuteOfDay = 23 * 60))
        assertEquals(Enforcement.Verdict.Block(Enforcement.BlockReason.QuietHours), verdict(rules, "com.app", minuteOfDay = 6 * 60))
        assertEquals(Enforcement.Verdict.Allow, verdict(rules, "com.app", minuteOfDay = 12 * 60))
    }

    @Test
    fun `a schedule closes only the apps picked for it`() {
        val sleep = QuietInterval.SLEEP.copy(packages = listOf("com.game", "com.video"))
        val rules = ChildRules(quietHours = listOf(sleep))
        assertEquals(Enforcement.Verdict.Block(Enforcement.BlockReason.QuietHours), verdict(rules, "com.game", minuteOfDay = 23 * 60))
        assertEquals(Enforcement.Verdict.Block(Enforcement.BlockReason.QuietHours), verdict(rules, "com.video", minuteOfDay = 23 * 60))
        // Not on the list → the schedule does not touch it, even though it is active.
        assertEquals(Enforcement.Verdict.Allow, verdict(rules, "com.browser", minuteOfDay = 23 * 60))
        assertTrue(rules.inQuietHours(monday, 23 * 60))
        assertEquals(sleep, rules.scheduleBlocking("com.game", monday, 23 * 60))
        assertNull(rules.scheduleBlocking("com.browser", monday, 23 * 60))
    }

    @Test
    fun `a schedule with no apps blocks nothing`() {
        // Legacy documents (before per-app schedules) and half-finished ones: never the whole phone.
        val rules = ChildRules(quietHours = listOf(QuietInterval(0, 24 * 60)))
        assertEquals(Enforcement.Verdict.Allow, verdict(rules, "com.app", minuteOfDay = 12 * 60))
    }

    @Test
    fun `two schedules cover different apps`() {
        val rules =
            ChildRules(
                quietHours =
                listOf(
                    QuietInterval.STUDY.copy(packages = listOf("com.game")), // 08:00–16:00 Mon–Fri
                    QuietInterval.SLEEP.copy(packages = listOf("com.video")), // 21:00–07:00 daily
                ),
            )
        assertEquals(Enforcement.Verdict.Block(Enforcement.BlockReason.QuietHours), verdict(rules, "com.game", minuteOfDay = 10 * 60))
        assertEquals(Enforcement.Verdict.Allow, verdict(rules, "com.video", minuteOfDay = 10 * 60))
        assertEquals(Enforcement.Verdict.Allow, verdict(rules, "com.game", minuteOfDay = 23 * 60))
        assertEquals(Enforcement.Verdict.Block(Enforcement.BlockReason.QuietHours), verdict(rules, "com.video", minuteOfDay = 23 * 60))
    }

    @Test
    fun `schedule applies only on its days and a midnight wrap belongs to the day it starts`() {
        val study = QuietInterval.STUDY // 08:00–16:00, Mon–Fri
        assertTrue(study.isActive(friday, 9 * 60))
        assertFalse(study.isActive(saturday, 9 * 60))

        val fridayNight = QuietInterval(22 * 60, 7 * 60, name = "Пятница", days = listOf(friday))
        assertTrue(fridayNight.isActive(friday, 23 * 60)) // evening part on Friday
        assertTrue(fridayNight.isActive(saturday, 6 * 60)) // morning part spills into Saturday
        assertFalse(fridayNight.isActive(saturday, 23 * 60)) // Saturday evening is not listed
        assertFalse(fridayNight.isActive(friday, 6 * 60)) // Friday morning belongs to Thursday's night
        assertTrue(QuietInterval(22 * 60, 7 * 60, days = listOf(sunday)).isActive(monday, 3 * 60)) // wraps the week
    }

    @Test
    fun `disabled schedule never blocks`() {
        val rules = ChildRules(quietHours = listOf(QuietInterval.SLEEP.copy(enabled = false, packages = listOf("com.app"))))
        assertEquals(Enforcement.Verdict.Allow, verdict(rules, "com.app", minuteOfDay = 23 * 60))
    }

    @Test
    fun `per-app limit blocks that app only`() {
        val rules = ChildRules(appRules = mapOf("com.game" to AppRule(dailyLimitMinutes = 30)))
        assertEquals(
            Enforcement.Verdict.Block(Enforcement.BlockReason.AppLimit),
            verdict(rules, "com.game", usedTodayMs = 31 * minute, usedAppTodayMs = 30 * minute),
        )
        assertEquals(Enforcement.Verdict.Allow, verdict(rules, "com.other", usedTodayMs = 31 * minute))
    }

    @Test
    fun `always-allowed app is never blocked, even past the daily limit or in quiet hours`() {
        val rules =
            ChildRules(
                dailyLimitMinutes = 60,
                quietHours = listOf(QuietInterval(0, 24 * 60, packages = listOf("com.dialer", "com.other"))), // all day quiet
                appRules = mapOf("com.dialer" to AppRule(alwaysAllowed = true)),
            )
        assertEquals(Enforcement.Verdict.Allow, verdict(rules, "com.dialer", minuteOfDay = 120, usedTodayMs = 999 * minute))
        // A normal app is still blocked under the same rules.
        assertEquals(Enforcement.Verdict.Block(Enforcement.BlockReason.QuietHours), verdict(rules, "com.other", minuteOfDay = 120))
    }

    @Test
    fun `daily limit blocks every app when exhausted`() {
        val rules = ChildRules(dailyLimitMinutes = 120)
        assertEquals(
            Enforcement.Verdict.Block(Enforcement.BlockReason.DailyLimit),
            verdict(rules, "com.any", usedTodayMs = 120 * minute, usedAppTodayMs = 5 * minute),
        )
        assertEquals(Enforcement.Verdict.Allow, verdict(rules, "com.any", usedTodayMs = 119 * minute, usedAppTodayMs = 5 * minute))
    }

    @Test
    fun `weekday limits override the legacy value and a null day is unlimited`() {
        val rules = ChildRules(dailyLimitMinutes = 60, weekdayLimits = listOf(120, 120, 120, 120, 120, 240, null))
        assertEquals(120, rules.limitFor(monday))
        assertEquals(240, rules.limitFor(saturday))
        assertNull(rules.limitFor(sunday))
        assertEquals(Enforcement.Verdict.Allow, verdict(rules, "com.any", day = monday, usedTodayMs = 90 * minute))
        assertEquals(
            Enforcement.Verdict.Block(Enforcement.BlockReason.DailyLimit),
            verdict(rules, "com.any", day = monday, usedTodayMs = 120 * minute),
        )
        assertEquals(Enforcement.Verdict.Allow, verdict(rules, "com.any", day = sunday, usedTodayMs = 999 * minute))
    }

    @Test
    fun `parent bonus extends today's limit`() {
        val rules = ChildRules(dailyLimitMinutes = 60)
        assertEquals(Enforcement.Verdict.Allow, verdict(rules, "com.any", usedTodayMs = 70 * minute, dayBonus = 15))
        assertEquals(
            Enforcement.Verdict.Block(Enforcement.BlockReason.DailyLimit),
            verdict(rules, "com.any", usedTodayMs = 75 * minute, dayBonus = 15),
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
