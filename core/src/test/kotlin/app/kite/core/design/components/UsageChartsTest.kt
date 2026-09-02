package app.kite.core.design.components

import org.junit.Assert.assertEquals
import org.junit.Test

/** The one duration format the whole product uses — both apps read it off the same charts. */
class UsageChartsTest {
    @Test
    fun `formats minutes below an hour`() {
        assertEquals("0 мин", formatUsageMs(0))
        assertEquals("0 мин", formatUsageMs(59_000))
        assertEquals("56 мин", formatUsageMs(56 * 60_000L))
    }

    @Test
    fun `formats whole and partial hours`() {
        assertEquals("1 ч", formatUsageMs(60 * 60_000L))
        assertEquals("2 ч 14 мин", formatUsageMs((2 * 60 + 14) * 60_000L))
        assertEquals("3 ч", formatUsageMs((3 * 60) * 60_000L + 59_000))
    }

    @Test
    fun `rank colours fall back to grey past the top three`() {
        assertEquals(UsageRankColors[0], usageRankColor(0))
        assertEquals(UsageRankColors[2], usageRankColor(2))
        assertEquals(UsageRestColor, usageRankColor(3))
        assertEquals(UsageRestColor, usageRankColor(99))
    }
}
