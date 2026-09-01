package app.kite.core.usage

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Splits a foreground interval into (local day, local hour) buckets so a session crossing
 * an hour or midnight boundary lands in the right bars. Pure time arithmetic on java.time —
 * no Android types, covered by unit tests.
 */
object HourBuckets {
    data class Bucket(val day: String, val hour: Int, val ms: Long)

    fun split(startMs: Long, endMs: Long, zone: ZoneId): List<Bucket> {
        if (endMs <= startMs) return emptyList()
        val result = mutableListOf<Bucket>()
        var cursor = ZonedDateTime.ofInstant(Instant.ofEpochMilli(startMs), zone)
        val end = ZonedDateTime.ofInstant(Instant.ofEpochMilli(endMs), zone)
        while (cursor.isBefore(end)) {
            val boundary = cursor.truncatedTo(ChronoUnit.HOURS).plusHours(1)
            val chunkEnd = if (boundary.isBefore(end)) boundary else end
            val ms = ChronoUnit.MILLIS.between(cursor, chunkEnd)
            if (ms > 0) {
                result +=
                    Bucket(
                        day = cursor.format(DateTimeFormatter.ISO_LOCAL_DATE),
                        hour = cursor.hour,
                        ms = ms,
                    )
            }
            cursor = chunkEnd
        }
        return result
    }
}
