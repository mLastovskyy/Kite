package app.kite.parent.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import app.kite.core.auth.AuthException
import app.kite.core.auth.SessionManager
import app.kite.core.design.LocalAppColors
import app.kite.core.design.LocalAppTypography
import app.kite.core.design.components.AppButton
import app.kite.core.design.components.AppButtonStyle
import app.kite.core.design.components.AppTextField
import app.kite.core.design.components.AvatarPreset
import app.kite.core.design.components.KiteAvatar
import app.kite.core.design.components.NoticeCard
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class AuthMode { SignIn, SignUp, SignUpCode, Reset, ResetCode }

private const val CODE_LENGTH = 6
private const val RESEND_COOLDOWN_SECONDS = 30

/**
 * Email + password entry — the primary path that must work on every device. Sign-in,
 * sign-up and «забыли пароль» share one screen. Email is verified with a 6-digit code from
 * our own mail (never a link): sign-up → code → signed in; reset → code + new password →
 * signed in. Signing in to an unconfirmed account re-sends the code instead of failing.
 */
@Composable
fun AuthScreen(sessionManager: SessionManager, onSignedIn: () -> Unit) {
    val colors = LocalAppColors.current
    val typography = LocalAppTypography.current
    val scope = rememberCoroutineScope()

    var mode by remember { mutableStateOf(AuthMode.SignIn) }
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

    fun fail(throwable: Throwable) {
        error = throwable.message ?: "Ошибка"
    }

    fun switchTo(next: AuthMode) {
        mode = next
        error = null
        notice = null
        code = ""
    }

    /** Sends the sign-up code and moves to code entry; shared by sign-up and unconfirmed sign-in. */
    suspend fun sendSignUpCode(mail: String, reason: String? = null) {
        sessionManager.requestSignUpCode(mail, password)
            .onSuccess {
                switchTo(AuthMode.SignUpCode)
                notice = listOfNotNull(reason, "Код отправлен на $mail").joinToString(". ")
                resendIn = RESEND_COOLDOWN_SECONDS
            }
            .onFailure(::fail)
    }

    suspend fun sendResetCode(mail: String) {
        sessionManager.requestPasswordResetCode(mail)
            .onSuccess {
                switchTo(AuthMode.ResetCode)
                password = ""
                notice = "Если аккаунт есть, код придёт на $mail"
                resendIn = RESEND_COOLDOWN_SECONDS
            }
            .onFailure(::fail)
    }

    val codeMode = mode == AuthMode.SignUpCode || mode == AuthMode.ResetCode

    Box(
        Modifier
            .fillMaxSize()
            .background(colors.bgGrouped)
            .safeContentPadding()
            .imePadding(),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(48.dp))
            KiteAvatar(preset = AvatarPreset.KITE, size = 88.dp)
            Spacer(Modifier.height(20.dp))
            Text(
                text =
                when (mode) {
                    AuthMode.SignIn -> "Вход"
                    AuthMode.SignUp -> "Регистрация"
                    AuthMode.SignUpCode -> "Код из письма"
                    AuthMode.Reset -> "Сброс пароля"
                    AuthMode.ResetCode -> "Новый пароль"
                },
                style = typography.title1,
                color = colors.textPrimary,
            )
            Spacer(Modifier.height(28.dp))

            if (!codeMode) {
                AppTextField(
                    value = email,
                    onValueChange = {
                        email = it
                        error = null
                    },
                    placeholder = "Email",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                )
            } else {
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
            if (mode == AuthMode.SignIn || mode == AuthMode.SignUp || mode == AuthMode.ResetCode) {
                Spacer(Modifier.height(10.dp))
                AppTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        error = null
                    },
                    placeholder = if (mode == AuthMode.ResetCode) "Новый пароль" else "Пароль",
                    isPassword = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
            }

            if (error != null) {
                Spacer(Modifier.height(12.dp))
                Text(text = error!!, style = typography.subhead, color = colors.danger, textAlign = TextAlign.Center)
            }
            if (notice != null) {
                Spacer(Modifier.height(16.dp))
                NoticeCard(text = notice!!)
            }

            Spacer(Modifier.height(24.dp))
            AppButton(
                text =
                when (mode) {
                    AuthMode.SignIn -> "Войти"
                    AuthMode.SignUp -> "Создать аккаунт"
                    AuthMode.SignUpCode -> "Подтвердить"
                    AuthMode.Reset -> "Отправить код"
                    AuthMode.ResetCode -> "Сохранить пароль"
                },
                loading = busy,
                onClick = {
                    val mail = email.trim()
                    val needsPassword = mode == AuthMode.SignIn || mode == AuthMode.SignUp || mode == AuthMode.ResetCode
                    when {
                        mail.isEmpty() || (needsPassword && password.isEmpty()) -> {
                            error = "Заполните поля"
                            return@AppButton
                        }
                        codeMode && code.length != CODE_LENGTH -> {
                            error = "Введите 6-значный код"
                            return@AppButton
                        }
                    }
                    scope.launch {
                        busy = true
                        error = null
                        when (mode) {
                            AuthMode.SignIn ->
                                sessionManager.signIn(mail, password)
                                    .onSuccess { onSignedIn() }
                                    .onFailure { throwable ->
                                        // The account exists but its email was never confirmed
                                        // (e.g. created before code verification existed): finish
                                        // that instead of dead-ending on an error.
                                        if ((throwable as? AuthException)?.code == AuthException.EMAIL_NOT_CONFIRMED) {
                                            sendSignUpCode(mail, reason = "Почта ещё не подтверждена")
                                        } else {
                                            fail(throwable)
                                        }
                                    }

                            AuthMode.SignUp -> sendSignUpCode(mail)

                            AuthMode.SignUpCode ->
                                sessionManager.verifySignUpCode(mail, code)
                                    .onSuccess { onSignedIn() }
                                    .onFailure(::fail)

                            AuthMode.Reset -> sendResetCode(mail)

                            AuthMode.ResetCode ->
                                sessionManager.resetPassword(mail, code, password)
                                    .onSuccess { onSignedIn() }
                                    .onFailure(::fail)
                        }
                        busy = false
                    }
                },
            )

            Spacer(Modifier.height(8.dp))
            when (mode) {
                AuthMode.SignIn -> {
                    AppButton(text = "Создать аккаунт", style = AppButtonStyle.Plain, onClick = { switchTo(AuthMode.SignUp) })
                    AppButton(text = "Забыли пароль?", style = AppButtonStyle.Plain, onClick = { switchTo(AuthMode.Reset) })
                }
                AuthMode.SignUp ->
                    AppButton(text = "У меня уже есть аккаунт", style = AppButtonStyle.Plain, onClick = { switchTo(AuthMode.SignIn) })
                AuthMode.SignUpCode, AuthMode.ResetCode -> {
                    AppButton(
                        text = if (resendIn > 0) "Отправить ещё раз ($resendIn)" else "Отправить код ещё раз",
                        style = AppButtonStyle.Plain,
                        enabled = resendIn == 0 && !busy,
                        onClick = {
                            scope.launch {
                                busy = true
                                error = null
                                val mail = email.trim()
                                if (mode == AuthMode.SignUpCode) sendSignUpCode(mail) else sendResetCode(mail)
                                busy = false
                            }
                        },
                    )
                    AppButton(text = "Назад ко входу", style = AppButtonStyle.Plain, onClick = {
                        switchTo(AuthMode.SignIn)
                        password = ""
                    })
                }
                AuthMode.Reset ->
                    AppButton(text = "Назад ко входу", style = AppButtonStyle.Plain, onClick = { switchTo(AuthMode.SignIn) })
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
