package app.kite.core.usage

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction

/**
 * One row = foreground time of one app within one local-time hour. Everything the screen
 * time UI needs (day totals, hourly bars, per-app lists, weekly charts) is a SUM over this
 * table, so it is the only raw-telemetry table. Raw usage NEVER leaves the device —
 * only daily aggregates are synced (CLAUDE.md, 500 MB server budget).
 */
@Entity(tableName = "usage_hour", primaryKeys = ["day", "hour", "package_name"])
data class UsageHourEntity(
    /** Local date as ISO yyyy-MM-dd; local because "the child's day" is what parents mean. */
    val day: String,
    /** Local hour of day, 0..23. */
    val hour: Int,
    @ColumnInfo(name = "package_name") val packageName: String,
    @ColumnInfo(name = "foreground_ms") val foregroundMs: Long,
)

/** SUM per day, for weekly bars. */
data class DayTotal(val day: String, val totalMs: Long)

/** SUM per app over a day range, for the ranked app list. */
data class AppTotal(val packageName: String, val totalMs: Long)

/** SUM per hour of one day, for the hourly bars. */
data class HourTotal(val hour: Int, val totalMs: Long)

@Dao
interface UsageDao {
    @Query("SELECT hour, SUM(foreground_ms) AS totalMs FROM usage_hour WHERE day = :day GROUP BY hour")
    suspend fun hourTotals(day: String): List<HourTotal>

    @Query("SELECT day, SUM(foreground_ms) AS totalMs FROM usage_hour WHERE day BETWEEN :fromDay AND :toDay GROUP BY day ORDER BY day")
    suspend fun dayTotals(fromDay: String, toDay: String): List<DayTotal>

    @Query(
        "SELECT package_name AS packageName, SUM(foreground_ms) AS totalMs FROM usage_hour " +
            "WHERE day BETWEEN :fromDay AND :toDay GROUP BY package_name ORDER BY totalMs DESC",
    )
    suspend fun appTotals(fromDay: String, toDay: String): List<AppTotal>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(row: UsageHourEntity): Long

    @Query(
        "UPDATE usage_hour SET foreground_ms = foreground_ms + :deltaMs " +
            "WHERE day = :day AND hour = :hour AND package_name = :packageName",
    )
    suspend fun addMs(day: String, hour: Int, packageName: String, deltaMs: Long)

    /** Adds the buckets on top of what is already stored (collection is incremental). */
    @Transaction
    suspend fun accumulate(rows: List<UsageHourEntity>) {
        rows.forEach { row ->
            if (insertIgnore(row) == -1L) addMs(row.day, row.hour, row.packageName, row.foregroundMs)
        }
    }

    /** Raw telemetry retention: rows older than the horizon are dropped. */
    @Query("DELETE FROM usage_hour WHERE day < :minDay")
    suspend fun purgeBefore(minDay: String)
}

@Database(entities = [UsageHourEntity::class], version = 1, exportSchema = false)
abstract class UsageDatabase : RoomDatabase() {
    abstract fun usageDao(): UsageDao
}
