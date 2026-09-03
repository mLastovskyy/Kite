package app.kite.parent.location

import app.kite.core.location.TrailPoint
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RoutesTest {
    private val base = Instant.parse("2026-09-02T08:00:00Z")

    private fun point(minutes: Int, lat: Double, lon: Double) = TrailPoint(
        memberId = "m",
        familyId = "f",
        latitude = lat,
        longitude = lon,
        accuracyM = 10f,
        recordedAt = base.plusSeconds(minutes * 60L).toString(),
    )

    @Test
    fun `haversine matches a known distance`() {
        // One degree of latitude ≈ 111.2 km.
        val d = Routes.distanceMeters(55.0, 37.0, 56.0, 37.0)
        assertTrue(d in 111_000.0..111_500.0, "got $d")
    }

    @Test
    fun `a dwell of ten minutes within 100 m is one stop, moving points are not`() {
        val points =
            listOf(
                point(0, 55.7500, 37.6000),
                point(5, 55.7501, 37.6001), // ~13 m away
                point(12, 55.7500, 37.6002), // still inside 100 m, 12 min in
                point(20, 55.7600, 37.6200), // far away: moving
                point(25, 55.7700, 37.6400), // moving
            )
        val stops = Routes.detectStops(points)
        assertEquals(1, stops.size)
        val stop = stops.single()
        assertEquals(base.toEpochMilli(), stop.fromMs)
        assertEquals(base.plusSeconds(12 * 60).toEpochMilli(), stop.toMs)
        assertTrue(stop.latitude in 55.7499..55.7502)
    }

    @Test
    fun `a short pause is not a stop`() {
        val points = listOf(point(0, 55.75, 37.60), point(4, 55.7501, 37.6001), point(9, 55.76, 37.62))
        assertTrue(Routes.detectStops(points).isEmpty())
    }

    @Test
    fun `path length sums consecutive legs`() {
        val points = listOf(point(0, 55.0, 37.0), point(10, 56.0, 37.0), point(20, 57.0, 37.0))
        val meters = Routes.pathMeters(points)
        assertTrue(meters in 222_000.0..223_000.0, "got $meters")
    }

    @Test
    fun `day range is a half-open local day`() {
        val (from, to) = Routes.dayRange(0, java.time.ZoneId.of("UTC"))
        assertTrue(from.endsWith("T00:00:00Z"))
        assertTrue(to.endsWith("T00:00:00Z"))
        assertTrue(Instant.parse(to).isAfter(Instant.parse(from)))
    }
}
