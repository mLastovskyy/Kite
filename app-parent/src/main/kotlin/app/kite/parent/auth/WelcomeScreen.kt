package app.kite.parent.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.kite.core.auth.SessionManager
import app.kite.core.design.LocalAppColors
import app.kite.core.design.LocalAppTypography
import app.kite.core.design.components.AppButton
import app.kite.core.design.components.AppButtonStyle
import app.kite.core.design.components.AvatarPreset
import app.kite.core.design.components.KiteAvatar
import kotlinx.coroutines.launch

/**
 * First screen. No account is required to use Kite: «Начать» opens an anonymous session and
 * goes straight to creating the family. An email is linked later, from Settings, for one
 * purpose only — signing in to the same family from another phone. «У меня есть аккаунт» is
 * that other phone.
 */
@Composable
fun WelcomeScreen(sessionManager: SessionManager, onSignIn: () -> Unit) {
    val colors = LocalAppColors.current
    val typography = LocalAppTypography.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.bgGrouped)
            .safeContentPadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        KiteAvatar(preset = AvatarPreset.KITE, size = 96.dp)
        Spacer(Modifier.height(20.dp))
        Text(text = "Kite", style = typography.largeTitle, color = colors.textPrimary)
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Экранное время, лимиты и местоположение ребёнка",
            style = typography.body,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.weight(1f))
        if (error != null) {
            Text(text = error!!, style = typography.subhead, color = colors.danger, textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
        }
        AppButton(
            text = "Начать",
            loading = busy,
            onClick = {
                scope.launch {
                    busy = true
                    error = null
                    // Success flips authState → ParentRoot moves on to home/onboarding by itself.
                    sessionManager.signInAnonymously().onFailure { error = it.message ?: "Не удалось подключиться" }
                    busy = false
                }
            },
        )
        Spacer(Modifier.height(8.dp))
        AppButton(text = "У меня есть аккаунт", style = AppButtonStyle.Plain, enabled = !busy, onClick = onSignIn)
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Аккаунт не обязателен. Email можно привязать позже, чтобы войти с другого телефона.",
            style = typography.footnote,
            color = colors.textTertiary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
    }
}
