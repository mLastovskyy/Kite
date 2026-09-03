package app.kite.child.location

import android.content.Context
import app.kite.child.identity.MemberIdentity
import app.kite.core.location.LocationDao
import app.kite.core.location.TrailPoint
import app.kite.core.location.TrailRemote
import java.time.Instant
import java.time.format.DateTimeFormatter

/** A raw Room fix considered for the trail. */
data class TrailCandidate(val id: Long, val latitude: Double, val longitude: Double, val accuracyM: Float?, val recordedAt: Long)

/** The last point actually uploaded — thinning is measured from it, across restarts. */
data class TrailAnchor(val latitude: Double, val longitude: Double, val recordedAt: Long)

/**
 * Which raw fixes become trail points: a fix must be BOTH [TrailRemote.MIN_GAP_MS] later and
 * [TrailRemote.MIN_GAP_METERS] away from the last uploaded one. That turns a minute-by-minute
 * stream into the day's movement — a child sitting at home adds nothing, walking adds a line.
 * Pure, so the rule is unit-tested; raw fixes never leave Room (CLAUDE.md).
 */
object TrailThinning {
    data class Result(val selected: List<TrailCandidate>, val anchor: TrailAnchor?)

    fun select(candidates: List<TrailCandidate>, anchor: TrailAnchor?): Result {
        var current = anchor
        val selected = mutableListOf<TrailCandidate>()
        candidates.sortedBy { it.recordedAt }.forEach { candidate ->
            val previous = current
            val far = previous == null ||
                (
                    candidate.recordedAt - previous.recordedAt >= TrailRemote.MIN_GAP_MS &&
                        haversineMeters(previous.latitude, previous.longitude, candidate.latitude, candidate.longitude) >=
                        TrailRemote.MIN_GAP_METERS
                    )
            if (far) {
                selected += candidate
                current = TrailAnchor(candidate.latitude, candidate.longitude, candidate.recordedAt)
            }
        }
        return Result(selected, current)
    }
}

/**
 * Uploads the thinned trail in batches. Every fix the batch looked at is marked synced —
 * including the ones thinning dropped — so they are never re-evaluated; a failed upload marks
 * nothing, which is exactly the offline queue (the `synced` flag in Room).
 */
class TrailUploader(
    context: Context,
    private val dao: LocationDao,
    private val remote: TrailRemote,
    private val identity: MemberIdentity,
) {
    private val prefs = context.getSharedPreferences("trail", Context.MODE_PRIVATE)

    /** Returns how many points were uploaded (0 when there was nothing or the call failed). */
    suspend fun uploadPending(): Int {
        val familyId = identity.familyId() ?: return 0
        val memberId = identity.memberId() ?: return 0
        val rows = dao.unsynced(BATCH_LIMIT)
        if (rows.isEmpty()) return 0
        val candidates =
            rows.map { row ->
                TrailCandidate(
                    id = row.id,
                    latitude = row.latitude,
                    longitude = row.longitude,
                    accuracyM = row.accuracyMeters,
                    recordedAt = row.recordedAt,
                )
            }
        val thinned = TrailThinning.select(candidates, anchor())
        val points =
            thinned.selected.map { candidate ->
                TrailPoint(
                    memberId = memberId,
                    familyId = familyId,
                    latitude = candidate.latitude,
                    longitude = candidate.longitude,
                    accuracyM = candidate.accuracyM,
                    recordedAt = DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(candidate.recordedAt)),
                )
            }
        if (remote.upload(points).isFailure) return 0
        thinned.anchor?.let(::saveAnchor)
        dao.markSynced(candidates.map { it.id })
        return points.size
    }

    private fun anchor(): TrailAnchor? {
        val recordedAt = prefs.getLong(KEY_AT, 0L)
        if (recordedAt == 0L) return null
        return TrailAnchor(
            latitude = Double.fromBits(prefs.getLong(KEY_LAT, 0L)),
            longitude = Double.fromBits(prefs.getLong(KEY_LON, 0L)),
            recordedAt = recordedAt,
        )
    }

    private fun saveAnchor(anchor: TrailAnchor) {
        prefs.edit()
            // Prefs have no Double; the raw bits round-trip exactly, unlike a string.
            .putLong(KEY_LAT, anchor.latitude.toRawBits())
            .putLong(KEY_LON, anchor.longitude.toRawBits())
            .putLong(KEY_AT, anchor.recordedAt)
            .apply()
    }

    private companion object {
        const val BATCH_LIMIT = 300
        const val KEY_LAT = "anchor_lat"
        const val KEY_LON = "anchor_lon"
        const val KEY_AT = "anchor_at"
    }
}
