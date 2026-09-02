package app.kite.child.pairing

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.kite.core.auth.AuthState
import app.kite.core.auth.SessionManager
import app.kite.core.avatar.AvatarRemote
import app.kite.core.design.LocalAppColors
import app.kite.core.design.LocalAppTypography
import app.kite.core.design.components.AppButton
import app.kite.core.design.components.AppButtonStyle
import app.kite.core.design.components.AppTextField
import app.kite.core.design.components.AvatarCropSheet
import app.kite.core.design.components.AvatarPreset
import app.kite.core.design.components.ProfileSetup
import app.kite.core.family.FamilyRepository
import app.kite.core.family.PairingPreview
import app.kite.core.family.PairingTokens
import kotlinx.coroutines.launch
import java.util.Base64

private enum class PairStep { Enter, Scan, Consent }

/**
 * Child-side pairing (Kite Jr). The child sets their name + avatar, then either scans the
 * parent's QR (token) or types the 6-digit code. Before consent the invite is previewed
 * (pairing_preview) so the MANDATORY consent screen can name the parent; it lists exactly
 * what will be visible — silent pairing would make this stalkerware. On agreement the
 * device redeems the invite together with a freshly generated offline-approval TOTP
 * secret; [onPaired] receives the family id and that secret (base64) for local storage.
 */
@Composable
fun ChildPairingScreen(
    familyRepository: FamilyRepository,
    sessionManager: SessionManager,
    avatarRemote: AvatarRemote,
    onPaired: (String, String) -> Unit,
) {
    val colors = LocalAppColors.current
    val typography = LocalAppTypography.current
    val scope = rememberCoroutineScope()

    var step by remember { mutableStateOf(PairStep.Enter) }
    var name by remember { mutableStateOf("") }
    var avatar by remember { mutableStateOf(AvatarPreset.entries.random()) }
    var customUrl by remember { mutableStateOf<String?>(null) }
    var showCrop by remember { mutableStateOf(false) }
    var code by remember { mutableStateOf("") }
    var token by remember { mutableStateOf<String?>(null) }
    var preview by remember { mutableStateOf<PairingPreview?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    // The invite must be previewed under a session; the anonymous sign-in from a failed
    // attempt is reused so retries do not mint extra anonymous users.
    suspend fun ensureSession(): Boolean {
        if (sessionManager.authState.value is AuthState.SignedIn) return true
        val auth = sessionManager.signInAnonymously()
        if (auth.isFailure) {
            error = auth.exceptionOrNull()?.message ?: "Не удалось подключиться"
            return false
        }
        return true
    }

    fun startPreview() {
        scope.launch {
            busy = true
            error = null
            if (!ensureSession()) {
                busy = false
                token = null
                return@launch
            }
            val scanned = token
            familyRepository.pairingPreview(token = scanned, code = if (scanned == null) code else null)
                .onSuccess { found ->
                    busy = false
                    if (found.isChildInvite) {
                        preview = found
                        step = PairStep.Consent
                    } else {
                        token = null
                        error = "Это приглашение для родителя, не для ребёнка"
                    }
                }
                .onFailure {
                    busy = false
                    token = null
                    error = it.message ?: "Код не подошёл"
                }
        }
    }

    if (showCrop) {
        // Uploading needs a session; the same anonymous one is reused for the pairing itself.
        AvatarCropSheet(
            onCancel = { showCrop = false },
            onCropped = { bytes ->
                showCrop = false
                scope.launch {
                    busy = true
                    if (ensureSession()) {
                        avatarRemote.upload(bytes)
                            .onSuccess { customUrl = it }
                            .onFailure { error = it.message ?: "Не удалось загрузить фото" }
                    }
                    busy = false
                }
            },
        )
        return
    }

    when (step) {
        PairStep.Enter ->
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
                Spacer(Modifier.height(20.dp))
                Text(text = "Привязка к родителю", style = typography.title1, color = colors.textPrimary)
                Spacer(Modifier.height(24.dp))
                ProfileSetup(
                    nickname = name,
                    onNicknameChange = {
                        name = it
                        error = null
                    },
                    selected = avatar,
                    onSelect = {
                        avatar = it
                        customUrl = null
                    },
                    nicknamePlaceholder = "Имя ребёнка",
                    customAvatarUrl = customUrl,
                    onPickPhoto = { showCrop = true },
                )
                Spacer(Modifier.height(20.dp))
                Text(text = "Код от родителя", style = typography.subhead, color = colors.textSecondary)
                Spacer(Modifier.height(8.dp))
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
                    text = "Далее",
                    loading = busy,
                    onClick = {
                        when {
                            name.isBlank() -> error = "Введите имя"
                            code.length != 6 -> error = "Введите 6-значный код"
                            else -> startPreview()
                        }
                    },
                )
                Spacer(Modifier.height(8.dp))
                AppButton(
                    text = "Сканировать QR-код",
                    style = AppButtonStyle.Plain,
                    onClick = {
                        if (name.isBlank()) {
                            error = "Введите имя"
                        } else {
                            error = null
                            step = PairStep.Scan
                        }
                    },
                )
                Spacer(Modifier.height(24.dp))
            }

        PairStep.Scan ->
            QrScanScreen(
                onFound = {
                    token = it
                    step = PairStep.Enter
                    startPreview()
                },
                onCancel = { step = PairStep.Enter },
            )

        PairStep.Consent ->
            ConsentScreen(
                parentName = preview?.inviterName?.takeIf { it.isNotBlank() } ?: preview?.familyName,
                busy = busy,
                error = error,
                onBack = {
                    step = PairStep.Enter
                    token = null
                    error = null
                },
                onAgree = {
                    scope.launch {
                        busy = true
                        error = null
                        if (!ensureSession()) {
                            busy = false
                            return@launch
                        }
                        // The offline-approval secret is born here, on the child device,
                        // and travels to the parent only through the redeem call (TLS).
                        val secretBase64 = Base64.getEncoder().encodeToString(PairingTokens.newSharedSecret())
                        val scanned = token
                        familyRepository.redeemPairing(
                            token = scanned,
                            code = if (scanned == null) code else null,
                            displayName = name.trim(),
                            avatarKind = avatar.id,
                            totpSecretBase64 = secretBase64,
                        )
                            .onSuccess { familyId ->
                                // The member row exists now; attach the photo picked earlier.
                                customUrl?.let { url -> avatarRemote.setMemberAvatarUrl(url) }
                                busy = false
                                onPaired(familyId, secretBase64)
                            }
                            .onFailure {
                                busy = false
                                error = it.message ?: "Код не подошёл"
                                token = null
                                step = PairStep.Enter
                            }
                    }
                },
            )
    }
}

