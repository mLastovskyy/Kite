package app.kite.child.permissions

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.kite.child.enforce.SelfLaunchedSettings
import app.kite.core.design.LocalAppColors
import app.kite.core.design.LocalAppTypography
import app.kite.core.design.components.AppButton
import app.kite.core.design.components.AppButtonStyle

/**
 * «Здоровье защиты»: every requirement with its live status and a Fix button. Permissions
 * get revoked by OS updates and by Android 11+ auto-reset for apps unused in the
 * foreground — which a child app always is — so this screen is permanent (CLAUDE.md).
 */
@Composable
fun ProtectionHealthScreen(controller: WizardController, backgroundOptionLabel: String?, onStartWizard: () -> Unit) {
    val colors = LocalAppColors.current
    val typography = LocalAppTypography.current
    val context = LocalContext.current
    val inspector = remember { ProtectionInspector(context) }

    val lifecycleOwner = LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) controller.refresh()
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val settingsLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            controller.refresh()
        }

    val allGranted = controller.grantedCount == controller.total

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.bgGrouped)
            .safeContentPadding()
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Spacer(Modifier.height(12.dp))
        Text(text = "Здоровье защиты", style = typography.largeTitle, color = colors.textPrimary)
        Spacer(Modifier.height(6.dp))
        Text(
            text =
            if (allGranted) {
                "Все разрешения на месте."
            } else {
                "Готово ${controller.grantedCount} из ${controller.total}. Нажмите «Исправить» у пунктов с крестиком."
            },
            style = typography.subhead,
            color = colors.textSecondary,
        )
        Spacer(Modifier.height(16.dp))

        Column(
            Modifier
                .fillMaxWidth()
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                .background(colors.bgBase),
        ) {
            controller.requirements.forEachIndexed { index, requirement ->
                val granted = controller.isSatisfied(requirement)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StatusDot(granted)
                    Spacer(Modifier.size(12.dp))
                    Text(
                        text = requirement.title,
                        style = typography.body,
                        color = colors.textPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    if (!granted) {
                        AppButton(
                            text = "Исправить",
                            style = AppButtonStyle.Plain,
                            onClick = {
                                inspector.settingsIntent(requirement)?.let { intent ->
                                    // Same as in the wizard: our own deep link, guard stands aside.
                                    SelfLaunchedSettings.stamp(context)
                                    runCatching { settingsLauncher.launch(intent) }
                                }
                                    ?: onStartWizard()
                            },
                        )
                    }
                }
                if (index < controller.requirements.lastIndex) {
                    Box(
                        Modifier
                            .padding(start = 44.dp)
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(colors.separator),
                    )
                }
            }
        }

        if (!allGranted) {
            Spacer(Modifier.height(20.dp))
            AppButton(text = "Пройти настройку заново", onClick = onStartWizard)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun StatusDot(granted: Boolean) {
    val colors = LocalAppColors.current
    Box(
        Modifier
            .size(18.dp)
            .clip(CircleShape)
            .background(if (granted) colors.success else colors.danger),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (granted) "✓" else "!",
            style = LocalAppTypography.current.caption,
            color = Color.White,
        )
    }
}
