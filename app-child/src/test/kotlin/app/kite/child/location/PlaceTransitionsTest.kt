package app.kite.child.location

import app.kite.core.location.Place
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaceTransitionsTest {
    private val home =
        Place(
            id = "home",
            familyId = "f",
            childMemberId = "c",
            name = "Дом",
            latitude = 55.0,
            longitude = 37.0,
            radiusM = 150,
        )
    private val degreesPerMeterLat = 1 / 111_320.0
    private val minute = 60_000L

    @Test
    fun `the first decisive fix seeds the state without an event`() {
        val step = PlaceTransitions.onFix(PlaceState(), home, fix(north = 20.0), nowMs = 0)
        assertEquals(PlaceState(inside = true), step.state)
        assertNull(step.entered)
    }

    @Test
    fun `a fix in the fuzzy ring changes nothing`() {
        val state = PlaceState(inside = true)
        assertEquals(state, PlaceTransitions.onFix(state, home, fix(north = 180.0), nowMs = 0).state)
    }

    @Test
    fun `a fix too coarse for the place is ignored`() {
        val state = PlaceState(inside = true)
        val step = PlaceTransitions.onFix(state, home, fix(north = 600.0, accuracy = 359f), nowMs = 0)
        assertEquals(state, step.state)
        assertNull(step.entered)
    }

    @Test
    fun `leaving is only a candidate until three minutes have passed`() {
        var state = PlaceState(inside = true)
        state = PlaceTransitions.onFix(state, home, fix(north = 260.0), nowMs = 0).state
        assertEquals(false, state.pendingInside)
        val early = PlaceTransitions.onFix(state, home, fix(north = 300.0), nowMs = 2 * minute)
        assertNull(early.entered)
        val confirmed = PlaceTransitions.onFix(early.state, home, fix(north = 340.0), nowMs = 3 * minute)
        assertEquals(false, confirmed.entered)
        assertEquals(0L, confirmed.at)
        assertEquals(PlaceState(inside = false), confirmed.state)
    }

    @Test
    fun `coming back inside cancels a pending exit`() {
        var state = PlaceState(inside = true)
        state = PlaceTransitions.onFix(state, home, fix(north = 260.0), nowMs = 0).state
        val step = PlaceTransitions.onFix(state, home, fix(north = 40.0), nowMs = minute)
        assertEquals(PlaceState(inside = true), step.state)
        assertNull(step.entered)
    }

    @Test
    fun `arriving is confirmed after two minutes`() {
        var state = PlaceState(inside = false)
        state = PlaceTransitions.onFix(state, home, fix(north = 100.0), nowMs = 0).state
        val step = PlaceTransitions.onFix(state, home, fix(north = 80.0), nowMs = 2 * minute)
        assertEquals(true, step.entered)
        assertEquals(PlaceState(inside = true), step.state)
    }

    @Test
    fun `a phone that went quiet is settled by the clock`() {
        val pending = PlaceTransitions.onFix(PlaceState(inside = false), home, fix(north = 100.0), nowMs = 0).state
        assertEquals(PlaceTransitions.ENTER_CONFIRM_MS, pending.deadline)
        assertNull(PlaceTransitions.settle(pending, nowMs = minute).entered)
        val step = PlaceTransitions.settle(pending, nowMs = 2 * minute)
        assertEquals(true, step.entered)
        assertNull(step.state.deadline)
    }

    @Test
    fun `fixes flipping in and out every second produce no events at all`() {
        var state = PlaceState(inside = true)
        var events = 0
        for (i in 0 until 20) {
            val north = if (i % 2 == 0) 600.0 else 20.0
            val step = PlaceTransitions.onFix(state, home, fix(north = north, accuracy = 30f), nowMs = i * 1_500L)
            state = step.state
            if (step.entered != null) events++
        }
        assertEquals(0, events)
        assertEquals(PlaceState(inside = true), state)
    }

    private fun fix(north: Double, accuracy: Float = 10f) = AcceptedFix(
        latitude = home.latitude + north * degreesPerMeterLat,
        longitude = home.longitude,
        accuracyM = accuracy,
        recordedAt = 0L,
    )
}
