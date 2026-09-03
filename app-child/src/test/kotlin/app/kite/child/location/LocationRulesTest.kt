package app.kite.child.location

import app.kite.core.location.TrailRemote
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two pure location rules the child device runs: distance, and which fixes become trail
 * points. Both decide what the parent sees, so they are covered here rather than on a phone.
 */
class LocationRulesTest {
    // ~500 m north, and ~10 m north, at this latitude.
    private val base = 55.0 to 37.0
    private val degreesFor500m = 0.0045
    private val degreesFor10m = 0.00009

    @Test
    fun `distance of a point to itself is zero`() {
        assertEquals(0.0, haversineMeters(base.first, base.second, base.first, base.second), 0.001)
    }

    @Test
    fun `one degree of latitude is about 111 kilometres`() {
        val meters = haversineMeters(0.0, 0.0, 1.0, 0.0)
        assertTrue("got $meters", meters in 110_500.0..111_500.0)
    }

    @Test
    fun `short northward hop measures in metres`() {
        val meters = haversineMeters(base.first, base.second, base.first + degreesFor500m, base.second)
        assertTrue("got $meters", meters in 480.0..520.0)
    }

    @Test
    fun `the first fix is always uploaded`() {
        val result = TrailThinning.select(listOf(candidate(1, at = 0)), anchor = null)
        assertEquals(listOf(1L), result.selected.map { it.id })
        assertEquals(0L, result.anchor?.recordedAt)
    }

    @Test
    fun `far in time but not in space is dropped`() {
        val anchor = TrailAnchor(base.first, base.second, recordedAt = 0)
        val later = candidate(2, at = TrailRemote.MIN_GAP_MS * 2, latOffset = degreesFor10m)
        val result = TrailThinning.select(listOf(later), anchor)
        assertTrue(result.selected.isEmpty())
        // The anchor does not move when nothing was selected.
        assertEquals(0L, result.anchor?.recordedAt)
    }

    @Test
    fun `far in space but not in time is dropped`() {
        val anchor = TrailAnchor(base.first, base.second, recordedAt = 0)
        val soon = candidate(3, at = 60_000, latOffset = degreesFor500m)
        assertTrue(TrailThinning.select(listOf(soon), anchor).selected.isEmpty())
    }

    @Test
    fun `far in both is uploaded and becomes the new anchor`() {
        val anchor = TrailAnchor(base.first, base.second, recordedAt = 0)
        val moved = candidate(4, at = TrailRemote.MIN_GAP_MS, latOffset = degreesFor500m)
        val result = TrailThinning.select(listOf(moved), anchor)
        assertEquals(listOf(4L), result.selected.map { it.id })
        assertEquals(TrailRemote.MIN_GAP_MS, result.anchor?.recordedAt)
        assertEquals(base.first + degreesFor500m, result.anchor?.latitude)
    }

    @Test
    fun `thinning measures from the last selected point, not the batch start`() {
        // A minute-by-minute walk: only every fifth minute clears both thresholds.
        val walk =
            (0..10).map { minute ->
                candidate(minute.toLong(), at = minute * 60_000L, latOffset = degreesFor500m * minute)
            }
        val result = TrailThinning.select(walk, anchor = null)
        assertEquals(listOf(0L, 5L, 10L), result.selected.map { it.id })
    }

    @Test
    fun `an empty batch keeps the anchor untouched`() {
        assertNull(TrailThinning.select(emptyList(), anchor = null).anchor)
    }

    private fun candidate(id: Long, at: Long, latOffset: Double = 0.0) = TrailCandidate(
        id = id,
        latitude = base.first + latOffset,
        longitude = base.second,
        accuracyM = 12f,
        recordedAt = at,
    )
}
