package app.kite.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The forms a server timestamp actually arrives in. The offset form is the one that crashed
 * the map on Android 10 — note that this test cannot prove it by itself: the desktop JVM
 * parses it either way, which is exactly how the bug reached a release.
 */
class TimestampsTest {
    @Test
    fun `postgrest offset form`() {
        assertEquals(1788719920847L, Timestamps.epochMsOrNull("2026-09-06T18:38:40.847+00:00"))
    }

    @Test
    fun `zulu form`() {
        assertEquals(1788719920847L, Timestamps.epochMsOrNull("2026-09-06T18:38:40.847Z"))
    }

    @Test
    fun `offset other than utc`() {
        assertEquals(
            Timestamps.epochMsOrNull("2026-09-06T18:38:40Z"),
            Timestamps.epochMsOrNull("2026-09-06T21:38:40+03:00"),
        )
    }

    @Test
    fun `bare local time counts as utc`() {
        assertEquals(Timestamps.epochMsOrNull("2026-09-06T18:38:40Z"), Timestamps.epochMsOrNull("2026-09-06T18:38:40"))
    }

    @Test
    fun `nothing to parse`() {
        assertNull(Timestamps.instantOrNull(null))
        assertNull(Timestamps.instantOrNull(""))
        assertNull(Timestamps.instantOrNull("вчера"))
        assertEquals(0L, Timestamps.epochMs("вчера"))
    }
}
