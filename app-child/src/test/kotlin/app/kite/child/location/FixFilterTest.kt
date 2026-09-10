package app.kite.child.location

import app.kite.core.platform.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FixFilterTest {
    private val home = 55.0 to 37.0
    private val degreesPerMeterLat = 1 / 111_320.0

    @Test
    fun `the first fix is a move`() {
        val filter = FixFilter()
        val decision = filter.offer(point(at = 1_000, accuracy = 8f), ageMs = 0)
        assertTrue(decision is FixDecision.Moved)
        assertEquals(1_000L, filter.current?.recordedAt)
    }

    @Test
    fun `a fix that is not newer than the last one is dropped`() {
        val filter = FixFilter(fix(at = 5_000, accuracy = 8f))
        assertEquals(FixDecision.Dropped, filter.offer(point(at = 5_000, accuracy = 8f, north = 300.0), ageMs = 0))
        assertEquals(FixDecision.Dropped, filter.offer(point(at = 4_000, accuracy = 8f, north = 300.0), ageMs = 0))
    }

    @Test
    fun `a cached fix older than two minutes is dropped`() {
        val filter = FixFilter()
        assertEquals(FixDecision.Dropped, filter.offer(point(at = 1_000, accuracy = 8f), ageMs = FixFilter.MAX_AGE_MS + 1))
    }

    @Test
    fun `a coarse fix whose circle covers the last precise point only confirms it`() {
        val filter = FixFilter(fix(at = 0, accuracy = 5f))
        val decision = filter.offer(point(at = 120_000, accuracy = 359f, north = 300.0), ageMs = 0)
        val stayed = decision as FixDecision.Stayed
        assertEquals(home.first, stayed.fix.latitude, 0.0)
        assertEquals(5f, stayed.fix.accuracyM)
        assertEquals(120_000L, stayed.fix.recordedAt)
    }

    @Test
    fun `a coarse fix far away at an impossible speed is an outlier`() {
        val filter = FixFilter(fix(at = 0, accuracy = 5f))
        assertEquals(FixDecision.Dropped, filter.offer(point(at = 1_500, accuracy = 359f, north = 600.0), ageMs = 0))
        assertEquals(home.first, filter.current?.latitude)
    }

    @Test
    fun `a coarse fix that only might mean a move is dropped while a precise one is recent`() {
        val filter = FixFilter(fix(at = 0, accuracy = 5f))
        assertEquals(FixDecision.Dropped, filter.offer(point(at = 120_000, accuracy = 359f, north = 600.0), ageMs = 0))
    }

    @Test
    fun `the same coarse fix is trusted once nothing precise came for ten minutes`() {
        val filter = FixFilter(fix(at = 0, accuracy = 5f))
        val decision = filter.offer(point(at = FixFilter.TRUST_COARSE_AFTER_MS, accuracy = 359f, north = 600.0), ageMs = 0)
        assertTrue(decision is FixDecision.Moved)
    }

    @Test
    fun `a coarse fix clearly beyond its own uncertainty is a move`() {
        val filter = FixFilter(fix(at = 0, accuracy = 5f))
        val decision = filter.offer(point(at = 240_000, accuracy = 150f, north = 900.0), ageMs = 0)
        assertTrue(decision is FixDecision.Moved)
    }

    @Test
    fun `a more accurate fix always moves`() {
        val filter = FixFilter(fix(at = 0, accuracy = 40f))
        val decision = filter.offer(point(at = 30_000, accuracy = 6f, north = 3.0), ageMs = 0)
        assertTrue(decision is FixDecision.Moved)
    }

    @Test
    fun `walking forty metres in half a minute is a move`() {
        val filter = FixFilter(fix(at = 0, accuracy = 10f))
        val decision = filter.offer(point(at = 30_000, accuracy = 12f, north = 40.0), ageMs = 0)
        assertTrue(decision is FixDecision.Moved)
    }

    @Test
    fun `a fix with no accuracy counts as a hundred metres`() {
        val filter = FixFilter(fix(at = 0, accuracy = 5f))
        val decision = filter.offer(point(at = 60_000, accuracy = null, north = 50.0), ageMs = 0)
        assertTrue(decision is FixDecision.Stayed)
    }

    private fun fix(at: Long, accuracy: Float) = AcceptedFix(home.first, home.second, accuracy, at)

    private fun point(at: Long, accuracy: Float?, north: Double = 0.0) = GeoPoint(
        latitude = home.first + north * degreesPerMeterLat,
        longitude = home.second,
        accuracyMeters = accuracy,
        timestampMillis = at,
    )
}
