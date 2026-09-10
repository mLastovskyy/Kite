package app.kite.child.location

import app.kite.core.location.Place
import kotlinx.serialization.Serializable

@Serializable
data class PlaceState(val inside: Boolean? = null, val pendingInside: Boolean? = null, val pendingSince: Long = 0L) {
    val deadline: Long? get() = pendingInside?.let { pendingSince + PlaceTransitions.confirmMs(it) }
}

data class PlaceStep(val state: PlaceState, val entered: Boolean? = null, val at: Long = 0L)

object PlaceTransitions {
    fun onFix(state: PlaceState, place: Place, fix: AcceptedFix, nowMs: Long): PlaceStep {
        if (fix.accuracyM > maxOf(place.radiusM.toFloat(), MIN_USABLE_ACCURACY_M)) return PlaceStep(state)
        val distance = haversineMeters(fix.latitude, fix.longitude, place.latitude, place.longitude)
        val exitEdge = place.radiusM + maxOf(EXIT_MARGIN_M, fix.accuracyM.toDouble())
        val observed =
            when {
                distance < place.radiusM -> true
                distance > exitEdge -> false
                else -> return PlaceStep(state)
            }
        val inside = state.inside ?: return PlaceStep(PlaceState(inside = observed))
        if (observed == inside) return PlaceStep(PlaceState(inside = inside))
        if (state.pendingInside != observed) return PlaceStep(state.copy(pendingInside = observed, pendingSince = nowMs))
        return settle(state, nowMs)
    }

    fun settle(state: PlaceState, nowMs: Long): PlaceStep {
        val pending = state.pendingInside ?: return PlaceStep(state)
        if (nowMs < state.pendingSince + confirmMs(pending)) return PlaceStep(state)
        return PlaceStep(PlaceState(inside = pending), entered = pending, at = state.pendingSince)
    }

    fun confirmMs(inside: Boolean): Long = if (inside) ENTER_CONFIRM_MS else EXIT_CONFIRM_MS

    const val MIN_USABLE_ACCURACY_M = 100f
    const val EXIT_MARGIN_M = 50.0
    const val ENTER_CONFIRM_MS = 2 * 60_000L
    const val EXIT_CONFIRM_MS = 3 * 60_000L
}
