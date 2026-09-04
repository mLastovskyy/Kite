package app.kite.parent.rules

import android.content.pm.PackageManager
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.kite.core.apps.AppIconsRemote
import app.kite.core.design.LocalAppColors
import app.kite.core.design.components.AppIconImage

@Composable
fun InstalledAppIcon(
    memberId: String,
    packageName: String,
    label: String,
    size: Dp = 32.dp,
    tint: Color = LocalAppColors.current.accent,
    dimmed: Boolean = false,
    modifier: Modifier = Modifier,
) {
    AppIconImage(
        packageName = packageName,
        label = label,
        remoteUrl = AppIconsRemote.publicUrl(memberId, packageName),
        size = size,
        tint = tint,
        dimmed = dimmed,
        modifier = modifier,
    )
}

fun PackageManager.hasApp(packageName: String): Boolean = runCatching { getApplicationInfo(packageName, 0) }.isSuccess
