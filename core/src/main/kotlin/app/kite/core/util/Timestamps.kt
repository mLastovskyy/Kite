package app.kite.core.util

import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset

/**
 * Timestamps as PostgREST returns them: `2026-09-06T18:38:40.847697+00:00`.
 *
 * [Instant.parse] cannot read that on older Android — its ISO_INSTANT wants a `Z`, and the
 * offset form was only accepted from Java 12 (Android 13+). On Android 10 the same string
 * that parses fine on a new phone throws `DateTimeParseException` at index 26, which is
 * exactly what crashed the map on a Huawei P40 lite (07.09.2026). Everything that reads a
 * server timestamp goes through here — and it never throws.
 */
object Timestamps {
    fun instantOrNull(iso: String?): Instant? {
        val text = iso?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return runCatching { OffsetDateTime.parse(text).toInstant() }.getOrNull()
            ?: runCatching { Instant.parse(text) }.getOrNull()
            // A bare `2026-09-06T18:38:40` (no zone at all) is UTC on this server.
            ?: runCatching { LocalDateTime.parse(text).toInstant(ZoneOffset.UTC) }.getOrNull()
    }

    fun epochMsOrNull(iso: String?): Long? = instantOrNull(iso)?.toEpochMilli()

    fun epochMs(iso: String?, fallback: Long = 0L): Long = epochMsOrNull(iso) ?: fallback
}
