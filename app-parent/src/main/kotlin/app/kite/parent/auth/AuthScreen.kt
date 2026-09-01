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
import app.kite.core.auth.SessionManager
import app.kite.core.auth.SignUpOutcome
import app.kite.core.design.LocalAppColors
import app.kite.core.design.LocalAppTypography
import app.kite.core.design.components.AppButton
import app.kite.core.design.components.AppButtonStyle
import app.kite.core.design.components.AppTextField
import app.kite.core.design.components.AvatarPreset
import app.kite.core.design.components.KiteAvatar
import kotlinx.coroutines.launch

private enum class AuthMode { SignIn, SignUp, Reset }

/**
 * Email + password entry — the primary path that must work on every device. Sign-in,
 * sign-up and «забыли пароль» share one screen. Social sign-in is added later as an
 * optional accelerator, never the only path.
 */
@Composable
fun AuthScreen(sessionManager: SessionManager, onSignedIn: () -> Unit) {
    val colors = LocalAppColors.current
    val typography = LocalAppTypography.current
    val scope = rememberCoroutineScope()

    var mode by remember { mutableStateOf(AuthMode.SignIn) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }

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
                    AuthMode.Reset -> "Сброс пароля"
                },
                style = typography.title1,
                color = colors.textPrimary,
            )
            Spacer(Modifier.height(28.dp))

            AppTextField(
                value = email,
                onValueChange = {
                    email = it
                    error = null
                },
                placeholder = "Email",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            )
            if (mode != AuthMode.Reset) {
                Spacer(Modifier.height(10.dp))
                AppTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        error = null
                    },
                    placeholder = "Пароль",
                    isPassword = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
            }

            if (error != null) {
                Spacer(Modifier.height(12.dp))
                Text(text = error!!, style = typography.subhead, color = colors.danger, textAlign = TextAlign.Center)
            }
            if (notice != null) {
                Spacer(Modifier.height(12.dp))
                Text(text = notice!!, style = typography.subhead, color = colors.success, textAlign = TextAlign.Center)
            }

            Spacer(Modifier.height(24.dp))
            AppButton(
                text =
                when (mode) {
                    AuthMode.SignIn -> "Войти"
                    AuthMode.SignUp -> "Создать аккаунт"
                    AuthMode.Reset -> "Отправить ссылку"
                },
                loading = busy,
                onClick = {
                    val mail = email.trim()
                    if (mail.isEmpty() || (mode != AuthMode.Reset && password.isEmpty())) {
                        error = "Заполните поля"
                        return@AppButton
                    }
                    scope.launch {
                        busy = true
                        error = null
                        notice = null
                        when (mode) {
                            AuthMode.SignIn ->
                                sessionManager.signIn(mail, password)
                                    .onSuccess { onSignedIn() }
                                    .onFailure { error = it.message ?: "Ошибка" }

                            AuthMode.SignUp ->
                                sessionManager.signUp(mail, password)
                                    .onSuccess { outcome ->
                                        when (outcome) {
                                            is SignUpOutcome.SignedIn -> onSignedIn()
                                            SignUpOutcome.NeedsEmailConfirmation -> {
                                                notice = "Подтвердите email по ссылке из письма на $mail, затем войдите"
                                                mode = AuthMode.SignIn
                                                password = ""
                                            }
                                        }
                                    }
                                    .onFailure { error = it.message ?: "Ошибка" }

                            AuthMode.Reset ->
                                sessionManager.sendPasswordReset(mail)
                                    .onSuccess {
                                        notice = "Письмо отправлено на $mail"
                                        mode = AuthMode.SignIn
                                    }
                                    .onFailure { error = it.message ?: "Ошибка" }
                        }
                        busy = false
                    }
                },
            )

            Spacer(Modifier.height(8.dp))
            when (mode) {
                AuthMode.SignIn -> {
                    AppButton(text = "Создать аккаунт", style = AppButtonStyle.Plain, onClick = {
                        mode = AuthMode.SignUp
                        error = null
                    })
                    AppButton(text = "Забыли пароль?", style = AppButtonStyle.Plain, onClick = {
                        mode = AuthMode.Reset
                        error = null
                    })
                }
                AuthMode.SignUp ->
                    AppButton(text = "У меня уже есть аккаунт", style = AppButtonStyle.Plain, onClick = {
                        mode = AuthMode.SignIn
                        error =
                            null
                    })
                AuthMode.Reset ->
                    AppButton(text = "Назад ко входу", style = AppButtonStyle.Plain, onClick = {
                        mode = AuthMode.SignIn
                        error = null
                    })
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
