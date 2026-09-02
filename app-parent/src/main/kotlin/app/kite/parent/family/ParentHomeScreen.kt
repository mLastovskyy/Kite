package app.kite.parent.family

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kite.core.appearance.AppearanceRepository
import app.kite.core.approval.ApprovalsRemote
import app.kite.core.auth.SessionManager
import app.kite.core.avatar.AvatarRemote
import app.kite.core.commands.CommandsRemote
import app.kite.core.design.LocalAppColors
import app.kite.core.design.LocalAppTypography
import app.kite.core.design.components.AppButton
import app.kite.core.design.components.AppButtonStyle
import app.kite.core.design.components.AppSpinner
import app.kite.core.design.components.AvatarCropSheet
import app.kite.core.design.components.AvatarPreset
import app.kite.core.design.components.ProfileSetup
import app.kite.core.family.Family
import app.kite.core.family.FamilyRepository
import app.kite.core.killswitch.KillSwitchRepository
import app.kite.core.location.DeviceLocationRemote
import app.kite.core.rules.RulesRemote
import app.kite.core.secure.SecureStore
import app.kite.core.tasks.TasksRemote
import app.kite.core.update.ApkInstaller
import app.kite.core.usage.UsageRemote
import app.kite.parent.auth.PinLock
import app.kite.parent.auth.PinSetupScreen
import app.kite.parent.home.MainTabs
import app.kite.parent.onboarding.ParentOnboarding
import kotlinx.coroutines.launch

private sealed interface HomeState {
    data object Loading : HomeState

    data object NeedsFamily : HomeState

    data class Ready(val family: Family) : HomeState

    data class Failed(val message: String) : HomeState
}

/**
 * Home after the session exists: loads the user's families. None yet → onboarding (profile +
 * family, notifications, mandatory PIN). Otherwise the tabbed home, preceded by the PIN setup
 * whenever one is requested. All server calls go through [FamilyRepository]; RLS guards.
 */
@Composable
fun ParentHomeScreen(
    familyRepository: FamilyRepository,
    sessionManager: SessionManager,
    secureStore: SecureStore,
    usageRemote: UsageRemote,
    rulesRemote: RulesRemote,
    commandsRemote: CommandsRemote,
    locationRemote: DeviceLocationRemote,
    approvalsRemote: ApprovalsRemote,
    tasksRemote: TasksRemote,
    avatarRemote: AvatarRemote,
    pinLock: PinLock,
    appearance: AppearanceRepository,
    apkInstaller: ApkInstaller,
    killSwitch: KillSwitchRepository,
    versionName: String,
) {
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<HomeState>(HomeState.Loading) }
    var reloadKey by remember { mutableStateOf(0) }
    val setupRequested by pinLock.setupRequested.collectAsStateWithLifecycle()

    LaunchedEffect(reloadKey) {
        state = HomeState.Loading
        familyRepository.myFamilies()
            .onSuccess { families -> state = families.firstOrNull()?.let { HomeState.Ready(it) } ?: HomeState.NeedsFamily }
            .onFailure { state = HomeState.Failed(it.message ?: "Ошибка загрузки") }
    }

    when (val s = state) {
        HomeState.Loading -> CenterSpinner()
        HomeState.NeedsFamily ->
            ParentOnboarding(
                familyRepository = familyRepository,
                avatarRemote = avatarRemote,
                pinLock = pinLock,
                onFinished = { reloadKey++ },
            )
        is HomeState.Ready ->
            if (setupRequested) {
                PinSetupScreen(pinLock = pinLock, onDone = { pinLock.dismissSetup() })
            } else {
                MainTabs(
                    family = s.family,
                    familyRepository = familyRepository,
                    sessionManager = sessionManager,
                    secureStore = secureStore,
                    usageRemote = usageRemote,
                    rulesRemote = rulesRemote,
                    commandsRemote = commandsRemote,
                    locationRemote = locationRemote,
                    approvalsRemote = approvalsRemote,
                    tasksRemote = tasksRemote,
                    avatarRemote = avatarRemote,
                    pinLock = pinLock,
                    appearance = appearance,
                    apkInstaller = apkInstaller,
                    killSwitch = killSwitch,
                    versionName = versionName,
                    onSignOut = { scope.launch { sessionManager.signOut() } },
                )
            }
        is HomeState.Failed ->
            RetryScreen(message = s.message, onRetry = { reloadKey++ })
    }
}

/** Profile + family creation — the first onboarding step, also reachable when no family exists. */
@Composable
internal fun CreateFamilyScreen(
    familyRepository: FamilyRepository,
    avatarRemote: AvatarRemote,
    onCreated: () -> Unit,
    onJoinInstead: () -> Unit,
) {
    val colors = LocalAppColors.current
    val typography = LocalAppTypography.current
    val scope = rememberCoroutineScope()
    var nickname by remember { mutableStateOf("") }
    var avatar by remember { mutableStateOf(AvatarPreset.entries.random()) }
    var customUrl by remember { mutableStateOf<String?>(null) }
    var showCrop by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    if (showCrop) {
        AvatarCropSheet(
            onCancel = { showCrop = false },
            onCropped = { bytes ->
                showCrop = false
                scope.launch {
                    avatarRemote.upload(bytes)
                        .onSuccess { customUrl = it }
                        .onFailure { error = it.message ?: "Не удалось загрузить фото" }
                }
            },
        )
        return
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.bgGrouped)
            .safeContentPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))
        Text(text = "Ваш профиль", style = typography.title1, color = colors.textPrimary)
        Spacer(Modifier.height(6.dp))
        Text(text = "Как вас увидят в семье", style = typography.subhead, color = colors.textSecondary)
        Spacer(Modifier.height(28.dp))
        ProfileSetup(
            nickname = nickname,
            onNicknameChange = {
                nickname = it
                error = null
            },
            selected = avatar,
            onSelect = {
                avatar = it
                customUrl = null
            },
            nicknamePlaceholder = "Ваше имя",
            customAvatarUrl = customUrl,
            onPickPhoto = { showCrop = true },
        )
        if (error != null) {
            Spacer(Modifier.height(12.dp))
            Text(text = error!!, style = typography.subhead, color = colors.danger, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(28.dp))
        AppButton(
            text = "Продолжить",
            loading = busy,
            onClick = {
                if (nickname.isBlank()) {
                    error = "Введите имя"
                    return@AppButton
                }
                scope.launch {
                    busy = true
                    error = null
                    familyRepository.createFamily(familyName = null, displayName = nickname.trim(), avatarKind = avatar.id)
                        .onSuccess {
                            customUrl?.let { url -> avatarRemote.setMemberAvatarUrl(url) }
                            onCreated()
                        }
                        .onFailure { error = it.message ?: "Ошибка" }
                    busy = false
                }
            },
        )
        Spacer(Modifier.height(8.dp))
        AppButton(text = "У меня есть код приглашения", style = AppButtonStyle.Plain, onClick = onJoinInstead)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
internal fun CenterSpinner() {
    val colors = LocalAppColors.current
    Box(Modifier.fillMaxSize().background(colors.bgGrouped), contentAlignment = Alignment.Center) {
        AppSpinner(color = colors.accent, size = 28.dp)
    }
}

@Composable
private fun RetryScreen(message: String, onRetry: () -> Unit) {
    val colors = LocalAppColors.current
    val typography = LocalAppTypography.current
    Column(
        Modifier
            .fillMaxSize()
            .background(colors.bgGrouped)
            .safeContentPadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = message, style = typography.body, color = colors.textSecondary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        AppButton(text = "Повторить", style = AppButtonStyle.Tinted, onClick = onRetry)
    }
}
