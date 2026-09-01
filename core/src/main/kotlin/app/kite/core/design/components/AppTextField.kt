package app.kite.core.design.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import app.kite.core.design.LocalAppColors
import app.kite.core.design.LocalAppTypography

/**
 * iOS-like single-line field: bgBase card, 10dp radius, 50dp min height, no Material
 * underline or floating label — a plain placeholder, matching the design system. Password
 * fields get a trailing reveal (eye) toggle, like iOS.
 */
@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    isPassword: Boolean = false,
    enabled: Boolean = true,
) {
    val colors = LocalAppColors.current
    val typography = LocalAppTypography.current
    var revealed by remember { mutableStateOf(false) }
    val hidden = isPassword && !revealed

    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(colors.bgBase)
            .heightIn(min = 50.dp)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                singleLine = true,
                textStyle = typography.body.merge(LocalTextStyle.current).copy(color = colors.textPrimary),
                cursorBrush = SolidColor(colors.accent),
                visualTransformation = if (hidden) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = keyboardOptions,
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner ->
                    if (value.isEmpty()) {
                        Text(text = placeholder, style = typography.body, color = colors.textTertiary)
                    }
                    inner()
                },
            )
        }
        if (isPassword) {
            EyeToggle(
                revealed = revealed,
                tint = colors.textSecondary,
                onClick = { revealed = !revealed },
            )
        }
    }
}

@Composable
private fun EyeToggle(revealed: Boolean, tint: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .padding(start = 8.dp)
            .size(28.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(22.dp)) {
            val w = size.width
            val h = size.height
            val stroke = w * 0.08f
            val line = Stroke(width = stroke, cap = StrokeCap.Round)
            // Almond eye outline.
            val eye =
                Path().apply {
                    moveTo(w * 0.08f, h * 0.5f)
                    cubicTo(w * 0.3f, h * 0.18f, w * 0.7f, h * 0.18f, w * 0.92f, h * 0.5f)
                    cubicTo(w * 0.7f, h * 0.82f, w * 0.3f, h * 0.82f, w * 0.08f, h * 0.5f)
                    close()
                }
            drawPath(eye, tint, style = line)
            // Pupil.
            drawCircle(color = tint, radius = w * 0.13f, center = Offset(w * 0.5f, h * 0.5f))
            // Slash when hidden (eye-off).
            if (!revealed) {
                drawLine(
                    color = tint,
                    start = Offset(w * 0.14f, h * 0.14f),
                    end = Offset(w * 0.86f, h * 0.86f),
                    strokeWidth = stroke * 1.2f,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}
