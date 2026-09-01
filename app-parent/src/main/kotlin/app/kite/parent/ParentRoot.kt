package app.kite.parent

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kite.core.approval.ApprovalsRemote
import app.kite.core.auth.AuthState
import app.kite.core.auth.SessionManager
import app.kite.core.avatar.AvatarRemote
import app.kite.core.commands.CommandsRemote
import app.kite.core.design.KiteTheme
import app.kite.core.design.LocalAppColors
import app.kite.core.design.components.AppChrome
import app.kite.core.design.components.AppSpinner
import app.kite.core.family.FamilyRepository
import app.kite.core.killswitch.KillSwitchRepository
import app.kite.core.location.DeviceLocationRemote
import app.kite.core.net.ConnectivityObserver
import app.kite.core.platform.PlatformServices
import app.kite.core.rules.RulesRemote
import app.kite.core.secure.SecureStore
import app.kite.core.usage.UsageRemote
import app.kite.parent.auth.AuthScreen
import app.kite.parent.auth.PinLock
import app.kite.parent.auth.PinSetupScreen
import app.kite.parent.auth.PinUnlockScreen
import app.kite.parent.family.ParentHomeScreen
import kotlinx.coroutines.launch

/**
 * Parent app shell. Routes on the auth state: a loading splash, the auth screen when
 * signed out, then — signed in — the PIN gate (cold start / relock), the one-time PIN setup
 * offer after a fresh sign-in, and finally home.
 */
@Composable
fun ParentRoot(
    sessionManager: SessionManager,
    pinLock: PinLock,
    familyRepository: FamilyRepository,
    secureStore: SecureStore,
    usageRemote: UsageRemote,
    rulesRemote: RulesRemote,
    commandsRemote: CommandsRemote,
    locationRemote: DeviceLocationRemote,
    approvalsRemote: ApprovalsRemote,
    avatarRemote: AvatarRemote,
    connectivityObserver: ConnectivityObserver,
    platformServices: PlatformServices,
    killSwitch: KillSwitchRepository,
    servicesFlavor: String,
    versionName: String,
    openReleasesPage: () -> Unit,
) {
    KiteTheme {
        AppChrome(connectivityObserver) {
            val scope = rememberCoroutineScope()
            val authState by sessionManager.authState.collectAsStateWithLifecycle()
            val locked by pinLock.locked.collectAsStateWithLifecycle()
            val setupRequested by pinLock.setupRequested.collectAsStateWithLifecycle()

            // A PIN belongs to the account that set it: signing out (by hand, or because the
            // server rejected the refresh token) removes it so the next account starts clean.
            LaunchedEffect(authState) {
                if (authState is AuthState.SignedOut) pinLock.clear()
            }

            when (authState) {
                AuthState.Loading -> Splash()
                AuthState.SignedOut ->
                    AuthScreen(
                        sessionManager = sessionManager,
                        // Fresh credentials just went in: offer the 6-digit code once, right away.
                        onSignedIn = { if (!pinLock.isSet()) pinLock.requestSetup() },
                    )
                is AuthState.SignedIn ->
                    when {
                        locked ->
                            PinUnlockScreen(
                                pinLock = pinLock,
                                onForgot = { scope.launch { sessionManager.signOut() } },
                            )
                        setupRequested -> PinSetupScreen(pinLock = pinLock, onDone = { pinLock.dismissSetup() })
                        else ->
                            ParentHomeScreen(
                                familyRepository = familyRepository,
                                sessionManager = sessionManager,
                                secureStore = secureStore,
                                usageRemote = usageRemote,
                                rulesRemote = rulesRemote,
                                commandsRemote = commandsRemote,
                                locationRemote = locationRemote,
                                approvalsRemote = approvalsRemote,
                                avatarRemote = avatarRemote,
                                onPinSettings = { pinLock.requestSetup() },
                            )
                    }
            }
        }
    }
}

@Composable
private fun Splash() {
    val colors = LocalAppColors.current
    Box(Modifier.fillMaxSize().background(colors.bgGrouped), contentAlignment = Alignment.Center) {
        AppSpinner(color = colors.accent, size = 28.dp)
    }
}
