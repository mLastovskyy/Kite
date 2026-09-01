package app.kite.core.usage

import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HourBucketsTest {
    private val zone = ZoneId.of("Europe/Moscow") // UTC+3, no DST

    private fun at(day: String, time: String): Long = java.time.LocalDateTime.parse("${day}T$time")
        .atZone(zone)
        .toInstant()
        .toEpochMilli()

    @Test
    fun `interval inside one hour lands in one bucket`() {
        val buckets = HourBuckets.split(at("2026-09-01", "10:15:00"), at("2026-09-01", "10:45:30"), zone)
        assertEquals(listOf(HourBuckets.Bucket("2026-09-01", 10, 30 * 60_000L + 30_000L)), buckets)
    }

    @Test
    fun `interval crossing an hour splits at the boundary`() {
        val buckets = HourBuckets.split(at("2026-09-01", "10:50:00"), at("2026-09-01", "11:10:00"), zone)
        assertEquals(
            listOf(
                HourBuckets.Bucket("2026-09-01", 10, 10 * 60_000L),
                HourBuckets.Bucket("2026-09-01", 11, 10 * 60_000L),
            ),
            buckets,
        )
    }

    @Test
    fun `interval crossing midnight splits into both days`() {
        val buckets = HourBuckets.split(at("2026-09-01", "23:59:00"), at("2026-09-02", "00:01:00"), zone)
        assertEquals(
            listOf(
                HourBuckets.Bucket("2026-09-01", 23, 60_000L),
                HourBuckets.Bucket("2026-09-02", 0, 60_000L),
            ),
            buckets,
        )
    }

    @Test
    fun `empty and inverted intervals produce nothing`() {
        val t = at("2026-09-01", "10:00:00")
        assertTrue(HourBuckets.split(t, t, zone).isEmpty())
        assertTrue(HourBuckets.split(t, t - 1000, zone).isEmpty())
    }

    @Test
    fun `total duration is preserved across many boundaries`() {
        val start = at("2026-09-01", "21:30:00")
        val end = at("2026-09-02", "02:15:00")
        val buckets = HourBuckets.split(start, end, zone)
        assertEquals(end - start, buckets.sumOf { it.ms })
        assertEquals(6, buckets.size) // 21,22,23 + 00,01,02
    }
}
