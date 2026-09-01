package app.kite.parent

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kite.core.auth.AuthState
import app.kite.core.auth.SessionManager
import app.kite.core.design.KiteTheme
import app.kite.core.design.LocalAppColors
import app.kite.core.design.components.AppSpinner
import app.kite.core.family.FamilyRepository
import app.kite.core.killswitch.KillSwitchRepository
import app.kite.core.platform.PlatformServices
import app.kite.core.rules.RulesRemote
import app.kite.core.secure.SecureStore
import app.kite.core.usage.UsageRemote
import app.kite.parent.auth.AuthScreen
import app.kite.parent.family.ParentHomeScreen

/**
 * Parent app shell. Routes on the auth state: a loading splash, the auth screen when
 * signed out, and the home (M1 gallery for now — the family screen slots in here next).
 */
@Composable
fun ParentRoot(
    sessionManager: SessionManager,
    familyRepository: FamilyRepository,
    secureStore: SecureStore,
    usageRemote: UsageRemote,
    rulesRemote: RulesRemote,
    platformServices: PlatformServices,
    killSwitch: KillSwitchRepository,
    servicesFlavor: String,
    versionName: String,
    openReleasesPage: () -> Unit,
) {
    KiteTheme {
        val authState by sessionManager.authState.collectAsStateWithLifecycle()
        when (authState) {
            AuthState.Loading -> Splash()
            AuthState.SignedOut -> AuthScreen(sessionManager = sessionManager, onSignedIn = {})
            is AuthState.SignedIn ->
                ParentHomeScreen(
                    familyRepository = familyRepository,
                    sessionManager = sessionManager,
                    secureStore = secureStore,
                    usageRemote = usageRemote,
                    rulesRemote = rulesRemote,
                )
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
