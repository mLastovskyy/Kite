package app.kite.parent.auth

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kite.core.design.LocalAppColors
import app.kite.core.design.LocalAppTypography
import app.kite.core.design.components.AppButton
import app.kite.core.design.components.AppButtonStyle
import app.kite.core.design.components.AppIcon
import app.kite.core.design.components.AppTextField
import app.kite.core.design.components.AvatarPreset
import app.kite.core.design.components.InsetGroup
import app.kite.core.design.components.InsetGroupedList
import app.kite.core.design.components.KiteAvatar
import app.kite.core.design.components.KiteIcons

/**
 * Shown right after the family is created / after sign-in, and from «Код входа» in
 * Settings. Passes: (current code, when one exists — owner 04.09.2026: a new code can only be
 * set by whoever knows the old one), enter, repeat. The code is MANDATORY (owner's decision):
 * a child holding the parent's phone must not get in, so there is no «Позже» and no way to
 * switch it off — only to change it. When changing, «Отмена» keeps the existing code.
 */
@Composable
fun PinSetupScreen(pinLock: PinLock, onDone: () -> Unit, allowSkip: Boolean = false) {
    var first by remember { mutableStateOf<String?>(null) }
    var askRecovery by remember { mutableStateOf(false) }
    var entry by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val hadPin = remember { pinLock.isSet() }
    var oldVerified by remember { mutableStateOf(!hadPin) }
    val failures by pinLock.failures.collectAsStateWithLifecycle()
    val exhausted = !oldVerified && failures >= PinLock.MAX_FAILURES

    if (askRecovery) {
        PinRecoveryStep(pinLock = pinLock, onDone = onDone)
        return
    }

    PinPad(
        title =
        when {
            !oldVerified -> "Текущий код"
            first != null -> "Повторите код"
            hadPin -> "Новый код входа"
            else -> "Код входа"
        },
        subtitle =
        when {
            !oldVerified -> "Сначала введите действующий код"
            first == null -> "6 цифр. Спрашивается при каждом открытии, чтобы ребёнок не зашёл в Kite"
            else -> null
        },
        entered = entry.length,
        error = if (exhausted) "Слишком много попыток" else error,
        enabled = !exhausted,
        onDigit = { digit ->
            if (entry.length >= PinLock.LENGTH) return@PinPad
            error = null
            entry += digit
            if (entry.length == PinLock.LENGTH) {
                if (!oldVerified) {
                    if (pinLock.verify(entry)) oldVerified = true else error = "Неверный код"
                    entry = ""
                    return@PinPad
                }
                val pending = first
                if (pending == null) {
                    first = entry
                    entry = ""
                } else if (pending == entry) {
                    pinLock.save(entry)
                    if (pinLock.hasRecovery()) onDone() else askRecovery = true
                } else {
                    error = "Коды не совпадают"
                    first = null
                    entry = ""
                }
            }
        },
        onBackspace = { entry = entry.dropLast(1) },
    ) {
        when {
            first != null ->
                AppButton(text = "Ввести заново", style = AppButtonStyle.Plain, onClick = {
                    first = null
                    entry = ""
                    error = null
                })
            hadPin ->
                AppButton(text = "Отмена", style = AppButtonStyle.Plain, onClick = {
                    pinLock.dismissSetup()
                    onDone()
                })
            allowSkip ->
                AppButton(text = "Пропустить", style = AppButtonStyle.Plain, onClick = {
                    pinLock.dismissSetup()
                    onDone()
                })
        }
    }
}

private val RECOVERY_QUESTIONS =
    listOf(
        "Девичья фамилия мамы",
        "Кличка первого питомца",
        "Город, где вы родились",
        "Любимое блюдо в детстве",
    )

