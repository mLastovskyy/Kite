package app.kite.parent.location

import app.kite.core.location.TrailPoint
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** A stretch where the child stayed within [STOP_RADIUS_M] for at least [MIN_DWELL_MS]. */
data class Stop(val latitude: Double, val longitude: Double, val fromMs: Long, val toMs: Long) {
    val dwellMs: Long get() = toMs - fromMs
}

/** Pure route maths for the «Маршруты» view — testable, no Android types. */
object Routes {
    const val STOP_RADIUS_M = 100.0
    const val MIN_DWELL_MS = 10 * 60 * 1000L

    /** Great-circle distance in metres. */
    fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * r * asin(sqrt(a))
    }

    /** Total path length over consecutive points, metres. */
    fun pathMeters(points: List<TrailPoint>): Double =
        points.zipWithNext().sumOf { (a, b) -> distanceMeters(a.latitude, a.longitude, b.latitude, b.longitude) }

    /**
     * Groups consecutive points that stay within [STOP_RADIUS_M] of the group's first point;
     * a group lasting at least [MIN_DWELL_MS] is a stop, centred on the group's mean.
     */
    fun detectStops(points: List<TrailPoint>): List<Stop> {
        if (points.isEmpty()) return emptyList()
        val stops = mutableListOf<Stop>()
        var group = mutableListOf(points.first())
        fun flush() {
            val from = epochMs(group.first().recordedAt)
            val to = epochMs(group.last().recordedAt)
            if (to - from >= MIN_DWELL_MS) {
                stops += Stop(group.map { it.latitude }.average(), group.map { it.longitude }.average(), from, to)
            }
        }
        for (point in points.drop(1)) {
            val anchor = group.first()
            if (distanceMeters(anchor.latitude, anchor.longitude, point.latitude, point.longitude) <= STOP_RADIUS_M) {
                group += point
            } else {
                flush()
                group = mutableListOf(point)
            }
        }
        flush()
        return stops
    }

    fun epochMs(iso: String): Long = runCatching { Instant.parse(iso).toEpochMilli() }.getOrDefault(0L)

    /** [dayOffset] 0 = today, 1 = yesterday … as [from, to) ISO instants in the local zone. */
    fun dayRange(dayOffset: Int, zone: ZoneId = ZoneId.systemDefault()): Pair<String, String> {
        val day = LocalDate.now(zone).minusDays(dayOffset.toLong())
        val from = day.atStartOfDay(zone).toInstant()
        val to = day.plusDays(1).atStartOfDay(zone).toInstant()
        return from.toString() to to.toString()
    }
}
