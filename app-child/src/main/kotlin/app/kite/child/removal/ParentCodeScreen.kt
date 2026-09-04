package app.kite.child.removal

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
import app.kite.core.design.LocalAppColors
import app.kite.core.design.LocalAppTypography
import app.kite.core.design.components.AppButton
import app.kite.core.design.components.AppButtonStyle
import app.kite.core.design.components.AppTextField

const val CODE_LENGTH = 6

@Composable
fun ParentCodeScreen(
    title: String,
    explanation: String,
    actionText: String,
    hasSecret: Boolean,
    submit: (String) -> String?,
    onCancel: () -> Unit,
) {
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
        Text(text = title, style = typography.title1, color = colors.textPrimary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(text = explanation, style = typography.subhead, color = colors.textSecondary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        AppTextField(
            value = code,
            onValueChange = {
                code = it.filter(Char::isDigit).take(CODE_LENGTH)
                error = null
            },
            placeholder = "$CODE_LENGTH цифр",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        error?.let {
            Spacer(Modifier.height(12.dp))
            Text(text = it, style = typography.subhead, color = colors.danger, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(24.dp))
        AppButton(
            text = actionText,
            onClick = {
                error =
                    when {
                        !hasSecret -> "Устройство не привязано — код недоступен"
                        code.length != CODE_LENGTH -> "Введите $CODE_LENGTH-значный код"
                        else -> submit(code)
                    }
            },
        )
        Spacer(Modifier.height(8.dp))
        AppButton(text = "Отмена", style = AppButtonStyle.Plain, onClick = onCancel)
    }
}
