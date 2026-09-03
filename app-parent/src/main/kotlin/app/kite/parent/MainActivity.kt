package app.kite.parent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import app.kite.core.appearance.AppearanceRepository
import app.kite.core.approval.ApprovalsRemote
import app.kite.core.apps.ChildAppsRemote
import app.kite.core.auth.AuthState
import app.kite.core.auth.SessionManager
import app.kite.core.avatar.AvatarRemote
import app.kite.core.commands.CommandsRemote
import app.kite.core.family.FamilyRepository
import app.kite.core.killswitch.KillSwitchRepository
import app.kite.core.location.DeviceLocationRemote
import app.kite.core.location.PlacesRemote
import app.kite.core.location.TrailRemote
import app.kite.core.net.ConnectivityObserver
import app.kite.core.platform.PlatformServices
import app.kite.core.push.PushRegistrar
import app.kite.core.rules.RulesRemote
import app.kite.core.secure.SecureStore
import app.kite.core.tasks.TasksRemote
import app.kite.core.update.ApkInstaller
import app.kite.core.usage.UsageRemote
import app.kite.parent.auth.PinLock
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.core.qualifier.named

class MainActivity : ComponentActivity() {
    private val platformServices: PlatformServices by inject()
    private val killSwitch: KillSwitchRepository by inject()
    private val sessionManager: SessionManager by inject()
    private val pinLock: PinLock by inject()
    private val appearance: AppearanceRepository by inject()
    private val apkInstaller: ApkInstaller by inject()
    private val familyRepository: FamilyRepository by inject()
    private val secureStore: SecureStore by inject()
    private val usageRemote: UsageRemote by inject()
    private val rulesRemote: RulesRemote by inject()
    private val commandsRemote: CommandsRemote by inject()
    private val locationRemote: DeviceLocationRemote by inject()
    private val placesRemote: PlacesRemote by inject()
    private val trailRemote: TrailRemote by inject()
    private val childAppsRemote: ChildAppsRemote by inject()
    private val approvalsRemote: ApprovalsRemote by inject()
    private val tasksRemote: TasksRemote by inject()
    private val avatarRemote: AvatarRemote by inject()
    private val connectivityObserver: ConnectivityObserver by inject()
    private val pushRegistrar: PushRegistrar by inject()
    private val servicesFlavor: String by inject(named("servicesFlavor"))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Register the FCM token whenever the parent becomes signed in (idempotent upsert).
        lifecycleScope.launch {
            sessionManager.authState.collect { state ->
                if (state is AuthState.SignedIn) pushRegistrar.ensureRegistered()
            }
        }
        setContent {
            ParentRoot(
                sessionManager = sessionManager,
                pinLock = pinLock,
                appearance = appearance,
                apkInstaller = apkInstaller,
                familyRepository = familyRepository,
                secureStore = secureStore,
                usageRemote = usageRemote,
                rulesRemote = rulesRemote,
                commandsRemote = commandsRemote,
                locationRemote = locationRemote,
                placesRemote = placesRemote,
                trailRemote = trailRemote,
                childAppsRemote = childAppsRemote,
                approvalsRemote = approvalsRemote,
                tasksRemote = tasksRemote,
                avatarRemote = avatarRemote,
                connectivityObserver = connectivityObserver,
                platformServices = platformServices,
                killSwitch = killSwitch,
                servicesFlavor = servicesFlavor,
                versionName = BuildConfig.VERSION_NAME,
            )
        }
    }

    // PIN relock is driven by how long the app sat in the background (see PinLock).
    override fun onStart() {
        super.onStart()
        pinLock.onForeground()
    }

    override fun onStop() {
        pinLock.onBackground()
        super.onStop()
    }
}
