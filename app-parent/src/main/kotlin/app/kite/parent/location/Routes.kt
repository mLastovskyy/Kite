package app.kite.parent.location

import app.kite.core.location.TrailPoint
import app.kite.core.util.Timestamps
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

    /** Below this a new fix says nothing new: it is inside the previous one's own error. */
    const val MIN_STEP_M = 30.0

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

    /**
     * Drops points that only repeat where the phone already was. A phone lying on a table
     * still reports every few minutes, and its own accuracy makes those fixes wander — drawn
     * as they come, the day turns into a scribble around the flat (owner, 07.09.2026). A point
     * is kept when it is further from the last kept one than [MIN_STEP_M] or than its own
     * accuracy, whichever is larger; the first and the last point are always kept, so the day
     * still starts and ends where it really did.
     *
     * For drawing and for the distance only — [detectStops] needs every raw point, or the two
     * hours spent in one place would collapse into a single fix and stop being a stop.
     */
    fun simplify(points: List<TrailPoint>, minMeters: Double = MIN_STEP_M): List<TrailPoint> {
        if (points.size < 3) return points
        val kept = mutableListOf(points.first())
        for (point in points.subList(1, points.size - 1)) {
            val last = kept.last()
            val step = maxOf(minMeters, point.accuracyM?.toDouble() ?: 0.0)
            if (distanceMeters(last.latitude, last.longitude, point.latitude, point.longitude) >= step) kept += point
        }
        kept += points.last()
        return kept
    }

    fun epochMs(iso: String): Long = Timestamps.epochMs(iso)

    /** [dayOffset] 0 = today, 1 = yesterday … as [from, to) ISO instants in the local zone. */
    fun dayRange(dayOffset: Int, zone: ZoneId = ZoneId.systemDefault()): Pair<String, String> {
        val day = LocalDate.now(zone).minusDays(dayOffset.toLong())
        val from = day.atStartOfDay(zone).toInstant()
        val to = day.plusDays(1).atStartOfDay(zone).toInstant()
        return from.toString() to to.toString()
    }
}
