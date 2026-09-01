package app.kite.parent.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kite.core.design.LocalAppColors
import app.kite.core.design.LocalAppTypography
import app.kite.core.design.components.AppButton
import app.kite.core.design.components.AppButtonStyle
import app.kite.core.design.components.AvatarPreset
import app.kite.core.design.components.KiteAvatar

/**
 * Shown right after sign-in (skippable) and from «Код входа» in the family screen. Two
 * passes: enter, repeat. When a code already exists the secondary action removes it.
 */
@Composable
fun PinSetupScreen(pinLock: PinLock, onDone: () -> Unit) {
    var first by remember { mutableStateOf<String?>(null) }
    var entry by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val hadPin = remember { pinLock.isSet() }

    PinPad(
        title = if (first == null) "Код входа" else "Повторите код",
        subtitle = if (first == null) "6 цифр вместо пароля при каждом открытии" else null,
        entered = entry.length,
        error = error,
        onDigit = { digit ->
            if (entry.length >= PinLock.LENGTH) return@PinPad
            error = null
            entry += digit
            if (entry.length == PinLock.LENGTH) {
                val pending = first
                if (pending == null) {
                    first = entry
                    entry = ""
                } else if (pending == entry) {
                    pinLock.save(entry)
                    onDone()
                } else {
                    error = "Коды не совпадают"
                    first = null
                    entry = ""
                }
            }
        },
        onBackspace = { entry = entry.dropLast(1) },
    ) {
        if (hadPin) {
            AppButton(text = "Отключить код", style = AppButtonStyle.Plain, onClick = {
                pinLock.clear()
                onDone()
            })
        } else {
            AppButton(text = "Позже", style = AppButtonStyle.Plain, onClick = {
                pinLock.dismissSetup()
                onDone()
            })
        }
    }
}

/** Cold-start / relock gate. After [PinLock.MAX_FAILURES] wrong codes only sign-out remains. */
@Composable
fun PinUnlockScreen(pinLock: PinLock, onForgot: () -> Unit) {
    var entry by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
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
        AppButton(text = if (exhausted) "Войти по паролю" else "Забыли код?", style = AppButtonStyle.Plain, onClick = onForgot)
    }
}

/** iOS-passcode-style entry: six dots and a 3×4 keypad. Digits arrive one at a time. */
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

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.bgGrouped)
            .safeContentPadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(48.dp))
        KiteAvatar(preset = AvatarPreset.KITE, size = 72.dp)
        Spacer(Modifier.height(20.dp))
        Text(text = title, style = typography.title1, color = colors.textPrimary)
        if (subtitle != null) {
            Spacer(Modifier.height(6.dp))
            Text(text = subtitle, style = typography.subhead, color = colors.textSecondary, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(28.dp))

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
        Spacer(Modifier.height(16.dp))
        Text(
            text = error ?: "",
            style = typography.subhead,
            color = colors.danger,
            textAlign = TextAlign.Center,
            modifier = Modifier.height(20.dp),
        )
        Spacer(Modifier.height(20.dp))

        val rows = listOf(listOf('1', '2', '3'), listOf('4', '5', '6'), listOf('7', '8', '9'))
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                row.forEach { digit -> Key(label = digit.toString(), enabled = enabled, onClick = { onDigit(digit) }) }
            }
            Spacer(Modifier.height(14.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Spacer(Modifier.size(KEY_SIZE))
            Key(label = "0", enabled = enabled, onClick = { onDigit('0') })
            Key(label = "⌫", enabled = enabled, plain = true, onClick = onBackspace)
        }

        Spacer(Modifier.weight(1f))
        footer()
        Spacer(Modifier.height(24.dp))
    }
}

private val KEY_SIZE = 72.dp

@Composable
private fun Key(label: String, enabled: Boolean, onClick: () -> Unit, plain: Boolean = false) {
    val colors = LocalAppColors.current
    val typography = LocalAppTypography.current
    Box(
        Modifier
            .size(KEY_SIZE)
            .clip(CircleShape)
            .background(if (plain) colors.bgGrouped else colors.bgBase)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = if (plain) typography.title2 else typography.title1,
            color = if (enabled) colors.textPrimary else colors.textTertiary,
        )
    }
}
