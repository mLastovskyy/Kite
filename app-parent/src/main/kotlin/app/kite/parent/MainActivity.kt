package app.kite.parent

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.net.toUri
import app.kite.core.killswitch.KillSwitchRepository
import app.kite.core.platform.PlatformServices
import app.kite.parent.gallery.GalleryScreen
import org.koin.android.ext.android.inject
import org.koin.core.qualifier.named

class MainActivity : ComponentActivity() {
    private val platformServices: PlatformServices by inject()
    private val killSwitch: KillSwitchRepository by inject()
    private val servicesFlavor: String by inject(named("servicesFlavor"))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GalleryScreen(
                platformVariant = platformServices.variant,
                servicesFlavor = servicesFlavor,
                versionName = BuildConfig.VERSION_NAME,
                disableEnforcement = killSwitch.disableEnforcement,
                updateStatus = killSwitch.updateStatus,
                checkForUpdates = { killSwitch.refresh().isSuccess },
                openReleasesPage = ::openReleasesPage,
            )
        }
    }

    private fun openReleasesPage() {
        // Until the in-app installer (hms) / Play In-App Updates (gms) land, the download
        // happens in the browser. runCatching: a device may have no browser at all.
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, KillSwitchRepository.RELEASES_PAGE_URL.toUri()))
        }
    }
}
