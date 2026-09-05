package app.kite.child.request

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import app.kite.core.design.AccentColors
import app.kite.core.design.KiteTheme
import app.kite.core.design.components.AppDialog
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

/**
 * The request window the block screen opens. The block screen is a raw system overlay with no
 * Compose host, so «кого попросить» lives here instead: pick a parent, the request goes to
 * them, and the answer comes back as a plain sentence.
 */
class AskParentActivity : ComponentActivity() {
    private val sender: ChildRequestSender by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val type = intent.getStringExtra(EXTRA_TYPE)
        val payload = intent.getStringExtra(EXTRA_PAYLOAD)
        if (type == null) {
            finish()
            return
        }
        setContent {
            KiteTheme(accents = AccentColors.Child) {
                val scope = rememberCoroutineScope()
                var note by remember { mutableStateOf<String?>(null) }
                if (note != null) {
                    AppDialog(
                        title = "Запрос",
                        message = note!!,
                        confirmText = "Хорошо",
                        cancelText = null,
                        onConfirm = { finish() },
                        onDismiss = { finish() },
                    )
                } else {
                    AskParentDialog(
                        sender = sender,
                        message = "Он получит уведомление и сможет ответить.",
                        onPick = { parent ->
                            scope.launch {
                                note = sender.send(type, payload, parent)
                                    .fold(
                                        onSuccess = { "Запрос отправлен. Ждём ответа." },
                                        onFailure = { "Нет связи — попробуй позже или введи код родителя." },
                                    )
                            }
                        },
                        onDismiss = { finish() },
                    )
                }
            }
        }
    }

    companion object {
        private const val EXTRA_TYPE = "type"
        private const val EXTRA_PAYLOAD = "payload"

        fun intent(context: Context, type: String, payloadJson: String? = null): Intent = Intent(context, AskParentActivity::class.java)
            .putExtra(EXTRA_TYPE, type)
            .putExtra(EXTRA_PAYLOAD, payloadJson)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }
}
