package app.kite.child.status

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The number the child sees on the home hero. It must agree with what enforcement does:
 * bonus minutes count, and an over-spent day shows zero rather than a negative.
 */
class TodaySummaryTest {
    private fun today(usedMinutes: Int, limitMinutes: Int?, bonusMinutes: Int = 0) = TodaySummary.Today(
        usedMs = usedMinutes * 60_000L,
        limitMinutes = limitMinutes,
        bonusMinutes = bonusMinutes,
        hourly = emptyList(),
        apps = emptyList(),
    )

    @Test
    fun `no limit means nothing to count down`() {
        assertNull(today(usedMinutes = 90, limitMinutes = null).remainingMinutes)
    }

    @Test
    fun `remaining subtracts what was used`() {
        assertEquals(30, today(usedMinutes = 30, limitMinutes = 60).remainingMinutes)
    }

    @Test
    fun `granted bonus minutes are part of the allowance`() {
        assertEquals(45, today(usedMinutes = 30, limitMinutes = 60, bonusMinutes = 15).remainingMinutes)
    }

    @Test
    fun `an exhausted day never goes negative`() {
        assertEquals(0, today(usedMinutes = 200, limitMinutes = 60).remainingMinutes)
    }
}
