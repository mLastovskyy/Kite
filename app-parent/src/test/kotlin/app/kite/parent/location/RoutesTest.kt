package app.kite.parent.location

import app.kite.core.location.TrailPoint
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RoutesTest {
    private val base = Instant.parse("2026-09-02T08:00:00Z")

    private fun point(minutes: Int, lat: Double, lon: Double, accuracy: Float = 10f) = TrailPoint(
        memberId = "m",
        familyId = "f",
        latitude = lat,
        longitude = lon,
        accuracyM = accuracy,
        recordedAt = base.plusSeconds(minutes * 60L).toString(),
    )

    @Test
    fun `haversine matches a known distance`() {
        // One degree of latitude ≈ 111.2 km.
        val d = Routes.distanceMeters(55.0, 37.0, 56.0, 37.0)
        assertTrue(d in 111_000.0..111_500.0, "got $d")
    }

    @Test
    fun `jitter around one spot collapses to a single point`() {
        val points =
            listOf(
                point(0, 55.7500, 37.6000),
                point(5, 55.75005, 37.60005), // ~7 m: inside the accuracy, says nothing new
                point(10, 55.74998, 37.59996), // ~5 m
                point(15, 55.75002, 37.60003), // ~4 m
            )
        assertEquals(1, Routes.simplify(points).size)
    }

    @Test
    fun `real movement is kept point by point`() {
        val points = (0..4).map { point(it * 5, 55.7500 + it * 0.002, 37.6000) } // ~220 m steps
        assertEquals(points.size, Routes.simplify(points).size)
    }

    @Test
    fun `the day still starts where it did, and standing still adds nothing after it`() {
        val points =
            listOf(
                point(0, 55.7500, 37.6000),
                point(5, 55.75004, 37.60004),
                point(10, 55.75002, 37.60001),
                point(15, 55.7530, 37.6000), // ~330 m: a real step, and it ends the day
            )
        val simplified = Routes.simplify(points)
        assertEquals(points.first(), simplified.first())
        assertEquals(points.last(), simplified.last())
        assertEquals(2, simplified.size)
    }

    @Test
    fun `fifteen minutes inside the allowed radius is one stop, moving points are not`() {
        val points =
            listOf(
                point(0, 55.7500, 37.6000),
                point(5, 55.7501, 37.6001), // ~13 m away
                point(12, 55.7500, 37.6002), // still inside the radius
                point(17, 55.75005, 37.60015), // 17 minutes in: a stop
                point(25, 55.7600, 37.6200), // far away: moving
                point(30, 55.7700, 37.6400), // moving
            )
        val stops = Routes.detectStops(points)
        assertEquals(1, stops.size)
        val stop = stops.single()
        assertEquals(base.toEpochMilli(), stop.fromMs)
        assertEquals(base.plusSeconds(17 * 60).toEpochMilli(), stop.toMs)
        assertTrue(stop.latitude in 55.7499..55.7502)
    }

    @Test
    fun `standing still for less than fifteen minutes is not a stop`() {
        val points =
            listOf(
                point(0, 55.75, 37.60),
                point(6, 55.7501, 37.6001),
                point(13, 55.75005, 37.60005), // 13 minutes: not yet
                point(20, 55.76, 37.62),
            )
        assertTrue(Routes.detectStops(points).isEmpty())
    }

    @Test
    fun `a lone fix across the city is not drawn`() {
        val home = 55.7500 to 37.6000
        val points =
            listOf(
                point(0, home.first, home.second),
                point(2, 55.75002, 37.60003),
                point(4, 55.7900, 37.6600), // ≈ 6 км за две минуты и обратно: погрешность GPS
                point(6, 55.75001, 37.60002),
                point(8, 55.74999, 37.59998),
            )
        val trusted = Routes.denoise(points)
        assertEquals(4, trusted.size)
        assertTrue(trusted.none { it.latitude > 55.76 })
    }

    @Test
    fun `two fixes from the same far spot are believed`() {
        val points =
            listOf(
                point(0, 55.7500, 37.6000),
                point(2, 55.75002, 37.60003),
                point(4, 55.7900, 37.6600),
                point(6, 55.79003, 37.66004), // второй оттуда же — значит, ребёнок правда там
                point(8, 55.7500, 37.6000),
            )
        val trusted = Routes.denoise(points)
        assertTrue(trusted.containsAll(points.subList(2, 4)), "далёкая точка подтверждена второй")
        // Возвращение домой — последний замер, подтвердить его пока нечем: придёт со следующим.
        assertEquals(points.dropLast(1), trusted)
    }

    @Test
    fun `driving away is not mistaken for noise`() {
        // ≈ 2 км за две минуты — 60 км/ч, обычная дорога; ни одна точка не возвращается назад.
        val points = (0..5).map { point(it * 2, 55.7000 + it * 0.018, 37.6000) }
        val trusted = Routes.denoise(points)
        // Последняя точка ждёт подтверждения — маршрут отстаёт на один замер, но не врёт.
        assertEquals(points.dropLast(1), trusted)
    }

    @Test
    fun `a vague fix that wanders and comes back is dropped`() {
        val points =
            listOf(
                point(0, 55.7500, 37.6000),
                point(2, 55.7530, 37.6040, accuracy = 1200f), // вышка связи: ≈ 400 м в сторону
                point(4, 55.75001, 37.60001),
                point(6, 55.74998, 37.59997),
            )
        val trusted = Routes.denoise(points)
        assertEquals(3, trusted.size)
        assertTrue(trusted.none { it.accuracyM == 1200f })
    }

    @Test
    fun `a walk to the shop and back stays on the map`() {
        val points =
            listOf(
                point(0, 55.7500, 37.6000),
                point(2, 55.7515, 37.6000), // ≈ 170 м: обычный шаг, вопросов не вызывает
                point(4, 55.7527, 37.6000), // ≈ 300 м от дома
                point(6, 55.7515, 37.6000),
                point(8, 55.7500, 37.6000),
            )
        assertEquals(points.size, Routes.denoise(points).size)
    }

    @Test
    fun `one radius drives the stops and the drawn points alike`() {
        assertEquals(Routes.ALLOWED_RADIUS_M, 200.0, 0.0)
        assertEquals(15 * 60 * 1000L, Routes.MIN_DWELL_MS)
    }

    @Test
    fun `fixes inside the radius are one place, drawn once and stood in once`() {
        val points =
            listOf(
                point(0, 55.7500, 37.6000),
                point(4, 55.7507, 37.6000), // ≈ 78 м: раньше рисовалась второй точкой
                point(8, 55.74994, 37.60008), // ≈ 9 м
                point(12, 55.7508, 37.6001), // ≈ 89 м от первой
                point(16, 55.7504, 37.6000), // ≈ 44 м — и шестнадцатая минута на одном месте
            )
        assertEquals(1, Routes.simplify(points).size)
        // …and a quarter of an hour inside those hundred metres is one stop.
        assertEquals(1, Routes.detectStops(points).size)
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
