package app.kite.core.design.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import app.kite.core.design.LocalAppColors
import app.kite.core.design.LocalAppTypography
import coil.compose.SubcomposeAsyncImage

@Composable
fun AppIconImage(
    packageName: String,
    label: String,
    modifier: Modifier = Modifier,
    remoteUrl: String? = null,
    size: Dp = 32.dp,
    tint: Color = LocalAppColors.current.accent,
    dimmed: Boolean = false,
) {
    val context = LocalContext.current
    val installed =
        remember(packageName, size) {
            runCatching {
                context.packageManager
                    .getApplicationIcon(packageName)
                    .toBitmap(size.value.toInt() * 3, size.value.toInt() * 3)
                    .asImageBitmap()
            }.getOrNull()
        }
    val shape = RoundedCornerShape(size * 0.24f)
    val alpha = if (dimmed) 0.45f else 1f
    when {
        installed != null ->
            Image(bitmap = installed, contentDescription = null, modifier = modifier.size(size).clip(shape), alpha = alpha)
        remoteUrl != null ->
            SubcomposeAsyncImage(
                model = remoteUrl,
                contentDescription = null,
                modifier = modifier.size(size).clip(shape),
                alpha = alpha,
                loading = { AppLetterDisc(label, size, tint, dimmed) },
                error = { AppLetterDisc(label, size, tint, dimmed) },
            )
        else -> AppLetterDisc(label, size, tint, dimmed, modifier)
    }
}

@Composable
fun AppLetterDisc(label: String, size: Dp, tint: Color, dimmed: Boolean = false, modifier: Modifier = Modifier) {
    val colors = LocalAppColors.current
    val typography = LocalAppTypography.current
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.24f))
            .background(if (dimmed) colors.fillQuaternary else tint.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label.take(1).uppercase(), style = typography.headline, color = if (dimmed) colors.textTertiary else tint)
    }
}
