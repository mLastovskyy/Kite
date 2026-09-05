package app.kite.parent.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kite.core.appearance.AppearanceRepository
import app.kite.core.approval.ApprovalsRemote
import app.kite.core.apps.ChildAppsRemote
import app.kite.core.auth.AuthState
import app.kite.core.auth.SessionManager
import app.kite.core.avatar.AvatarRemote
import app.kite.core.commands.CommandsRemote
import app.kite.core.design.LocalAppColors
import app.kite.core.design.LocalAppTypography
import app.kite.core.design.components.AppButton
import app.kite.core.design.components.AppIcon
import app.kite.core.design.components.AvatarPreset
import app.kite.core.design.components.KiteAvatar
import app.kite.core.design.components.KiteIcons
import app.kite.core.family.ChildDeviceRemote
import app.kite.core.family.Family
import app.kite.core.family.FamilyMember
import app.kite.core.family.FamilyRepository
import app.kite.core.killswitch.KillSwitchRepository
import app.kite.core.location.DeviceLocationRemote
import app.kite.core.location.PlacesRemote
import app.kite.core.location.TrailRemote
import app.kite.core.push.PushDiagnostics
import app.kite.core.realtime.RealtimeTable
import app.kite.core.rules.RulesRemote
import app.kite.core.secure.SecureStore
import app.kite.core.tasks.TasksRemote
import app.kite.core.update.ApkInstaller
import app.kite.core.usage.UsageRemote
import app.kite.parent.auth.PinLock
import app.kite.parent.family.AddChildFlow
import app.kite.parent.family.CenterSpinner
import app.kite.parent.family.FamilyMapScreen
import app.kite.parent.family.FamilyScreen
import app.kite.parent.settings.SettingsScreen
import app.kite.parent.stats.StatisticsScreen
import app.kite.parent.tasks.TasksScreen

/** Kids360 tab set: Главная · Статистика · Задания · Карта · Ещё. */
enum class ParentTab(val label: String, val icon: Int) {
    Home("Главная", KiteIcons.House),
    Stats("Статистика", KiteIcons.ChartColumn),
    Tasks("Задания", KiteIcons.ListChecks),
    Map("Карта", KiteIcons.MapPin),
    More("Ещё", KiteIcons.Ellipsis),
}

/**
 * Signed-in home: an iOS-style tab bar over five sections. Members are loaded once here and
 * shared by every tab; the selected child is shared by Главная, Статистика and Задания.
 * «Семья» (from Ещё) and the staged «Добавить ребёнка» render over the tabs.
 */
