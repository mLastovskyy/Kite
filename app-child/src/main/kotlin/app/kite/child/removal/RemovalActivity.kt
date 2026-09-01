package app.kite.child.removal

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.kite.child.KEY_OFFLINE_TOTP_SECRET
import app.kite.child.enforce.UninstallGuard
import app.kite.core.approval.OfflineApprovalCode
import app.kite.core.design.AccentColors
import app.kite.core.design.KiteTheme
import app.kite.core.design.LocalAppColors
import app.kite.core.design.LocalAppTypography
import app.kite.core.design.components.AppButton
import app.kite.core.design.components.AppButtonStyle
import app.kite.core.design.components.AppTextField
import app.kite.core.secure.SecureStore
import org.koin.android.ext.android.inject
import java.util.Base64

/**
 * Enters the parent's rotating offline approval code to authorise removal (M6). The code is
 * verified locally against the shared TOTP secret — no network, so a child with Wi-Fi off
 * is never permanently stuck (CLAUDE.md). On success the guard lifts protection for 10
 * minutes and drops the device admin, then the app-details screen opens for the uninstall.
 */
class RemovalActivity : ComponentActivity() {
    private val secureStore: SecureStore by inject()
    private val guard: UninstallGuard by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val secret =
            secureStore.getString(KEY_OFFLINE_TOTP_SECRET)
                ?.let { runCatching { Base64.getDecoder().decode(it) }.getOrNull() }
        setContent {
            KiteTheme(accents = AccentColors.Child) {
                RemovalScreen(
                    hasSecret = secret != null,
                    verify = { code -> secret != null && OfflineApprovalCode(secret).verify(code) },
                    onAuthorised = {
                        guard.liftProtection()
                        startActivity(
                            Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.fromParts("package", packageName, null),
                            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                        finish()
                    },
                    onCancel = { finish() },
                )
            }
        }
    }
}

@Composable
private fun RemovalScreen(hasSecret: Boolean, verify: (String) -> Boolean, onAuthorised: () -> Unit, onCancel: () -> Unit) {
    val colors = LocalAppColors.current
    val typography = LocalAppTypography.current
    var code by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.bgGrouped)
            .safeContentPadding()
            .imePadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))
        Text(text = "Код родителя", style = typography.title1, color = colors.textPrimary)
        Spacer(Modifier.height(8.dp))
        Text(
            text =
            "Попросите родителя открыть Kite и назвать код подтверждения. " +
                "Он меняется каждые несколько минут и работает без интернета.",
            style = typography.subhead,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
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
        Spacer(Modifier.height(24.dp))
        AppButton(
            text = "Подтвердить",
            onClick = {
                when {
                    !hasSecret -> error = "Устройство не привязано — код недоступен"
                    code.length != 6 -> error = "Введите 6-значный код"
                    verify(code) -> onAuthorised()
                    else -> error = "Код не подошёл"
                }
            },
        )
        Spacer(Modifier.height(8.dp))
        AppButton(text = "Отмена", style = AppButtonStyle.Plain, onClick = onCancel)
    }
}
