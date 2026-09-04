package app.kite.child.removal

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import app.kite.child.KEY_OFFLINE_TOTP_SECRET
import app.kite.child.enforce.UninstallGuard
import app.kite.core.approval.OfflineApprovalCode
import app.kite.core.design.AccentColors
import app.kite.core.design.KiteTheme
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
                ParentCodeScreen(
                    title = "Код родителя",
                    explanation =
                    "Попроси родителя открыть Kite и назвать код подтверждения. " +
                        "Он меняется каждые несколько минут и работает без интернета.",
                    actionText = "Подтвердить",
                    hasSecret = secret != null,
                    submit = { code ->
                        if (secret != null && OfflineApprovalCode(secret).verify(code)) {
                            guard.liftProtection()
                            startActivity(
                                Intent(
                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.fromParts("package", packageName, null),
                                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                            finish()
                            null
                        } else {
                            "Код не подошёл"
                        }
                    },
                    onCancel = { finish() },
                )
            }
        }
    }
}
