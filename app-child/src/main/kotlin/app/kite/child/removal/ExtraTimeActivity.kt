package app.kite.child.removal

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.kite.child.KEY_OFFLINE_TOTP_SECRET
import app.kite.child.enforce.OfflineTimeGrant
import app.kite.core.design.AccentColors
import app.kite.core.design.KiteTheme
import app.kite.core.secure.SecureStore
import org.koin.android.ext.android.inject
import java.util.Base64

class ExtraTimeActivity : ComponentActivity() {
    private val secureStore: SecureStore by inject()
    private val grant: OfflineTimeGrant by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val secret =
            secureStore.getString(KEY_OFFLINE_TOTP_SECRET)
                ?.let { runCatching { Base64.getDecoder().decode(it) }.getOrNull() }
        setContent {
            KiteTheme(accents = AccentColors.Child) {
                ParentCodeScreen(
                    title = "Код от родителя",
                    explanation =
                    "Попроси родителя открыть Kite и назвать код подтверждения. " +
                        "Код даёт ${OfflineTimeGrant.MINUTES} минут и работает без интернета.",
                    actionText = "Получить ${OfflineTimeGrant.MINUTES} минут",
                    hasSecret = secret != null,
                    submit = { code ->
                        when (grant.redeem(secret, code)) {
                            OfflineTimeGrant.Outcome.Granted -> {
                                finish()
                                null
                            }
                            OfflineTimeGrant.Outcome.AlreadyUsed -> "Этот код уже использован — попроси новый"
                            OfflineTimeGrant.Outcome.WrongCode -> "Код не подошёл"
                            OfflineTimeGrant.Outcome.NoSecret -> "Устройство не привязано — код недоступен"
                        }
                    },
                    onCancel = { finish() },
                )
            }
        }
    }
}
