package app.kite.child.enforce

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression cover for the bug the owner hit: the wizard could not enable the device admin
 * because the guard treated the activation screen as a removal attempt and threw the user
 * out. `DeviceAdminAdd` is the same Activity for activating and deactivating, so the admin
 * state — not the class name — decides.
 */
class RemovalThreatTest {
    private fun threat(
        packageName: String? = "com.android.settings",
        className: String = "",
        windowText: String = "",
        adminActive: Boolean = true,
        protectionLifted: Boolean = false,
        selfLaunchedSettings: Boolean = false,
    ) = RemovalThreat.isThreat(
        packageName = packageName,
        className = className,
        windowText = windowText,
        ownPackage = "app.kite.child",
        adminActive = adminActive,
        protectionLifted = protectionLifted,
        selfLaunchedSettings = selfLaunchedSettings,
    )

    @Test
    fun `admin activation screen is not a threat while the admin is off`() {
        assertFalse(
            threat(
                className = "com.android.settings.DeviceAdminAdd",
                windowText = "Kite Jr администратор устройства активировать",
                adminActive = false,
            ),
        )
    }

    @Test
    fun `the same screen is a threat once the admin is on`() {
        assertTrue(threat(className = "com.android.settings.DeviceAdminAdd", adminActive = true))
    }

    @Test
    fun `nothing is a threat while there is no active admin`() {
        // Without an active admin Android permits the uninstall anyway: bouncing the child
        // protects nothing and only breaks our own setup screens.
        assertFalse(threat(className = "com.android.settings.applications.InstalledAppDetails", adminActive = false))
        assertFalse(threat(windowText = "Kite Jr удалить", adminActive = false))
    }

    @Test
    fun `app details page is a threat`() {
        assertTrue(threat(className = "com.android.settings.applications.InstalledAppDetails"))
        assertTrue(threat(className = "com.android.packageinstaller.UninstallerActivity"))
    }

    @Test
    fun `settings we opened ourselves are left alone`() {
        // The wizard deep-links into the app-details page for background location.
        assertFalse(
            threat(
                className = "com.android.settings.applications.InstalledAppDetails",
                selfLaunchedSettings = true,
            ),
        )
    }

    @Test
    fun `an authorised removal window is left alone`() {
        assertFalse(threat(className = "com.android.settings.DeviceAdminAdd", protectionLifted = true))
    }

    @Test
    fun `unrelated settings screens do not trip the guard`() {
        assertFalse(threat(className = "com.android.settings.wifi.WifiSettings", windowText = "Wi-Fi сети подключение"))
        // Our label alone is not enough, and neither is an action word alone.
        assertFalse(threat(windowText = "Kite Jr сведения о приложении"))
        assertFalse(threat(windowText = "удалить другое приложение"))
    }

    @Test
    fun `oem settings are matched by text when the class name is unknown`() {
        assertTrue(threat(packageName = "com.huawei.systemmanager", windowText = "Kite Jr удалить приложение"))
    }

    @Test
    fun `screens outside settings are ignored`() {
        assertFalse(threat(packageName = "com.example.launcher", className = "DeviceAdminAdd"))
        assertFalse(threat(packageName = null))
    }
}
