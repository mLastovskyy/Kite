package app.kite.parent

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.net.toUri
import app.kite.core.auth.SessionManager
import app.kite.core.family.FamilyRepository
import app.kite.core.killswitch.KillSwitchRepository
import app.kite.core.platform.PlatformServices
import app.kite.core.rules.RulesRemote
import app.kite.core.secure.SecureStore
import app.kite.core.usage.UsageRemote
import org.koin.android.ext.android.inject
import org.koin.core.qualifier.named

class MainActivity : ComponentActivity() {
    private val platformServices: PlatformServices by inject()
    private val killSwitch: KillSwitchRepository by inject()
    private val sessionManager: SessionManager by inject()
    private val familyRepository: FamilyRepository by inject()
    private val secureStore: SecureStore by inject()
    private val usageRemote: UsageRemote by inject()
    private val rulesRemote: RulesRemote by inject()
    private val servicesFlavor: String by inject(named("servicesFlavor"))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ParentRoot(
                sessionManager = sessionManager,
                familyRepository = familyRepository,
                secureStore = secureStore,
                usageRemote = usageRemote,
                rulesRemote = rulesRemote,
                platformServices = platformServices,
                killSwitch = killSwitch,
                servicesFlavor = servicesFlavor,
                versionName = BuildConfig.VERSION_NAME,
                openReleasesPage = ::openReleasesPage,
            )
        }
    }

    private fun openReleasesPage() {
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, KillSwitchRepository.RELEASES_PAGE_URL.toUri()))
        }
    }
}
