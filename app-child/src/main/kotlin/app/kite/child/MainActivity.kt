package app.kite.child

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import app.kite.core.auth.AuthState
import app.kite.core.auth.SessionManager
import app.kite.core.avatar.AvatarRemote
import app.kite.core.family.FamilyRepository
import app.kite.core.killswitch.KillSwitchRepository
import app.kite.core.net.ConnectivityObserver
import app.kite.core.platform.PlatformServices
import app.kite.core.push.PushRegistrar
import app.kite.core.secure.SecureStore
import app.kite.core.update.ApkInstaller
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {
    private val platformServices: PlatformServices by inject()
    private val killSwitch: KillSwitchRepository by inject()
    private val sessionManager: SessionManager by inject()
    private val familyRepository: FamilyRepository by inject()
    private val secureStore: SecureStore by inject()
    private val connectivityObserver: ConnectivityObserver by inject()
    private val pushRegistrar: PushRegistrar by inject()
    private val avatarRemote: AvatarRemote by inject()
    private val apkInstaller: ApkInstaller by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Register the FCM token whenever the device becomes signed in (idempotent upsert).
        lifecycleScope.launch {
            sessionManager.authState.collect { state ->
                if (state is AuthState.SignedIn) pushRegistrar.ensureRegistered()
            }
        }
        setContent {
            ChildRoot(
                sessionManager = sessionManager,
                familyRepository = familyRepository,
                secureStore = secureStore,
                connectivityObserver = connectivityObserver,
                platformServices = platformServices,
                killSwitch = killSwitch,
                avatarRemote = avatarRemote,
                apkInstaller = apkInstaller,
            )
        }
    }
}
