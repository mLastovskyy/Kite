package app.kite.core.family

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * The device report goes up as a PostgREST upsert that merges column by column, so a field
 * missing from the body leaves the old value in place. These are the two rules that keeps
 * honest, pinned here because the failure is silent: the row simply never changes again.
 */
class DeviceReportEncodingTest {
    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
            encodeDefaults = true
            explicitNulls = false
        }

    @Test
    fun `an unlocked device with nothing missing still sends both columns`() {
        val body =
            json.encodeToString(
                ChildDevice(memberId = "m", familyId = "f", locked = false, protectionMissing = emptyList()),
            )
        assertContains(body, "\"locked\":false")
        assertContains(body, "\"protection_missing\":[]")
    }

    @Test
    fun `a null column is left out, so the server default still applies`() {
        val body = json.encodeToString(ChildDevice(memberId = "m", familyId = "f", batteryPct = null))
        assertFalse(body.contains("battery_pct"))
    }

    @Test
    fun `an explicit null in a hand-built body survives, so a column can be cleared`() {
        val body =
            json.encodeToString(
                buildJsonObject {
                    put("status", "done")
                    put("photo_url", null as String?)
                },
            )
        assertEquals("""{"status":"done","photo_url":null}""", body)
    }
}
