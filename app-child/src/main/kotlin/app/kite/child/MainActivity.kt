package app.kite.child

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.kite.core.auth.SessionManager
import app.kite.core.family.FamilyRepository
import app.kite.core.killswitch.KillSwitchRepository
import app.kite.core.platform.PlatformServices
import app.kite.core.secure.SecureStore
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {
    private val platformServices: PlatformServices by inject()
    private val killSwitch: KillSwitchRepository by inject()
    private val sessionManager: SessionManager by inject()
    private val familyRepository: FamilyRepository by inject()
    private val secureStore: SecureStore by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ChildRoot(
                sessionManager = sessionManager,
                familyRepository = familyRepository,
                secureStore = secureStore,
                platformServices = platformServices,
                killSwitch = killSwitch,
            )
        }
    }
}
