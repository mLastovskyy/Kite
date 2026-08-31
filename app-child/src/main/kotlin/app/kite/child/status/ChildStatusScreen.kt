package app.kite.child.status

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kite.core.design.AccentColors
import app.kite.core.design.KiteTheme
import app.kite.core.design.LocalAppColors
import app.kite.core.design.LocalAppTypography
import app.kite.core.platform.PlatformVariant
import kotlinx.coroutines.flow.Flow

/**
 * M1 placeholder for Kite Jr: shows that the kill-switch flow and platform detection are
 * really wired. Real child screens arrive from M2 on. Warm accent by design — the child
 * app must not read as a supervision app.
 */
@Composable
fun ChildStatusScreen(platformVariant: PlatformVariant, disableEnforcement: Flow<Boolean>) {
    KiteTheme(accents = AccentColors.Child) {
        val colors = LocalAppColors.current
        val typography = LocalAppTypography.current
        val enforcementDisabled by disableEnforcement.collectAsStateWithLifecycle(initialValue = false)
        Box(Modifier.fillMaxSize().background(colors.bgGrouped)) {
            Column(
                modifier = Modifier.align(Alignment.Center).padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(text = "Kite Jr", style = typography.largeTitle, color = colors.textPrimary)
                Text(
                    text = if (enforcementDisabled) "Ограничения временно отключены" else "Защита активна",
                    style = typography.body,
                    color = colors.textSecondary,
                )
                Text(
                    text = "Сервисы: ${platformVariant.name}",
                    style = typography.footnote,
                    color = colors.textTertiary,
                )
            }
        }
    }
}
