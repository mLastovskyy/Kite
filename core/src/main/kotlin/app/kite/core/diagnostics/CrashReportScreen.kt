package app.kite.core.diagnostics

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import app.kite.core.design.LocalAppColors
import app.kite.core.design.LocalAppTypography
import app.kite.core.design.components.AppButton
import app.kite.core.design.components.AppButtonStyle
import app.kite.core.design.components.BackHeader

/**
 * The last crash, in full, with a copy button — the only way to get a stack trace off a
 * sideloaded build on a phone that has no GMS and no cable attached.
 */
@Composable
fun CrashReportScreen(crashLog: CrashLog, onBack: () -> Unit) {
    val colors = LocalAppColors.current
    val typography = LocalAppTypography.current
    val context = LocalContext.current
    var report by remember { mutableStateOf(crashLog.last()) }
    var copied by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.bgGrouped)
            .safeContentPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        BackHeader(title = "Отчёт о сбое", onBack = onBack)
        Spacer(Modifier.height(12.dp))

        val text = report
        if (text == null) {
            Text(
                text = "Сбоев не было.",
                style = typography.body,
                color = colors.textSecondary,
                modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
            )
            return@Column
        }

        Text(
            text = "Покажите этот текст разработчику — в нём видно, что именно упало.",
            style = typography.subhead,
            color = colors.textSecondary,
        )
        Spacer(Modifier.height(12.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(colors.bgBase)
                .padding(12.dp)
                .horizontalScroll(rememberScrollState()),
        ) {
            Text(text = text, style = typography.footnote.copy(fontFamily = FontFamily.Monospace), color = colors.textPrimary)
        }
        Spacer(Modifier.height(16.dp))
        AppButton(
            text = if (copied) "Скопировано" else "Скопировать",
            onClick = {
                copyToClipboard(context, text)
                copied = true
            },
        )
        Spacer(Modifier.height(8.dp))
        AppButton(
            text = "Удалить отчёт",
            style = AppButtonStyle.Plain,
            onClick = {
                crashLog.clear()
                report = null
            },
        )
        Spacer(Modifier.height(32.dp))
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    manager.setPrimaryClip(ClipData.newPlainText("Kite crash", text))
}
