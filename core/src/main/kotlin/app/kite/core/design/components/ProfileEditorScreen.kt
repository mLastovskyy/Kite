package app.kite.core.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.kite.core.avatar.AvatarRemote
import app.kite.core.design.LocalAppColors
import app.kite.core.design.LocalAppTypography
import app.kite.core.family.FamilyMember
import app.kite.core.family.FamilyRepository
import kotlinx.coroutines.launch

@Composable
fun ProfileEditorScreen(
    me: FamilyMember?,
    familyRepository: FamilyRepository,
    avatarRemote: AvatarRemote,
    onSaved: () -> Unit,
    onCancel: () -> Unit,
    title: String = "Профиль",
    namePlaceholder: String = "Ваше имя",
) {
    val colors = LocalAppColors.current
    val typography = LocalAppTypography.current
    val scope = rememberCoroutineScope()

    // Keyed on the member: the profile is often opened while the row is still loading, and
    // an unkeyed state would keep the empty first frame after it arrives.
    var nickname by remember(me?.id) { mutableStateOf(me?.displayName.orEmpty()) }
    var avatar by remember(me?.id) {
        mutableStateOf(AvatarPreset.entries.firstOrNull { it.id == me?.avatarKind } ?: AvatarPreset.entries.random())
    }
    var customUrl by remember(me?.id) { mutableStateOf(me?.avatarUrl) }
    var photoCleared by remember { mutableStateOf(false) }
    var showCrop by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    if (showCrop) {
        AvatarCropSheet(
            onCancel = { showCrop = false },
            onCropped = { bytes ->
                showCrop = false
                scope.launch {
                    busy = true
                    avatarRemote.upload(bytes)
                        .onSuccess {
                            customUrl = it
                            photoCleared = false
                        }
                        .onFailure { error = it.message ?: "Не удалось загрузить фото" }
                    busy = false
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
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(8.dp))
        BackHeader(title = title, onBack = onCancel)
        Spacer(Modifier.height(20.dp))
        ProfileSetup(
            nickname = nickname,
            onNicknameChange = {
                nickname = it
                error = null
            },
            selected = avatar,
            onSelect = {
                avatar = it
                if (customUrl != null) photoCleared = true
                customUrl = null
            },
            nicknamePlaceholder = namePlaceholder,
            customAvatarUrl = customUrl,
            onPickPhoto = { showCrop = true },
        )
        if (error != null) {
            Spacer(Modifier.height(12.dp))
            Text(text = error!!, style = typography.subhead, color = colors.danger, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(28.dp))
        AppButton(
            text = "Сохранить",
            loading = busy,
            onClick = {
                if (nickname.isBlank()) {
                    error = "Введите имя"
                    return@AppButton
                }
                scope.launch {
                    busy = true
                    error = null
                    familyRepository.updateMyProfile(
                        displayName = nickname.trim(),
                        avatarKind = avatar.id,
                        avatarUrl = customUrl,
                        clearAvatarUrl = photoCleared && customUrl == null,
                    )
                        .onSuccess { onSaved() }
                        .onFailure { error = it.message ?: "Не удалось сохранить" }
                    busy = false
                }
            },
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun BackHeader(title: String, onBack: () -> Unit, trailing: (@Composable () -> Unit)? = null) {
    val colors = LocalAppColors.current
    val typography = LocalAppTypography.current
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onBack)
                    .padding(top = 8.dp, bottom = 8.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppIcon(icon = KiteIcons.ChevronRight, tint = colors.accent, size = 22.dp, modifier = Modifier.rotate(180f))
                Text(text = "Назад", style = typography.body, color = colors.accent)
            }
            Spacer(Modifier.weight(1f))
            trailing?.invoke()
        }
        Spacer(Modifier.height(4.dp))
        Text(text = title, style = typography.title1, color = colors.textPrimary)
    }
}
