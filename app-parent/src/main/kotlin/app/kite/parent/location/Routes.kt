package app.kite.parent.location

import app.kite.core.location.TrailPoint
import app.kite.core.util.Timestamps
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** A stretch where the child stayed within [Routes.ALLOWED_RADIUS_M] for at least [Routes.MIN_DWELL_MS]. */
data class Stop(val latitude: Double, val longitude: Double, val fromMs: Long, val toMs: Long) {
    val dwellMs: Long get() = toMs - fromMs
}

/** Pure route maths for the «Маршруты» view — testable, no Android types. */
object Routes {
    /**
     * «Допустимый радиус»: within it two fixes mean the same place. One number for every job —
     * how many points the map draws, when standing still counts as standing still, and what
     * counts as two fixes agreeing in [denoise] — so the route and the stop list can never
     * disagree. 75 m on 07.09.2026, then 100, then 200 — the owner's call each time
     * (09.09.2026).
     */
    const val ALLOWED_RADIUS_M = 200.0

    /** The location did not leave that radius for this long — that is a stop. */
    const val MIN_DWELL_MS = 15 * 60 * 1000L

    /** Further than this from the last trusted fix, the phone makes a claim, not a report. */
    const val TRUSTED_JUMP_M = 500.0

    /** ≈120 км/ч — faster than a child crosses a city, so such a fix has to be backed up. */
    const val MAX_SPEED_MPS = 33.0

    /** Worse than this and the fix came from cell towers or Wi-Fi: it wanders on its own. */
    const val SUSPECT_ACCURACY_M = 150f

    /** How many fixes must land in one spot before a far claim is drawn (owner, 08.09.2026). */
    const val CONFIRMATIONS = 2

    /** How far ahead to look for the trail coming back where it was. */
    const val RETURN_LOOKAHEAD = 3

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
     * Drops the fixes nothing backs up. Indoors — and on every cell-tower fix — the phone puts
     * the child a kilometre away, once, and the day grows a spike across the city (owner,
     * 08.09.2026). A fix that lands far from where the child just was is drawn only when the
     * fixes around it agree: either [CONFIRMATIONS] of them land in the same spot, or the trail
     * carries on from there instead of coming straight back to where it started.
     *
     * Nothing is deleted — the raw points stay in Room on the child and in `location_trail` on
     * the server. This is only about what the map draws.
     *
     * The price: an excursion the trail cannot confirm yet — the very last fix of a drive, where
     * every fix is kilometres from the one before — waits for the next fix instead of being drawn
     * at once. The live marker comes from `device_location` and is not affected.
     */
    fun denoise(points: List<TrailPoint>): List<TrailPoint> {
        if (points.size <= CONFIRMATIONS) return points
        val kept = mutableListOf<TrailPoint>()
        var anchor: TrailPoint? = null
        var index = 0
        while (index < points.size) {
            val point = points[index]
            var next = index + 1
            while (next < points.size && metersBetween(point, points[next]) <= ALLOWED_RADIUS_M) next++
            val here = anchor
            val questionable =
                when {
                    here != null -> isSuspect(here, point)
                    // Nothing trusted yet: the day's first fix can only be judged by physics.
                    next < points.size -> isVague(point) || isImpossible(point, points[next])
                    else -> false
                }
            val backed =
                !questionable ||
                    next - index >= CONFIRMATIONS ||
                    (here != null && next < points.size && !comesBack(here, points, next))
            if (backed) {
                for (i in index until next) kept += points[i]
                anchor = points[next - 1]
            }
            index = next
        }
        return kept
    }

    /**
     * A fix worth questioning: it left the allowed radius, and it is either a long way off or
     * comes from a source that wanders by itself. A step across the yard is never questioned —
     * [simplify] swallows it anyway, and a walk to the shop and back is not noise.
     */
    private fun isSuspect(from: TrailPoint, to: TrailPoint): Boolean {
        val meters = metersBetween(from, to)
        return meters > ALLOWED_RADIUS_M && (isVague(to) || meters > TRUSTED_JUMP_M)
    }

    /** Cell towers and Wi-Fi report hundreds of metres of accuracy and drift within them. */
    private fun isVague(point: TrailPoint): Boolean = (point.accuracyM ?: 0f) > SUSPECT_ACCURACY_M

    /** No history to lean on: only a trip nobody could have made gives the fix away. */
    private fun isImpossible(from: TrailPoint, to: TrailPoint): Boolean {
        val meters = metersBetween(from, to)
        if (meters <= TRUSTED_JUMP_M) return false
        val seconds = (epochMs(to.recordedAt) - epochMs(from.recordedAt)) / 1000.0
        return seconds <= 0.0 || meters / seconds > MAX_SPEED_MPS
    }

    /** The trail returning to [anchor] right after an excursion is what tells noise from a walk. */
    private fun comesBack(anchor: TrailPoint, points: List<TrailPoint>, from: Int): Boolean =
        (from until minOf(points.size, from + RETURN_LOOKAHEAD)).any { metersBetween(anchor, points[it]) <= ALLOWED_RADIUS_M }

    private fun metersBetween(a: TrailPoint, b: TrailPoint): Double = distanceMeters(a.latitude, a.longitude, b.latitude, b.longitude)

    /**
     * Groups consecutive points that stay within [ALLOWED_RADIUS_M] of the group's first
     * point; a group lasting at least [MIN_DWELL_MS] is a stop, centred on the group's mean.
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
            if (distanceMeters(anchor.latitude, anchor.longitude, point.latitude, point.longitude) <= ALLOWED_RADIUS_M) {
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
     * is kept when it is further from the last kept one than [ALLOWED_RADIUS_M] or than its own
     * accuracy, whichever is larger. Only the first point is unconditional: several fixes inside
     * one radius are the same place, and the last of them is not drawn either (owner, 08.09.2026)
     * — a day spent at home is one dot, not a knot.
     *
     * For drawing and for the distance only — [detectStops] needs every raw point, or the two
     * hours spent in one place would collapse into a single fix and stop being a stop.
     */
    fun simplify(points: List<TrailPoint>, minMeters: Double = ALLOWED_RADIUS_M): List<TrailPoint> {
        if (points.size < 2) return points
        val kept = mutableListOf(points.first())
        for (point in points.drop(1)) {
            val last = kept.last()
            val step = maxOf(minMeters, point.accuracyM?.toDouble() ?: 0.0)
            if (metersBetween(last, point) >= step) kept += point
        }
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
