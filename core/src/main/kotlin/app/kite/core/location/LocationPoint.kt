package app.kite.core.location

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query

/**
 * One recorded location fix. The full history lives here in Room ON THE CHILD DEVICE —
 * individual points never leave it (CLAUDE.md); only the latest position and a coarse
 * trail are synced. [synced] marks whether the coarse-trail upload has taken this point,
 * so an offline gap is caught up later (the offline queue).
 */
@Entity(tableName = "location_point")
data class LocationPointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float?,
    val recordedAt: Long,
    val synced: Boolean = false,
)

@Dao
interface LocationDao {
    @Insert
    suspend fun insert(point: LocationPointEntity): Long

    @Query("SELECT * FROM location_point ORDER BY recordedAt DESC LIMIT 1")
    suspend fun latest(): LocationPointEntity?

    @Query("SELECT * FROM location_point WHERE synced = 0 ORDER BY recordedAt ASC LIMIT :limit")
    suspend fun unsynced(limit: Int): List<LocationPointEntity>

    @Query("UPDATE location_point SET synced = 1 WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<Long>)

    /** History for a time window, for the parent's trail view (bounded by the caller). */
    @Query("SELECT * FROM location_point WHERE recordedAt BETWEEN :fromMs AND :toMs ORDER BY recordedAt ASC")
    suspend fun between(fromMs: Long, toMs: Long): List<LocationPointEntity>

    /** Retention: drop points older than the horizon so Room stays bounded. */
    @Query("DELETE FROM location_point WHERE recordedAt < :minMs")
    suspend fun purgeBefore(minMs: Long)
}