@Composable
private fun ConsentScreen(parentName: String?, busy: Boolean, error: String?, onBack: () -> Unit, onAgree: () -> Unit) {
    val colors = LocalAppColors.current
    val typography = LocalAppTypography.current

    val visible =
        listOf(
            "Сколько времени в каждом приложении",
            "Где находится телефон на карте",
            "Заряд батареи",
            "Когда действуют лимиты и блокировки",
        )
    val hidden =
        listOf(
            "Содержимое экрана и переписки",
            "Пароли и набранный текст",
            "Микрофон и звонки",
        )

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.bgGrouped)
            .safeContentPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(20.dp))
        Text(text = "Что будет видно родителю", style = typography.title1, color = colors.textPrimary)
        Spacer(Modifier.height(6.dp))
        Text(
            // The consent must name who will monitor (CLAUDE.md) — the preview provides it.
            text =
            if (parentName != null) {
                "Тебя привязывает $parentName. Прочитай и согласись, чтобы продолжить."
            } else {
                "Прочитай и согласись, чтобы продолжить. Kite Jr работает открыто."
            },
            style = typography.subhead,
            color = colors.textSecondary,
        )
        Spacer(Modifier.height(20.dp))

        ConsentList(header = "Видно", items = visible, positive = true)
        Spacer(Modifier.height(20.dp))
        ConsentList(header = "Не видно", items = hidden, positive = false)

        if (error != null) {
            Spacer(Modifier.height(16.dp))
            Text(text = error, style = typography.subhead, color = colors.danger, textAlign = TextAlign.Center)
        }

        Spacer(Modifier.height(28.dp))
        AppButton(text = "Согласен, привязать", loading = busy, onClick = onAgree)
        Spacer(Modifier.height(8.dp))
        AppButton(text = "Назад", style = AppButtonStyle.Plain, onClick = onBack)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ConsentList(header: String, items: List<String>, positive: Boolean) {
    val colors = LocalAppColors.current
    val typography = LocalAppTypography.current
    Text(
        text = header,
        style = typography.footnote,
        color = colors.textSecondary,
        modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
    )
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(colors.bgBase)) {
        items.forEachIndexed { index, item ->
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    Modifier.size(20.dp).clip(CircleShape).background(if (positive) colors.success else colors.danger),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (positive) "✓" else "✕",
                        style = typography.caption.copy(fontSize = 12.sp),
                        color = androidx.compose.ui.graphics.Color.White,
                    )
                }
                Text(text = item, style = typography.body, color = colors.textPrimary)
            }
            if (index < items.lastIndex) {
                Box(Modifier.padding(start = 48.dp).fillMaxWidth().height(1.dp).background(colors.separator))
            }
        }
    }
}
