package app.kite.parent.rules

import android.content.pm.PackageManager
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
import app.kite.core.apps.AppIconsRemote
import app.kite.core.design.LocalAppColors
import app.kite.core.design.LocalAppTypography
import coil.compose.SubcomposeAsyncImage

/**
 * The child's app icon, best source first: the same app installed on THIS phone (instant,
 * pixel-perfect), else the 64 px PNG the child device uploaded to Storage, else the first
 * letter on a tinted disc. Parents recognise YouTube by its icon, not by a grey «Y».
 */
@Composable
fun InstalledAppIcon(
    memberId: String,
    packageName: String,
    label: String,
    size: Dp = 32.dp,
    tint: Color = LocalAppColors.current.accent,
    dimmed: Boolean = false,
) {
    val context = LocalContext.current
    val local =
        remember(packageName) {
            runCatching {
                context.packageManager.getApplicationIcon(
                    packageName,
                ).toBitmap(size.value.toInt() * 3, size.value.toInt() * 3).asImageBitmap()
            }.getOrNull()
        }
    val shape = RoundedCornerShape(size * 0.24f)
    val alpha = if (dimmed) 0.45f else 1f
    when {
        local != null ->
            Image(bitmap = local, contentDescription = null, modifier = Modifier.size(size).clip(shape), alpha = alpha)
        else ->
            SubcomposeAsyncImage(
                model = AppIconsRemote.publicUrl(memberId, packageName),
                contentDescription = null,
                modifier = Modifier.size(size).clip(shape),
                alpha = alpha,
                loading = { LetterDisc(label, size, tint, dimmed) },
                error = { LetterDisc(label, size, tint, dimmed) },
            )
    }
}

@Composable
private fun LetterDisc(label: String, size: Dp, tint: Color, dimmed: Boolean) {
    val colors = LocalAppColors.current
    val typography = LocalAppTypography.current
    Box(
        Modifier.size(
            size,
        ).clip(RoundedCornerShape(size * 0.24f)).background(if (dimmed) colors.fillQuaternary else tint.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label.take(1).uppercase(), style = typography.headline, color = if (dimmed) colors.textTertiary else tint)
    }
}

/** Whether [packageName] is installed on this (the parent's) phone. */
fun PackageManager.hasApp(packageName: String): Boolean = runCatching { getApplicationInfo(packageName, 0) }.isSuccess
