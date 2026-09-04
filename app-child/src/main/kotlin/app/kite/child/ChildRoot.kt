package app.kite.child

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kite.child.enforce.ProtectionState
import app.kite.child.identity.MemberIdentity
import app.kite.child.location.LocationService
import app.kite.child.pairing.ChildPairingScreen
import app.kite.child.permissions.OnboardingWizardScreen
import app.kite.child.permissions.ProtectionHealthScreen
import app.kite.child.permissions.ProtectionInspector
import app.kite.child.permissions.ProtectionRequirement
import app.kite.child.permissions.WizardController
import app.kite.child.permissions.WizardStateStore
import app.kite.child.removal.RemovalActivity
import app.kite.child.setup.PAIRING_STAGES
import app.kite.child.status.ChildStatsScreen
import app.kite.child.status.ChildStatusScreen
import app.kite.child.status.TodaySummary
import app.kite.child.tasks.ChildTasksScreen
import app.kite.child.tasks.TasksStore
import app.kite.child.tasks.TasksSyncer
import app.kite.child.transparency.TransparencyScreen
import app.kite.child.usage.UsageCollectScheduler
import app.kite.core.approval.ApprovalsRemote
import app.kite.core.auth.AuthState
import app.kite.core.auth.SessionManager
import app.kite.core.avatar.AvatarRemote
import app.kite.core.design.AccentColors
import app.kite.core.design.KiteTheme
import app.kite.core.design.components.AppChrome
import app.kite.core.design.components.ProfileEditorScreen
import app.kite.core.family.FamilyMember
import app.kite.core.family.FamilyRepository
import app.kite.core.killswitch.KillSwitchRepository
import app.kite.core.net.ConnectivityObserver
import app.kite.core.platform.PlatformServices
import app.kite.core.secure.SecureStore
import app.kite.core.update.ApkInstaller
import kotlinx.coroutines.launch

private enum class ChildDestination { Wizard, Status, Health, Transparency, Tasks, Stats, Profile }

/** SecureStore key marking the device as paired; also read by the usage syncer. */
const val KEY_PAIRED_FAMILY_ID = "paired_family_id"

/** Offline-approval TOTP secret (base64), generated at pairing; verified locally in M6. */
const val KEY_OFFLINE_TOTP_SECRET = "offline_totp_secret"

/**
 * Child app shell. Pairing is complete only when a family id is stored: the anonymous
 * session appears BEFORE redeem_pairing succeeds, so a bare session must not flip the UI
 * (it would cancel the redeem coroutine mid-flight). Once paired it runs the onboarding
 * wizard, then the status screen with a persistent banner while anything is missing.
 */
@Composable
fun ChildRoot(
    sessionManager: SessionManager,
    familyRepository: FamilyRepository,
    secureStore: SecureStore,
    connectivityObserver: ConnectivityObserver,
    platformServices: PlatformServices,
    killSwitch: KillSwitchRepository,
    avatarRemote: AvatarRemote,
    apkInstaller: ApkInstaller,
    summary: TodaySummary,
    tasksStore: TasksStore,
    tasksSyncer: TasksSyncer,
    identity: MemberIdentity,
    approvalsRemote: ApprovalsRemote,
    protectionState: ProtectionState,
    versionName: String,
) {
    KiteTheme(accents = AccentColors.Child) {
        AppChrome(connectivityObserver) {
            val authState by sessionManager.authState.collectAsStateWithLifecycle()
            val appContext = LocalContext.current.applicationContext
            var pairedFamilyId by remember { mutableStateOf(secureStore.getString(KEY_PAIRED_FAMILY_ID)) }
            when {
                authState is AuthState.Loading -> Unit
                pairedFamilyId == null ->
                    ChildPairingScreen(
                        familyRepository = familyRepository,
                        sessionManager = sessionManager,
                        avatarRemote = avatarRemote,
                        onPaired = { familyId, totpSecretBase64 ->
                            secureStore.putString(KEY_OFFLINE_TOTP_SECRET, totpSecretBase64)
                            secureStore.putString(KEY_PAIRED_FAMILY_ID, familyId)
                            pairedFamilyId = familyId
                            // The parent is looking at an empty screen right now: publish the
                            // app list and pull the rules immediately, not at the 4-hour tick.
                            UsageCollectScheduler.runNow(appContext)
                        },
                    )
                else ->
                    PairedShell(
                        platformServices = platformServices,
                        killSwitch = killSwitch,
                        apkInstaller = apkInstaller,
                        summary = summary,
                        tasksStore = tasksStore,
                        tasksSyncer = tasksSyncer,
                        identity = identity,
                        approvalsRemote = approvalsRemote,
                        protectionState = protectionState,
                        familyRepository = familyRepository,
                        avatarRemote = avatarRemote,
                        versionName = versionName,
                    )
            }
        }
    }
}

