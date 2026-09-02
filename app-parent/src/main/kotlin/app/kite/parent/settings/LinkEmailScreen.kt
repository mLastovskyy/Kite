package app.kite.parent.settings

import androidx.compose.foundation.background
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.kite.core.auth.SessionManager
import app.kite.core.design.LocalAppColors
import app.kite.core.design.LocalAppTypography
import app.kite.core.design.components.AppButton
import app.kite.core.design.components.AppButtonStyle
import app.kite.core.design.components.AppTextField
import app.kite.core.design.components.NoticeCard
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class LinkStep { Credentials, Code }

private const val CODE_LENGTH = 6
private const val RESEND_COOLDOWN_SECONDS = 30

/**
 * Attaches an email + password to the anonymous parent session. The only reason an account
 * exists in Kite is signing in to the same family from another phone, so this lives in
 * Settings and is never forced. Two steps: credentials → 6-digit code from our own mail
 * (never a link). The auth user id does not change, so nothing about the family moves.
 */
@Composable
fun LinkEmailScreen(sessionManager: SessionManager, onLinked: () -> Unit, onCancel: () -> Unit) {
    val colors = LocalAppColors.current
    val typography = LocalAppTypography.current
    val scope = rememberCoroutineScope()

    var step by remember { mutableStateOf(LinkStep.Credentials) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var resendIn by remember { mutableIntStateOf(0) }

    LaunchedEffect(resendIn) {
        if (resendIn > 0) {
            delay(1_000)
            resendIn -= 1
        }
    }

    suspend fun sendCode() {
        sessionManager.requestLinkEmailCode(email.trim(), password)
            .onSuccess {
                step = LinkStep.Code
                code = ""
                notice = "Код отправлен на ${email.trim()}"
                resendIn = RESEND_COOLDOWN_SECONDS
            }
            .onFailure { error = it.message ?: "Ошибка" }
    }

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
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(text = "Привязать email", style = typography.title1, color = colors.textPrimary, modifier = Modifier.weight(1f))
            AppButton(text = "Отмена", style = AppButtonStyle.Plain, enabled = !busy, onClick = onCancel)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text =
            when (step) {
                LinkStep.Credentials -> "Чтобы войти в Kite с другого телефона."
                LinkStep.Code -> "Введите код из письма."
            },
            style = typography.subhead,
            color = colors.textSecondary,
        )
        Spacer(Modifier.height(24.dp))

        when (step) {
            LinkStep.Credentials -> {
                AppTextField(
                    value = email,
                    onValueChange = {
                        email = it
                        error = null
                    },
                    placeholder = "Email",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                )
                Spacer(Modifier.height(10.dp))
                AppTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        error = null
                    },
                    placeholder = "Пароль, от 6 символов",
                    isPassword = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
            }
            LinkStep.Code ->
                AppTextField(
                    value = code,
                    onValueChange = {
                        code = it.filter(Char::isDigit).take(CODE_LENGTH)
                        error = null
                    },
                    placeholder = "Код из письма",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
        }

        if (error != null) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = error!!,
                style = typography.subhead,
                color = colors.danger,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (notice != null && step == LinkStep.Code) {
            Spacer(Modifier.height(16.dp))
            NoticeCard(text = notice!!)
        }

        Spacer(Modifier.height(24.dp))
        AppButton(
            text = if (step == LinkStep.Credentials) "Отправить код" else "Подтвердить",
            loading = busy,
            onClick = {
                when {
                    step == LinkStep.Credentials && !email.contains('@') -> error = "Введите email"
                    step == LinkStep.Credentials && password.length < 6 -> error = "Пароль слишком короткий (минимум 6 символов)"
                    step == LinkStep.Code && code.length != CODE_LENGTH -> error = "Введите 6-значный код"
                    else ->
                        scope.launch {
                            busy = true
                            error = null
                            when (step) {
                                LinkStep.Credentials -> sendCode()
                                LinkStep.Code ->
                                    sessionManager.linkEmail(email.trim(), code, password)
                                        .onSuccess { onLinked() }
                                        .onFailure { error = it.message ?: "Ошибка" }
                            }
                            busy = false
                        }
                }
            },
        )
        if (step == LinkStep.Code) {
            Spacer(Modifier.height(8.dp))
            AppButton(
                text = if (resendIn > 0) "Отправить ещё раз ($resendIn)" else "Отправить код ещё раз",
                style = AppButtonStyle.Plain,
                enabled = resendIn == 0 && !busy,
                onClick = {
                    scope.launch {
                        busy = true
                        error = null
                        sendCode()
                        busy = false
                    }
                },
            )
            AppButton(
                text = "Изменить email",
                style = AppButtonStyle.Plain,
                enabled = !busy,
                onClick = {
                    step = LinkStep.Credentials
                    error = null
                    notice = null
                },
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}
