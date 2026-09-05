package app.kite.parent.family

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import app.kite.core.avatar.AvatarRemote
import app.kite.core.design.LocalAppColors
import app.kite.core.design.LocalAppTypography
import app.kite.core.design.components.AppDialog
import app.kite.core.design.components.AppIcon
import app.kite.core.design.components.AvatarPreset
import app.kite.core.design.components.InsetGroup
import app.kite.core.design.components.InsetGroupedList
import app.kite.core.design.components.KiteAvatar
import app.kite.core.design.components.KiteIcons
import app.kite.core.design.components.ProfileEditorScreen
import app.kite.core.design.components.rowIcon
import app.kite.core.family.ChildDevice
import app.kite.core.family.ChildDeviceRemote
import app.kite.core.family.Family
import app.kite.core.family.FamilyMember
import app.kite.core.family.FamilyRepository
import app.kite.core.family.PairingInvite
import app.kite.core.family.PairingKind
import app.kite.parent.rules.SubScreenHeader
import kotlinx.coroutines.launch

/**
 * «Семья» (from Ещё, as in Kids360): the children with «Добавить ребёнка» (staged setup), the
 * adults with «Добавить взрослого» (invite code/QR). Tapping a child offers removal from the
 * family after a confirmation; the last parent cannot be removed by anyone.
 */
@Composable
fun FamilyScreen(
    family: Family,
    members: List<FamilyMember>,
    myUserId: String?,
    familyRepository: FamilyRepository,
    childDeviceRemote: ChildDeviceRemote,
    avatarRemote: AvatarRemote,
    onMembersChanged: () -> Unit,
    onBack: () -> Unit,
) {
    val colors = LocalAppColors.current
    val typography = LocalAppTypography.current
    val scope = rememberCoroutineScope()
    var addingChild by remember { mutableStateOf(false) }
    var parentInvite by remember { mutableStateOf<PairingInvite?>(null) }
    var creatingInvite by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<FamilyMember?>(null) }
    var editing by remember { mutableStateOf<FamilyMember?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var devices by remember(family.id) { mutableStateOf<Map<String, ChildDevice>>(emptyMap()) }

    LaunchedEffect(family.id, members.size) {
        devices = childDeviceRemote.forFamily(family.id).getOrNull()
            .orEmpty()
            .associateBy { it.memberId }
    }

    editing?.let { member ->
        ProfileEditorScreen(
            me = member,
            familyRepository = familyRepository,
            avatarRemote = avatarRemote,
            title = if (member.userId == myUserId) "Мой профиль" else member.displayName.ifBlank { "Профиль" },
            namePlaceholder = if (member.isParent) "Имя родителя" else "Имя ребёнка",
            onSaved = {
                editing = null
                onMembersChanged()
            },
            onCancel = { editing = null },
        )
        return
    }
    if (addingChild) {
        AddChildFlow(
            familyId = family.id,
            knownMemberIds = members.map { it.id }.toSet(),
            familyRepository = familyRepository,
            onDone = {
                addingChild = false
                onMembersChanged()
            },
            onCancel = {
                addingChild = false
                onMembersChanged()
            },
        )
        return
    }
    parentInvite?.let { invite ->
        InviteScreen(
            invite = invite,
            onClose = {
                parentInvite = null
                onMembersChanged()
            },
        )
        return
    }
    removing?.let { member ->
        AppDialog(
            title = "Удалить ${member.displayName.ifBlank { "ребёнка" }} из семьи?",
            message = "Kite Jr на телефоне перестанет присылать данные, правила снимутся. Телефон нужно будет привязать заново.",
            confirmText = "Удалить",
            destructive = true,
            onConfirm = {
                removing = null
                scope.launch {
                    familyRepository.deleteMember(member.id)
                        .onSuccess { onMembersChanged() }
                        .onFailure { error = it.message ?: "Не удалось удалить" }
                }
            },
            onDismiss = { removing = null },
        )
    }

    val children = members.filterNot { it.isParent }
    val adults = members.filter { it.isParent }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.bgGrouped)
            .safeContentPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        SubScreenHeader(title = "Семья", onBack = onBack)
        Spacer(Modifier.height(20.dp))
        InsetGroupedList {
            InsetGroup(
                header = "Дети",
                footer = if (children.isEmpty()) "Настройте телефон ребёнка: установить Kite Jr, ввести код, выдать разрешения." else null,
            ) {
                children.forEach { child ->
                    custom(separatorInset = 68.dp) {
                        val device = devices[child.id]
                        MemberRow(
                            child,
                            onRemove = { removing = child },
                            subtitle = device?.model,
                            warning = device?.isHealthy == false,
                            known = device != null,
                            onEdit = { editing = child },
                        )
                    }
                }
                row(title = "Добавить ребёнка", icon = rowIcon(KiteIcons.Plus, colors.accent), showChevron = true, onClick = {
                    addingChild =
                        true
                })
            }
            InsetGroup(header = "Взрослые", footer = "Второй родитель управляет теми же детьми со своего телефона.") {
                adults.forEach { adult ->
                    custom(separatorInset = 68.dp) {
                        val role = if (adult.userId == myUserId) "Вы" else "Родитель"
                        val phone = devices[adult.id]?.model
                        MemberRow(
                            adult,
                            onRemove = if (adult.userId != myUserId && adults.size > 1) ({ removing = adult }) else null,
                            subtitle = listOfNotNull(role, phone).joinToString(" · "),
                            onEdit = if (adult.userId == myUserId) ({ editing = adult }) else null,
                        )
                    }
                }
                row(
                    title = if (creatingInvite) "Создаём код…" else "Добавить взрослого",
                    icon = rowIcon(KiteIcons.Users, colors.success),
                    showChevron = true,
                    enabled = !creatingInvite,
                    onClick = {
                        scope.launch {
                            creatingInvite = true
                            familyRepository.createInvite(family.id, PairingKind.INVITE_PARENT)
                                .onSuccess { parentInvite = it }
                                .onFailure { error = it.message }
                            creatingInvite = false
                        }
                    },
                )
            }
        }
        error?.let {
            Spacer(Modifier.height(12.dp))
            Text(
                text = it,
                style = typography.footnote,
                color = colors.danger,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(32.dp))
    }
}

