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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.kite.child.permissions.ProtectionInspector
import app.kite.child.setup.PAIRING_STAGES
import app.kite.child.setup.SetupProgress
import app.kite.child.setup.minutesLeft
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

private enum class PairStage { Code, Scan, Profile, Consent }

/**
 * Child-side pairing (Kite Jr), staged: the code first (nothing else can happen without a
 * valid invite), then the child's name and avatar, then the MANDATORY consent screen —
 * silent pairing would make this stalkerware. Validating the code up front also gives us
 * the parent's name (pairing_preview), so consent can say who is about to watch.
 *
 * The progress bar at the top counts the pairing stages and the permission wizard as one
 * sequence: setting up the phone is one job, not two. On agreement the device redeems the
 * invite together with a freshly generated offline-approval TOTP secret; [onPaired] receives
 * the family id and that secret (base64) for local storage.
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
    val context = LocalContext.current

    // The wizard steps that follow, so the progress bar can show the whole journey.
    val wizardSteps = remember { ProtectionInspector(context).requirements.size }
    val totalSteps = PAIRING_STAGES + wizardSteps

    var stage by remember { mutableStateOf(PairStage.Code) }
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

    /** Stage 1 → 2: the code (or scanned token) is checked before anything else is asked. */
    fun checkInvite() {
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
                        stage = PairStage.Profile
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

    when (stage) {
        PairStage.Code ->
            StageColumn(step = 1, total = totalSteps) {
                Text(text = "Введите код из приложения родителя", style = typography.title1, color = colors.textPrimary)
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Шестизначный код нужен один раз — он привяжет этот телефон к семье.",
                    style = typography.subhead,
                    color = colors.textSecondary,
                )
                Spacer(Modifier.height(24.dp))
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
                Spacer(Modifier.height(20.dp))
                AppButton(
                    text = "Далее",
                    loading = busy,
                    onClick = { if (code.length != 6) error = "Введите 6-значный код" else checkInvite() },
                )
                Spacer(Modifier.height(8.dp))
                AppButton(
                    text = "Сканировать QR-код",
                    style = AppButtonStyle.Plain,
                    onClick = {
                        error = null
                        stage = PairStage.Scan
                    },
                )
                Spacer(Modifier.height(20.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.bgBase)
                        .padding(16.dp),
                ) {
                    Column {
                        Text(text = "Где взять код?", style = typography.headline, color = colors.textPrimary)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Kite → Главная → Добавить ребёнка",
                            style = typography.subhead,
                            color = colors.textSecondary,
                        )
                    }
                }
            }

        PairStage.Scan ->
            QrScanScreen(
                onFound = {
                    token = it
                    stage = PairStage.Code
                    checkInvite()
                },
                onCancel = { stage = PairStage.Code },
            )

        PairStage.Profile ->
            StageColumn(step = 2, total = totalSteps) {
                Text(text = "Как тебя зовут?", style = typography.title1, color = colors.textPrimary)
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Имя и картинку увидит родитель в списке семьи.",
                    style = typography.subhead,
                    color = colors.textSecondary,
                )
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
                if (error != null) {
                    Spacer(Modifier.height(12.dp))
                    Text(text = error!!, style = typography.subhead, color = colors.danger, textAlign = TextAlign.Center)
                }
                Spacer(Modifier.height(24.dp))
                AppButton(
                    text = "Далее",
                    loading = busy,
                    onClick = {
                        if (name.isBlank()) error = "Введите имя" else stage = PairStage.Consent
                    },
                )
                Spacer(Modifier.height(8.dp))
                AppButton(
                    text = "Назад",
                    style = AppButtonStyle.Plain,
                    onClick = {
                        error = null
                        stage = PairStage.Code
                    },
                )
            }

        PairStage.Consent ->
            StageColumn(step = 3, total = totalSteps) {
                ConsentContent(
                    parentName = preview?.inviterName?.takeIf { it.isNotBlank() } ?: preview?.familyName,
                    busy = busy,
                    error = error,
                    onBack = {
                        stage = PairStage.Profile
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
                                    stage = PairStage.Code
                                }
                        }
                    },
                )
            }
    }
}

/** Every pairing stage: the shared progress header on top, the stage content below it. */
@Composable
private fun StageColumn(step: Int, total: Int, content: @Composable () -> Unit) {
    val colors = LocalAppColors.current
    Column(
        Modifier
            .fillMaxSize()
            .background(colors.bgGrouped)
            .safeContentPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(12.dp))
        SetupProgress(step = step, total = total, note = "≈ ${minutesLeft(total - step + 1)} мин осталось")
        Spacer(Modifier.height(28.dp))
        content()
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun ConsentContent(parentName: String?, busy: Boolean, error: String?, onBack: () -> Unit, onAgree: () -> Unit) {
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

    Column {
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
                        color = Color.White,
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