@Composable
private fun PinRecoveryStep(pinLock: PinLock, onDone: () -> Unit) {
    val colors = LocalAppColors.current
    val typography = LocalAppTypography.current
    var question by remember { mutableStateOf(RECOVERY_QUESTIONS.first()) }
    var answer by remember { mutableStateOf("") }
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
        Spacer(Modifier.height(32.dp))
        KiteAvatar(preset = AvatarPreset.KITE, size = 64.dp)
        Spacer(Modifier.height(16.dp))
        Text(text = "Если забудете код", style = typography.title1, color = colors.textPrimary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Ответ на вопрос вернёт доступ без выхода из аккаунта.",
            style = typography.subhead,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        InsetGroupedList {
            InsetGroup(header = "Вопрос") {
                RECOVERY_QUESTIONS.forEach { option ->
                    row(
                        title = option,
                        onClick = { question = option },
                        trailing = { if (option == question) AppIcon(icon = KiteIcons.Check, tint = colors.accent, size = 20.dp) },
                    )
                }
            }
            InsetGroup(header = "Ответ") {
                custom {
                    Box(Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                        AppTextField(
                            value = answer,
                            onValueChange = {
                                answer = it.take(40)
                                error = null
                            },
                            placeholder = "Ответ",
                        )
                    }
                }
            }
        }
        error?.let {
            Spacer(Modifier.height(12.dp))
            Text(text = it, style = typography.subhead, color = colors.danger)
        }
        Spacer(Modifier.height(24.dp))
        AppButton(
            text = "Сохранить",
            onClick = {
                if (answer.isBlank()) {
                    error = "Введите ответ"
                } else {
                    pinLock.saveRecovery(question, answer)
                    onDone()
                }
            },
        )
        Spacer(Modifier.height(8.dp))
        AppButton(text = "Пропустить", style = AppButtonStyle.Plain, onClick = onDone)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun PinRecoveryUnlock(pinLock: PinLock, onCancel: () -> Unit) {
    val colors = LocalAppColors.current
    val typography = LocalAppTypography.current
    var answer by remember { mutableStateOf("") }
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
        Spacer(Modifier.height(40.dp))
        Text(text = "Контрольный вопрос", style = typography.title1, color = colors.textPrimary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        Text(
            text = pinLock.recoveryQuestion().orEmpty(),
            style = typography.body,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        AppTextField(
            value = answer,
            onValueChange = {
                answer = it.take(40)
                error = null
            },
            placeholder = "Ответ",
        )
        error?.let {
            Spacer(Modifier.height(12.dp))
            Text(text = it, style = typography.subhead, color = colors.danger)
        }
        Spacer(Modifier.height(24.dp))
        AppButton(
            text = "Продолжить",
            onClick = { if (!pinLock.resetWithRecovery(answer)) error = "Ответ не подошёл" },
        )
        Spacer(Modifier.height(8.dp))
        AppButton(text = "Назад", style = AppButtonStyle.Plain, onClick = onCancel)
        Spacer(Modifier.height(24.dp))
    }
}

/** Cold-start / relock gate. After [PinLock.MAX_FAILURES] wrong codes only sign-out remains. */
@Composable
fun PinUnlockScreen(pinLock: PinLock, onForgot: () -> Unit) {
    var entry by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var recovering by remember { mutableStateOf(false) }
    if (recovering) {
        PinRecoveryUnlock(pinLock = pinLock, onCancel = { recovering = false })
        return
    }
    val failures by pinLock.failures.collectAsStateWithLifecycle()
    val exhausted = failures >= PinLock.MAX_FAILURES

    PinPad(
        title = "Введите код",
        subtitle = null,
        entered = entry.length,
        error = if (exhausted) "Слишком много попыток — войдите по паролю" else error,
        enabled = !exhausted,
        onDigit = { digit ->
            if (entry.length >= PinLock.LENGTH) return@PinPad
            error = null
            entry += digit
            if (entry.length == PinLock.LENGTH) {
                if (!pinLock.unlock(entry)) error = "Неверный код"
                entry = ""
            }
        },
        onBackspace = { entry = entry.dropLast(1) },
    ) {
        AppButton(
            text = if (exhausted && !pinLock.hasRecovery()) "Войти по паролю" else "Забыли код?",
            style = AppButtonStyle.Plain,
            onClick = { if (pinLock.hasRecovery()) recovering = true else onForgot() },
        )
    }
}

/**
 * iOS-passcode-style entry: six dots and a 3×4 keypad. Digits arrive one at a time.
 *
 * The screen usually appears straight after the password field, so the system keyboard is
 * still up and would hide the lower half of the keypad — it is dismissed on entry. Key size
 * is derived from the real screen (narrow phones, display zoom, short screens shrink the
 * keypad instead of clipping it), and the column scrolls as a last resort.
 */
@Composable
private fun PinPad(
    title: String,
    subtitle: String?,
    entered: Int,
    error: String?,
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    enabled: Boolean = true,
    footer: @Composable () -> Unit,
) {
    val colors = LocalAppColors.current
    val typography = LocalAppTypography.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    LaunchedEffect(Unit) {
        focusManager.clearFocus(force = true)
        keyboard?.hide()
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(colors.bgGrouped)
            .safeContentPadding(),
    ) {
        val gap = 14.dp
        val roomForKeys = maxHeight - HEADER_HEIGHT - FOOTER_HEIGHT
        val keySize =
            minOf(
                MAX_KEY_SIZE,
                (maxWidth - SIDE_PADDING * 2 - gap * 2) / 3,
                (roomForKeys - gap * 3) / 4,
            ).coerceAtLeast(MIN_KEY_SIZE)
        val compact = keySize < 64.dp

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = SIDE_PADDING),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(if (compact) 20.dp else 40.dp))
            KiteAvatar(preset = AvatarPreset.KITE, size = if (compact) 52.dp else 72.dp)
            Spacer(Modifier.height(if (compact) 12.dp else 20.dp))
            Text(text = title, style = typography.title1, color = colors.textPrimary)
            if (subtitle != null) {
                Spacer(Modifier.height(6.dp))
                Text(text = subtitle, style = typography.subhead, color = colors.textSecondary, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(if (compact) 18.dp else 28.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                repeat(PinLock.LENGTH) { index ->
                    val filled = index < entered
                    Box(
                        Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(if (filled) colors.accent else colors.bgGrouped)
                            .border(1.5.dp, if (filled) colors.accent else colors.textTertiary, CircleShape),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = error ?: "",
                style = typography.subhead,
                color = colors.danger,
                textAlign = TextAlign.Center,
                modifier = Modifier.height(20.dp),
            )
            Spacer(Modifier.height(if (compact) 8.dp else 16.dp))

            val rows = listOf(listOf('1', '2', '3'), listOf('4', '5', '6'), listOf('7', '8', '9'))
            rows.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    row.forEach { digit ->
                        Key(label = digit.toString(), size = keySize, enabled = enabled, onClick = { onDigit(digit) })
                    }
                }
                Spacer(Modifier.height(gap))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                Spacer(Modifier.size(keySize))
                Key(label = "0", size = keySize, enabled = enabled, onClick = { onDigit('0') })
                BackspaceKey(size = keySize, enabled = enabled, onClick = onBackspace)
            }

            Spacer(Modifier.height(if (compact) 12.dp else 24.dp))
            footer()
            Spacer(Modifier.height(16.dp))
        }
    }
}

private val MAX_KEY_SIZE = 72.dp
private val MIN_KEY_SIZE = 48.dp
private val SIDE_PADDING = 24.dp

/** Avatar + title + subtitle + dots + error line, estimated for the tallest variant. */
private val HEADER_HEIGHT = 300.dp

/** Footer plain button with its spacers. */
private val FOOTER_HEIGHT = 96.dp

@Composable
private fun Key(label: String, size: Dp, enabled: Boolean, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    val typography = LocalAppTypography.current
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(colors.bgBase)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = typography.title1,
            color = if (enabled) colors.textPrimary else colors.textTertiary,
        )
    }
}

/**
 * Backspace drawn with Canvas: the ⌫ glyph is missing from Inter and from some vendor
 * fallback fonts, which rendered an invisible key on real devices.
 */
@Composable
private fun BackspaceKey(size: Dp, enabled: Boolean, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    val tint = if (enabled) colors.textPrimary else colors.textTertiary
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(size * 0.42f)) {
            val w = this.size.width
            val h = this.size.height
            val strokeWidth = w * 0.08f
            val outline =
                Path().apply {
                    moveTo(w * 0.34f, h * 0.2f)
                    lineTo(w * 0.96f, h * 0.2f)
                    lineTo(w * 0.96f, h * 0.8f)
                    lineTo(w * 0.34f, h * 0.8f)
                    lineTo(w * 0.04f, h * 0.5f)
                    close()
                }
            drawPath(outline, tint, style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawLine(tint, Offset(w * 0.52f, h * 0.36f), Offset(w * 0.78f, h * 0.64f), strokeWidth, StrokeCap.Round)
            drawLine(tint, Offset(w * 0.78f, h * 0.36f), Offset(w * 0.52f, h * 0.64f), strokeWidth, StrokeCap.Round)
        }
    }
}
