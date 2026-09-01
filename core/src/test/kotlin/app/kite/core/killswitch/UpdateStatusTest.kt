package app.kite.core.killswitch

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UpdateStatusTest {
    @Test
    fun `update available only when latest is strictly newer`() {
        assertTrue(UpdateStatus(currentVersionCode = 5, latestVersionCode = 6).updateAvailable)
        assertFalse(UpdateStatus(currentVersionCode = 5, latestVersionCode = 5).updateAvailable)
        assertFalse(UpdateStatus(currentVersionCode = 5, latestVersionCode = 4).updateAvailable)
    }

    @Test
    fun `default manifest never nags a fresh install`() {
        // latestVersionCode defaults to 0 until the first successful fetch.
        assertFalse(UpdateStatus(currentVersionCode = 1, latestVersionCode = 0).updateAvailable)
    }
}
