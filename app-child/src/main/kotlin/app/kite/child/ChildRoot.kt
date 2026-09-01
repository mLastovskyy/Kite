package app.kite.child

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kite.child.pairing.ChildPairingScreen
import app.kite.child.permissions.OnboardingWizardScreen
import app.kite.child.permissions.ProtectionHealthScreen
import app.kite.child.permissions.ProtectionInspector
import app.kite.child.permissions.WizardController
import app.kite.child.permissions.WizardStateStore
import app.kite.child.status.ChildStatusScreen
import app.kite.child.transparency.TransparencyScreen
import app.kite.core.auth.AuthState
import app.kite.core.auth.SessionManager
import app.kite.core.design.AccentColors
import app.kite.core.design.KiteTheme
import app.kite.core.family.FamilyRepository
import app.kite.core.killswitch.KillSwitchRepository
import app.kite.core.platform.PlatformServices
import kotlinx.coroutines.launch

private enum class ChildDestination { Wizard, Status, Health, Transparency }

/**
 * Child app shell. Until the device is paired (no session) it shows the pairing flow with
 * its mandatory consent screen; once paired it runs the onboarding wizard, then the status
 * screen with a persistent banner while anything is missing.
 */
@Composable
fun ChildRoot(
    sessionManager: SessionManager,
    familyRepository: FamilyRepository,
    platformServices: PlatformServices,
    killSwitch: KillSwitchRepository,
) {
    KiteTheme(accents = AccentColors.Child) {
        val authState by sessionManager.authState.collectAsStateWithLifecycle()
        when (authState) {
            AuthState.Loading -> Unit
            AuthState.SignedOut ->
                ChildPairingScreen(
                    familyRepository = familyRepository,
                    sessionManager = sessionManager,
                    onPaired = {},
                )
            is AuthState.SignedIn ->
                PairedShell(platformServices = platformServices, killSwitch = killSwitch)
        }
    }
}

@Composable
private fun PairedShell(platformServices: PlatformServices, killSwitch: KillSwitchRepository) {
    val context = LocalContext.current
    val inspector = remember { ProtectionInspector(context) }
    val controller = remember { WizardController(inspector).apply { refresh() } }
    val store = remember { WizardStateStore(context) }
    val backgroundLabel = remember { inspector.backgroundPermissionOptionLabel() }
    val scope = rememberCoroutineScope()

    var destination by remember {
        mutableStateOf(if (controller.firstUnsatisfied == null) ChildDestination.Status else ChildDestination.Wizard)
    }

    when (destination) {
        ChildDestination.Wizard ->
            OnboardingWizardScreen(
                controller = controller,
                store = store,
                backgroundOptionLabel = backgroundLabel,
                onFinished = { destination = ChildDestination.Status },
                onPostpone = {
                    scope.launch { store.setPostponed(true) }
                    destination = ChildDestination.Status
                },
            )

        ChildDestination.Status ->
            ChildStatusScreen(
                platformVariant = platformServices.variant,
                disableEnforcement = killSwitch.disableEnforcement,
                updateStatus = killSwitch.updateStatus,
                protectionGranted = controller.grantedCount,
                protectionTotal = controller.total,
                onOpenHealth = { destination = ChildDestination.Health },
                onOpenTransparency = { destination = ChildDestination.Transparency },
            )

        ChildDestination.Health ->
            ProtectionHealthScreen(
                controller = controller,
                backgroundOptionLabel = backgroundLabel,
                onStartWizard = { destination = ChildDestination.Wizard },
            )

        ChildDestination.Transparency -> TransparencyScreen()
    }
}
