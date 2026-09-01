package app.kite.core.usage

import app.kite.core.usage.ForegroundIntervals.Carry
import app.kite.core.usage.ForegroundIntervals.Ev
import app.kite.core.usage.ForegroundIntervals.Interval
import app.kite.core.usage.ForegroundIntervals.Kind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ForegroundIntervalsTest {
    @Test
    fun `resume then pause makes one interval`() {
        val out =
            ForegroundIntervals.reduce(
                listOf(Ev(Kind.Resumed, "a", 100), Ev(Kind.Paused, "a", 400)),
                initial = null,
                endAt = 1000,
            )
        assertEquals(listOf(Interval("a", 100, 400)), out.intervals)
        assertNull(out.carry)
    }

    @Test
    fun `app switch closes the previous app at the switch moment`() {
        val out =
            ForegroundIntervals.reduce(
                listOf(Ev(Kind.Resumed, "a", 100), Ev(Kind.Resumed, "b", 300), Ev(Kind.Paused, "b", 500)),
                initial = null,
                endAt = 1000,
            )
        assertEquals(listOf(Interval("a", 100, 300), Interval("b", 300, 500)), out.intervals)
    }

    @Test
    fun `still-open session is closed at endAt and carried forward`() {
        val out =
            ForegroundIntervals.reduce(
                listOf(Ev(Kind.Resumed, "a", 100)),
                initial = null,
                endAt = 900,
            )
        assertEquals(listOf(Interval("a", 100, 900)), out.intervals)
        assertEquals(Carry("a", 900), out.carry)
    }

    @Test
    fun `carry from previous run continues the session`() {
        val out =
            ForegroundIntervals.reduce(
                listOf(Ev(Kind.Paused, "a", 250)),
                initial = Carry("a", 0),
                endAt = 1000,
            )
        assertEquals(listOf(Interval("a", 0, 250)), out.intervals)
        assertNull(out.carry)
    }

    @Test
    fun `screen off closes whatever is current`() {
        val out =
            ForegroundIntervals.reduce(
                listOf(Ev(Kind.Resumed, "a", 100), Ev(Kind.ScreenOff, null, 300)),
                initial = null,
                endAt = 1000,
            )
        assertEquals(listOf(Interval("a", 100, 300)), out.intervals)
        assertNull(out.carry)
    }

    @Test
    fun `pause of a different app and duplicate resume are ignored`() {
        val out =
            ForegroundIntervals.reduce(
                listOf(
                    Ev(Kind.Resumed, "a", 100),
                    Ev(Kind.Paused, "b", 200),
                    Ev(Kind.Resumed, "a", 300),
                    Ev(Kind.Paused, "a", 500),
                ),
                initial = null,
                endAt = 1000,
            )
        assertEquals(listOf(Interval("a", 100, 500)), out.intervals)
    }
}
