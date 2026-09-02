package app.kite.child.setup

import org.junit.Assert.assertEquals
import org.junit.Test

/** The «≈ N мин осталось» estimate: 30 s per remaining step, rounded up, never «0 мин». */
class SetupProgressTest {
    @Test
    fun `rounds up to whole minutes`() {
        assertEquals(1, minutesLeft(1))
        assertEquals(1, minutesLeft(2))
        assertEquals(2, minutesLeft(3))
        assertEquals(2, minutesLeft(4))
        assertEquals(6, minutesLeft(12))
    }

    @Test
    fun `never promises zero minutes`() {
        assertEquals(1, minutesLeft(0))
        assertEquals(1, minutesLeft(-3))
    }
}