/**
 * A family member: the row opens their name and picture, the trash can removes them. Tapping
 * a name must never be the destructive action, so removal stays behind its own icon.
 */
@Composable
private fun MemberRow(
    member: FamilyMember,
    onRemove: (() -> Unit)?,
    subtitle: String? = null,
    warning: Boolean = false,
    known: Boolean = false,
    onEdit: (() -> Unit)? = null,
) {
    val colors = LocalAppColors.current
    val typography = LocalAppTypography.current
    val statusColor = if (warning) colors.warning else colors.success.takeIf { known }
    Row(
        Modifier
            .fillMaxWidth()
            .then(
                if (onEdit == null) {
                    Modifier
                } else {
                    Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onEdit)
                },
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            KiteAvatar(preset = AvatarPreset.byId(member.avatarKind), size = 40.dp, avatarUrl = member.avatarUrl)
            // Status dot on the avatar, the way family apps mark a device that needs attention:
            // green when the phone is fully set up, amber when something is still missing.
            if (!member.isParent && statusColor != null) {
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .size(13.dp)
                        .clip(CircleShape)
                        .background(colors.bgBase),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.size(9.dp).clip(CircleShape).background(statusColor))
                }
            }
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(
                text = member.displayName.ifBlank {
                    if (member.isParent) "Родитель" else "Ребёнок"
                },
                style = typography.body,
                color = colors.textPrimary,
            )
            Text(
                text =
                when {
                    warning -> "Не всё настроено"
                    subtitle != null -> subtitle
                    member.isParent -> "Родитель"
                    else -> "Ребёнок"
                },
                style = typography.footnote,
                color = if (warning) colors.warning else colors.textSecondary,
                maxLines = 1,
            )
        }
        if (onEdit != null) {
            AppIcon(icon = KiteIcons.ChevronRight, tint = colors.textTertiary, size = 16.dp)
        }
        if (onRemove != null) {
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onRemove),
                contentAlignment = Alignment.Center,
            ) {
                AppIcon(icon = KiteIcons.Trash, tint = colors.textTertiary, size = 18.dp)
            }
        }
    }
}
