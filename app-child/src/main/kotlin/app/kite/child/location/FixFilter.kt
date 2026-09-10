package app.kite.child.location

import app.kite.core.platform.GeoPoint

data class AcceptedFix(val latitude: Double, val longitude: Double, val accuracyM: Float, val recordedAt: Long)

sealed interface FixDecision {
    data class Moved(val fix: AcceptedFix) : FixDecision

    data class Stayed(val fix: AcceptedFix) : FixDecision

    data object Dropped : FixDecision
}

class FixFilter(initial: AcceptedFix? = null) {
    var current: AcceptedFix? = initial
        private set

    @Synchronized
    fun seed(fix: AcceptedFix) {
        if (current == null) current = fix
    }

    @Synchronized
    fun offer(point: GeoPoint, ageMs: Long): FixDecision {
        val accuracy = point.accuracyMeters ?: DEFAULT_ACCURACY_M
        if (accuracy > USELESS_ACCURACY_M || ageMs > MAX_AGE_MS) return FixDecision.Dropped
        val previous = current ?: return move(point, accuracy)
        if (point.timestampMillis <= previous.recordedAt) return FixDecision.Dropped
        if (accuracy < previous.accuracyM) return move(point, accuracy)
        val distance = haversineMeters(previous.latitude, previous.longitude, point.latitude, point.longitude)
        if (distance <= accuracy) return stay(point.timestampMillis)
        val elapsedMs = point.timestampMillis - previous.recordedAt
        if ((distance - accuracy) * 1000 / elapsedMs > MAX_SPEED_MPS) return FixDecision.Dropped
        val uncertain = accuracy > COARSE_ACCURACY_M && distance < 2 * accuracy && elapsedMs < TRUST_COARSE_AFTER_MS
        if (uncertain) return FixDecision.Dropped
        return move(point, accuracy)
    }

    private fun move(point: GeoPoint, accuracy: Float): FixDecision {
        val fix = AcceptedFix(point.latitude, point.longitude, accuracy, point.timestampMillis)
        current = fix
        return FixDecision.Moved(fix)
    }

    private fun stay(at: Long): FixDecision {
        val fix = checkNotNull(current).copy(recordedAt = at)
        current = fix
        return FixDecision.Stayed(fix)
    }

    companion object {
        const val DEFAULT_ACCURACY_M = 100f
        const val USELESS_ACCURACY_M = 2_000f
        const val COARSE_ACCURACY_M = 100f
        const val MAX_AGE_MS = 2 * 60_000L
        const val MAX_SPEED_MPS = 60.0
        const val TRUST_COARSE_AFTER_MS = 10 * 60_000L
    }
}
