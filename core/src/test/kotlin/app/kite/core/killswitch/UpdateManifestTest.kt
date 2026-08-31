package app.kite.core.killswitch

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UpdateManifestTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `parses the reference manifest from the spec`() {
        val raw =
            """
            { "latestVersionCode": 0, "minSupportedVersionCode": 0,
              "disableEnforcement": false, "message": null }
            """.trimIndent()
        val manifest = json.decodeFromString<UpdateManifest>(raw)
        assertEquals(0, manifest.latestVersionCode)
        assertEquals(0, manifest.minSupportedVersionCode)
        assertFalse(manifest.disableEnforcement)
        assertNull(manifest.message)
    }

    @Test
    fun `missing fields fall back to safe defaults`() {
        val manifest = json.decodeFromString<UpdateManifest>("{}")
        assertFalse(manifest.disableEnforcement, "enforcement must stay on by default")
        assertEquals(0, manifest.latestVersionCode)
        assertNull(manifest.message)
    }

    @Test
    fun `unknown fields from a future manifest version are ignored`() {
        val raw = """{ "disableEnforcement": true, "futureField": {"nested": 1} }"""
        val manifest = json.decodeFromString<UpdateManifest>(raw)
        assertTrue(manifest.disableEnforcement)
    }

    @Test
    fun `manifest round-trips through encoding`() {
        val original = UpdateManifest(latestVersionCode = 42, minSupportedVersionCode = 7, disableEnforcement = true, message = "тест")
        val decoded = json.decodeFromString<UpdateManifest>(json.encodeToString(UpdateManifest.serializer(), original))
        assertEquals(original, decoded)
    }
}