@Composable
private fun PairedShell(
    platformServices: PlatformServices,
    killSwitch: KillSwitchRepository,
    apkInstaller: ApkInstaller,
    summary: TodaySummary,
    tasksStore: TasksStore,
    tasksSyncer: TasksSyncer,
    identity: MemberIdentity,
    approvalsRemote: ApprovalsRemote,
    protectionState: ProtectionState,
    familyRepository: FamilyRepository,
    avatarRemote: AvatarRemote,
    versionName: String,
) {
    val context = LocalContext.current
    val inspector = remember { ProtectionInspector(context) }
    val controller = remember { WizardController(inspector).apply { refresh() } }
    val store = remember { WizardStateStore(context) }
    val backgroundLabel = remember { inspector.backgroundPermissionOptionLabel() }
    val scope = rememberCoroutineScope()

    var destination by remember {
        mutableStateOf(if (controller.firstUnsatisfied == null) ChildDestination.Status else ChildDestination.Wizard)
    }
    // The wizard continues the pairing numbering on a first run, but stands alone when it is
    // reopened later from «Здоровье защиты».
    var wizardStandalone by remember { mutableStateOf(false) }
    // Bonus minutes granted today, for the «Задания» screen header.
    var bonusMinutes by remember { mutableIntStateOf(0) }
    val released by protectionState.released.collectAsStateWithLifecycle()
    LaunchedEffect(destination) {
        if (destination == ChildDestination.Tasks) bonusMinutes = summary.today().bonusMinutes
    }

    // Start location reporting once foreground location is granted. Started from a composable
    // (definitely foreground) so Android 12+ does not reject the foreground-service start.
    LaunchedEffect(Unit) {
        if (inspector.isSatisfied(ProtectionRequirement.LOCATION_FOREGROUND, vendorAutostartConfirmed = false)) {
            LocationService.start(context)
        }
    }

    // The system back gesture must work everywhere (DESIGN_SYSTEM.md): every screen the child
    // opens from home returns home instead of dropping out of the app.
    BackHandler(enabled = destination != ChildDestination.Status && destination != ChildDestination.Wizard) {
        destination = ChildDestination.Status
    }

    when (destination) {
        ChildDestination.Wizard ->
            OnboardingWizardScreen(
                controller = controller,
                store = store,
                backgroundOptionLabel = backgroundLabel,
                onFinished = {
                    destination = ChildDestination.Status
                    // Permissions just landed: start reporting now so the map and the statistics
                    // fill in while the parent still has the phone in hand.
                    if (inspector.isSatisfied(ProtectionRequirement.LOCATION_FOREGROUND, vendorAutostartConfirmed = false)) {
                        LocationService.start(context)
                    }
                    UsageCollectScheduler.runNow(context)
                },
                onPostpone = {
                    scope.launch { store.setPostponed(true) }
                    destination = ChildDestination.Status
                },
                precedingSteps = if (wizardStandalone) 0 else PAIRING_STAGES,
            )

        ChildDestination.Status ->
            ChildStatusScreen(
                platformVariant = platformServices.variant,
                disableEnforcement = killSwitch.disableEnforcement,
                killSwitch = killSwitch,
                apkInstaller = apkInstaller,
                versionName = versionName,
                released = released,
                protectionGranted = controller.grantedCount,
                protectionTotal = controller.total,
                summary = summary,
                tasksStore = tasksStore,
                identity = identity,
                approvalsRemote = approvalsRemote,
                onOpenProfile = { destination = ChildDestination.Profile },
                onOpenHealth = { destination = ChildDestination.Health },
                onOpenTransparency = { destination = ChildDestination.Transparency },
                onOpenTasks = { destination = ChildDestination.Tasks },
                onOpenStats = { destination = ChildDestination.Stats },
                onEnterParentCode = {
                    context.startActivity(
                        Intent(context, RemovalActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                },
            )

        ChildDestination.Tasks ->
            ChildTasksScreen(
                tasksStore = tasksStore,
                tasksSyncer = tasksSyncer,
                identity = identity,
                approvalsRemote = approvalsRemote,
                bonusMinutesToday = bonusMinutes,
                onClose = { destination = ChildDestination.Status },
            )

        ChildDestination.Stats ->
            ChildStatsScreen(
                summary = summary,
                onClose = { destination = ChildDestination.Status },
            )

        ChildDestination.Profile -> {
            var me by remember { mutableStateOf<FamilyMember?>(null) }
            LaunchedEffect(Unit) {
                val familyId = identity.familyId() ?: return@LaunchedEffect
                val memberId = identity.memberId() ?: return@LaunchedEffect
                me = familyRepository.members(familyId).getOrNull()?.firstOrNull { it.id == memberId }
            }
            ProfileEditorScreen(
                me = me,
                familyRepository = familyRepository,
                avatarRemote = avatarRemote,
                title = "Мой профиль",
                namePlaceholder = "Твоё имя",
                onSaved = { destination = ChildDestination.Status },
                onCancel = { destination = ChildDestination.Status },
            )
        }

        ChildDestination.Health ->
            ProtectionHealthScreen(
                controller = controller,
                backgroundOptionLabel = backgroundLabel,
                onStartWizard = {
                    wizardStandalone = true
                    destination = ChildDestination.Wizard
                },
            )

        ChildDestination.Transparency -> TransparencyScreen()
    }
}
