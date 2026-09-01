package app.kite.parent.family

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.kite.core.design.LocalAppColors
import app.kite.core.design.LocalAppTypography
import app.kite.core.design.components.AppButton
import app.kite.core.design.components.AppButtonStyle
import app.kite.core.design.components.AppTextField
import app.kite.core.design.components.AvatarPreset
import app.kite.core.design.components.ProfileSetup
import app.kite.core.family.FamilyRepository
import kotlinx.coroutines.launch

/**
 * Second parent joins an existing family with the 6-digit invite_parent code. The invite
 * is previewed first: a pair_child code typed here is rejected — otherwise the server
 * would add this account with the child role.
 */
@Composable
fun JoinFamilyScreen(familyRepository: FamilyRepository, onJoined: () -> Unit, onBack: () -> Unit) {
    val colors = LocalAppColors.current
    val typography = LocalAppTypography.current
    val scope = rememberCoroutineScope()

    var nickname by remember { mutableStateOf("") }
    var avatar by remember { mutableStateOf(AvatarPreset.KITE) }
    var code by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

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
        Spacer(Modifier.height(24.dp))
        Text(text = "Присоединиться к семье", style = typography.title1, color = colors.textPrimary)
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Введите код приглашения от другого родителя",
            style = typography.subhead,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
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
        Spacer(Modifier.height(20.dp))
        AppTextField(
            value = code,
            onValueChange = {
                code = it.filter(Char::isDigit).take(6)
                error = null
            },
            placeholder = "6 цифр",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        if (error != null) {
            Spacer(Modifier.height(12.dp))
            Text(text = error!!, style = typography.subhead, color = colors.danger, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(24.dp))
        AppButton(
            text = "Присоединиться",
            loading = busy,
            onClick = {
                when {
                    nickname.isBlank() -> error = "Введите имя"
                    code.length != 6 -> error = "Введите 6-значный код"
                    else ->
                        scope.launch {
                            busy = true
                            error = null
                            familyRepository.pairingPreview(token = null, code = code)
                                .onSuccess { found ->
                                    if (found.isChildInvite) {
                                        busy = false
                                        error = "Это код для привязки ребёнка — введите его в Kite Jr"
                                    } else {
                                        familyRepository.redeemPairing(
                                            token = null,
                                            code = code,
                                            displayName = nickname.trim(),
                                            avatarKind = avatar.id,
                                        )
                                            .onSuccess {
                                                busy = false
                                                onJoined()
                                            }
                                            .onFailure {
                                                busy = false
                                                error = it.message ?: "Код не подошёл"
                                            }
                                    }
                                }
                                .onFailure {
                                    busy = false
                                    error = it.message ?: "Код не подошёл"
                                }
                        }
                }
            },
        )
        Spacer(Modifier.height(8.dp))
        AppButton(text = "Назад", style = AppButtonStyle.Plain, onClick = onBack)
        Spacer(Modifier.height(24.dp))
    }
}