@Composable
fun MainTabs(
    family: Family,
    familyRepository: FamilyRepository,
    sessionManager: SessionManager,
    secureStore: SecureStore,
    usageRemote: UsageRemote,
    rulesRemote: RulesRemote,
    commandsRemote: CommandsRemote,
    locationRemote: DeviceLocationRemote,
    placesRemote: PlacesRemote,
    trailRemote: TrailRemote,
    childAppsRemote: ChildAppsRemote,
    childDeviceRemote: ChildDeviceRemote,
    realtime: RealtimeTable,
    approvalsRemote: ApprovalsRemote,
    tasksRemote: TasksRemote,
    avatarRemote: AvatarRemote,
    pinLock: PinLock,
    pushDiagnostics: PushDiagnostics,
    appearance: AppearanceRepository,
    apkInstaller: ApkInstaller,
    killSwitch: KillSwitchRepository,
    versionName: String,
    onSignOut: () -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(ParentTab.Home) }
    var members by remember { mutableStateOf<List<FamilyMember>>(emptyList()) }
    var membersLoaded by remember { mutableStateOf(false) }
    var membersKey by remember { mutableIntStateOf(0) }
    var selectedChildId by rememberSaveable { mutableStateOf<String?>(null) }
    var familyOpen by rememberSaveable { mutableStateOf(false) }
    var addChildOpen by rememberSaveable { mutableStateOf(false) }
    var linkEmailRequested by remember { mutableStateOf(false) }
    // «Лимит на это приложение» from Статистика: Главная opens «Приложения» with this app.
    var pendingAppPackage by remember { mutableStateOf<String?>(null) }
    // Full-screen flows replace the tabs in place; the system back must close them, not the app.
    BackHandler(enabled = addChildOpen) {
        addChildOpen = false
        membersKey++
    }
    BackHandler(enabled = familyOpen && !addChildOpen) { familyOpen = false }

    LaunchedEffect(family.id, membersKey) {
        familyRepository.members(family.id).onSuccess {
            members = it
            membersLoaded = true
        }
    }

    // Observed, not read once: linking an email updates the session while these tabs are open.
    val authState by sessionManager.authState.collectAsStateWithLifecycle()
    val session = (authState as? AuthState.SignedIn)?.session
    val me = members.firstOrNull { it.userId == session?.userId }
    val children = members.filterNot { it.isParent }
    val selectedChild = children.firstOrNull { it.id == selectedChildId } ?: children.firstOrNull()

    if (addChildOpen) {
        AddChildFlow(
            familyId = family.id,
            knownMemberIds = members.map { it.id }.toSet(),
            familyRepository = familyRepository,
            onDone = { joined ->
                addChildOpen = false
                joined?.let { selectedChildId = it.id }
                membersKey++
            },
            onCancel = {
                addChildOpen = false
                membersKey++
            },
        )
        return
    }
    if (familyOpen) {
        FamilyScreen(
            family = family,
            members = members,
            myUserId = session?.userId,
            familyRepository = familyRepository,
            onMembersChanged = { membersKey++ },
            onBack = { familyOpen = false },
        )
        return
    }

    Column(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                // The tab bar owns the bottom inset; screens must not pad for it twice.
                .consumeWindowInsets(WindowInsets.navigationBars),
        ) {
            when (tab) {
                ParentTab.Home ->
                    when {
                        !membersLoaded -> CenterSpinner()
                        selectedChild == null -> NoChildHome(onAddChild = { addChildOpen = true })
                        else ->
                            ChildHomeScreen(
                                familyId = family.id,
                                children = children,
                                child = selectedChild,
                                onSelectChild = { selectedChildId = it.id },
                                anonymousAccount = session?.isAnonymous == true,
                                onLinkEmail = {
                                    linkEmailRequested = true
                                    tab = ParentTab.More
                                },
                                onOpenTasks = { tab = ParentTab.Tasks },
                                onOpenMap = { tab = ParentTab.Map },
                                openAppPackage = pendingAppPackage,
                                onOpenedApp = { pendingAppPackage = null },
                                usageRemote = usageRemote,
                                rulesRemote = rulesRemote,
                                commandsRemote = commandsRemote,
                                approvalsRemote = approvalsRemote,
                                locationRemote = locationRemote,
                                childAppsRemote = childAppsRemote,
                                childDeviceRemote = childDeviceRemote,
                                realtime = realtime,
                                familyRepository = familyRepository,
                                secureStore = secureStore,
                            )
                    }
                ParentTab.Stats ->
                    StatisticsScreen(
                        children = children,
                        selected = selectedChild,
                        onSelectChild = { selectedChildId = it.id },
                        usageRemote = usageRemote,
                        onLimitApp = { app ->
                            pendingAppPackage = app.packageName
                            tab = ParentTab.Home
                        },
                    )
                ParentTab.Tasks ->
                    TasksScreen(
                        familyId = family.id,
                        children = children,
                        selected = selectedChild,
                        onSelectChild = { selectedChildId = it.id },
                        tasksRemote = tasksRemote,
                        commandsRemote = commandsRemote,
                        approvalsRemote = approvalsRemote,
                    )
                ParentTab.Map ->
                    FamilyMapScreen(
                        familyId = family.id,
                        children = children,
                        selected = selectedChild,
                        onSelectChild = { selectedChildId = it.id },
                        locationRemote = locationRemote,
                        childDeviceRemote = childDeviceRemote,
                        commandsRemote = commandsRemote,
                        placesRemote = placesRemote,
                        versionName = versionName,
                    )
                ParentTab.More ->
                    SettingsScreen(
                        me = me,
                        email = session?.email,
                        childrenCount = children.size,
                        sessionManager = sessionManager,
                        familyRepository = familyRepository,
                        avatarRemote = avatarRemote,
                        pinLock = pinLock,
                        pushDiagnostics = pushDiagnostics,
                        appearance = appearance,
                        apkInstaller = apkInstaller,
                        killSwitch = killSwitch,
                        versionName = versionName,
                        openLinkEmail = linkEmailRequested,
                        onLinkEmailShown = { linkEmailRequested = false },
                        onOpenFamily = { familyOpen = true },
                        onProfileChanged = { membersKey++ },
                        onSignOut = onSignOut,
                    )
            }
        }
        TabBar(selected = tab, onSelect = { tab = it })
    }
}

/** Главная before the first child: one thing to do. */
@Composable
private fun NoChildHome(onAddChild: () -> Unit) {
    val colors = LocalAppColors.current
    val typography = LocalAppTypography.current
    Column(
        Modifier
            .fillMaxSize()
            .background(colors.bgGrouped)
            .safeContentPadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(12.dp))
        Text(text = "Главная", style = typography.largeTitle, color = colors.textPrimary, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.weight(1f))
        KiteAvatar(preset = AvatarPreset.HEART, size = 96.dp)
        Spacer(Modifier.height(20.dp))
        Text(text = "Настроим телефон ребёнка", style = typography.title2, color = colors.textPrimary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Kite уже установлен у вас. Осталось поставить Kite Jr на телефон ребёнка и ввести код.",
            style = typography.body,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.weight(1f))
        AppButton(text = "Настроить телефон ребёнка", onClick = onAddChild)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun TabBar(selected: ParentTab, onSelect: (ParentTab) -> Unit) {
    val colors = LocalAppColors.current
    val typography = LocalAppTypography.current
    Column(Modifier.fillMaxWidth().background(colors.bgBase)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.separator))
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(top = 6.dp, bottom = 8.dp),
        ) {
            ParentTab.entries.forEach { tab ->
                val active = tab == selected
                val tint = if (active) colors.accent else colors.textTertiary
                Column(
                    Modifier
                        .weight(1f)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onSelect(tab) },
                        )
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    AppIcon(icon = tab.icon, tint = tint, size = 24.dp)
                    Spacer(Modifier.height(3.dp))
                    Text(text = tab.label, style = typography.caption, color = tint, maxLines = 1)
                }
            }
        }
    }
}
