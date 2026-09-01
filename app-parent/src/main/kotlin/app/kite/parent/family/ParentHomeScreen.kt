package app.kite.parent.family

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.kite.core.auth.SessionManager
import app.kite.core.design.LocalAppColors
import app.kite.core.design.LocalAppTypography
import app.kite.core.design.components.AppButton
import app.kite.core.design.components.AppButtonStyle
import app.kite.core.design.components.AppSpinner
import app.kite.core.design.components.AvatarPreset
import app.kite.core.design.components.KiteAvatar
import app.kite.core.design.components.ProfileSetup
import app.kite.core.family.Family
import app.kite.core.family.FamilyMember
import app.kite.core.family.FamilyRepository
import app.kite.core.family.PairingInvite
import app.kite.core.family.PairingKind
import app.kite.core.secure.SecureStore
import kotlinx.coroutines.launch

private sealed interface HomeState {
    data object Loading : HomeState

    data object NeedsFamily : HomeState

    data class Ready(val family: Family) : HomeState

    data class Failed(val message: String) : HomeState
}

/**
 * Home after sign-in: loads the user's families. None yet → create one (profile + name).
 * Otherwise the family screen. All server calls go through [FamilyRepository]; RLS guards.
 */
@Composable
fun ParentHomeScreen(familyRepository: FamilyRepository, sessionManager: SessionManager, secureStore: SecureStore) {
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<HomeState>(HomeState.Loading) }
    var reloadKey by remember { mutableStateOf(0) }

    LaunchedEffect(reloadKey) {
        state = HomeState.Loading
        familyRepository.myFamilies()
            .onSuccess { families -> state = families.firstOrNull()?.let { HomeState.Ready(it) } ?: HomeState.NeedsFamily }
            .onFailure { state = HomeState.Failed(it.message ?: "Ошибка загрузки") }
    }

    when (val s = state) {
        HomeState.Loading -> CenterSpinner()
        HomeState.NeedsFamily ->
            CreateFamilyScreen(
                familyRepository = familyRepository,
                onCreated = { reloadKey++ },
            )
        is HomeState.Ready ->
            FamilyScreen(
                family = s.family,
                familyRepository = familyRepository,
                secureStore = secureStore,
                onSignOut = { scope.launch { sessionManager.signOut() } },
            )
        is HomeState.Failed ->
            RetryScreen(message = s.message, onRetry = { reloadKey++ })
    }
}

@Composable
private fun CreateFamilyScreen(familyRepository: FamilyRepository, onCreated: () -> Unit) {
    val colors = LocalAppColors.current
    val typography = LocalAppTypography.current
    val scope = rememberCoroutineScope()
    var nickname by remember { mutableStateOf("") }
    var avatar by remember { mutableStateOf(AvatarPreset.KITE) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

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
            onSelect = { avatar = it },
            nicknamePlaceholder = "Ваше имя",
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
                        .onSuccess { onCreated() }
                        .onFailure { error = it.message ?: "Ошибка" }
                    busy = false
                }
            },
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun FamilyScreen(family: Family, familyRepository: FamilyRepository, secureStore: SecureStore, onSignOut: () -> Unit) {
    val colors = LocalAppColors.current
    val typography = LocalAppTypography.current
    val scope = rememberCoroutineScope()
    var members by remember { mutableStateOf<List<FamilyMember>>(emptyList()) }
    var invite by remember { mutableStateOf<PairingInvite?>(null) }
    var creatingInvite by remember { mutableStateOf(false) }
    var codeMember by remember { mutableStateOf<FamilyMember?>(null) }

    LaunchedEffect(family.id) {
        familyRepository.members(family.id).onSuccess { members = it }
    }

    invite?.let { active ->
        InviteScreen(invite = active, onClose = { invite = null })
        return
    }

    codeMember?.let { child ->
        ApprovalCodeScreen(
            member = child,
            familyRepository = familyRepository,
            secureStore = secureStore,
            onClose = { codeMember = null },
        )
        return
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.bgGrouped)
            .safeContentPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(text = "Семья", style = typography.largeTitle, color = colors.textPrimary, modifier = Modifier.weight(1f))
            AppButton(text = "Выйти", style = AppButtonStyle.Plain, onClick = onSignOut)
        }
        Spacer(Modifier.height(16.dp))

        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(colors.bgBase),
        ) {
            members.forEachIndexed { index, member ->
                // A child row opens the offline approval code; parent rows are inert.
                MemberRow(member, onClick = if (member.isParent) null else ({ codeMember = member }))
                if (index < members.lastIndex) {
                    Box(Modifier.padding(start = 68.dp).fillMaxWidth().height(1.dp).background(colors.separator))
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        AppButton(
            text = "Добавить ребёнка",
            loading = creatingInvite,
            onClick = {
                scope.launch {
                    creatingInvite = true
                    familyRepository.createInvite(family.id, PairingKind.PAIR_CHILD)
                        .onSuccess { invite = it }
                    creatingInvite = false
                }
            },
        )
        Spacer(Modifier.height(10.dp))
        AppButton(
            text = "Пригласить родителя",
            style = AppButtonStyle.Tinted,
            onClick = {
                scope.launch {
                    familyRepository.createInvite(family.id, PairingKind.INVITE_PARENT).onSuccess { invite = it }
                }
            },
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun MemberRow(member: FamilyMember, onClick: (() -> Unit)? = null) {
    val colors = LocalAppColors.current
    val typography = LocalAppTypography.current
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KiteAvatar(preset = AvatarPreset.byId(member.avatarKind), size = 44.dp)
        Spacer(Modifier.height(0.dp))
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(
                text = member.displayName.ifBlank { if (member.isParent) "Родитель" else "Ребёнок" },
                style = typography.headline,
                color = colors.textPrimary,
            )
            Text(
                text = if (member.isParent) "Родитель" else "Ребёнок",
                style = typography.subhead,
                color = colors.textSecondary,
            )
        }
        if (onClick != null) {
            Text(text = "›", style = typography.title1, color = colors.textSecondary)
        }
    }
}

@Composable
private fun CenterSpinner() {
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
        Modifier.fillMaxSize().background(colors.bgGrouped).safeContentPadding().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = message, style = typography.body, color = colors.textSecondary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        AppButton(text = "Повторить", onClick = onRetry)
    }
}
