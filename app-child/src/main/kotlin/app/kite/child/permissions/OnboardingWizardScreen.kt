package app.kite.child.permissions

import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.kite.child.setup.SetupProgress
import app.kite.child.setup.minutesLeft
import app.kite.core.design.LocalAppColors
import app.kite.core.design.LocalAppTypography
import app.kite.core.design.components.AppButton
import app.kite.core.design.components.AppButtonStyle
import kotlinx.coroutines.launch

/**
 * Permission onboarding wizard: one requirement per screen, plain-Russian benefit before
 * the system UI, deep link into the exact Settings screen, re-check in onResume, automatic
 * advance. Resumes from the first unsatisfied step (CLAUDE.md, "Permission onboarding").
 *
 * [precedingSteps] are the pairing stages already behind the child on a first run, so the
 * progress bar continues that sequence instead of restarting at «Шаг 1»; it is zero when the
 * wizard is reopened later from «Здоровье защиты», where pairing is not part of the journey.
 */
@Composable
fun OnboardingWizardScreen(
    controller: WizardController,
    store: WizardStateStore,
    backgroundOptionLabel: String?,
    onFinished: () -> Unit,
    onPostpone: () -> Unit,
    precedingSteps: Int = 0,
) {
    val colors = LocalAppColors.current
    val typography = LocalAppTypography.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    // Re-check every time the screen resumes (returning from a Settings screen or dialog).
    DisposableEffectRecheck(controller)

    val current = controller.firstUnsatisfied
    LaunchedEffect(current) {
        if (current == null) onFinished()
    }
    val requirement = current ?: return

    val stepNumber = controller.requirements.indexOf(requirement) + 1
    val total = controller.total

    // Runtime-dialog permissions (notifications, foreground location) go through a launcher;
    // Settings-screen permissions open their deep link. Background location is Android 11+
    // Settings-only, so it also uses the deep link.
    val runtimePermission =
        when (requirement) {
            ProtectionRequirement.NOTIFICATIONS ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.POST_NOTIFICATIONS else null
            ProtectionRequirement.LOCATION_FOREGROUND -> Manifest.permission.ACCESS_FINE_LOCATION
            else -> null
        }
    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            controller.refresh()
        }
    val settingsLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            controller.refresh()
        }

    val inspector = remember { ProtectionInspector(context) }

    Box(
        Modifier
            .fillMaxSize()
            .background(colors.bgGrouped)
            .safeContentPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Column(Modifier.fillMaxSize()) {
            // «≈ N мин осталось» counts the steps still ahead, this one included.
            SetupProgress(
                step = precedingSteps + stepNumber,
                total = precedingSteps + total,
                note = "≈ ${minutesLeft(total - stepNumber + 1)} мин осталось",
            )
            Spacer(Modifier.height(24.dp))
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(text = requirement.title, style = typography.largeTitle, color = colors.textPrimary)
                Spacer(Modifier.height(12.dp))
                Text(text = requirement.benefit, style = typography.body, color = colors.textSecondary)

                val hint =
                    if (requirement == ProtectionRequirement.LOCATION_BACKGROUND && backgroundOptionLabel != null) {
                        "На следующем экране выберите «$backgroundOptionLabel»."
                    } else {
                        requirement.settingsHint
                    }
                if (hint != null) {
                    Spacer(Modifier.height(16.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .background(colors.bgBase, RoundedCornerShape(10.dp))
                            .padding(16.dp),
                    ) {
                        Text(text = hint, style = typography.subhead, color = colors.textSecondary)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            AppButton(
                text = actionLabel(requirement),
                onClick = {
                    when {
                        requirement == ProtectionRequirement.VENDOR_AUTOSTART -> {
                            scope.launch { store.confirmVendorAutostart() }
                            inspector.settingsIntent(requirement)?.let { runCatching { settingsLauncher.launch(it) } }
                            controller.setVendorAutostartConfirmed(true)
                        }
                        runtimePermission != null -> permissionLauncher.launch(runtimePermission)
                        else -> inspector.settingsIntent(requirement)?.let { launchSettings(settingsLauncher, it) }
                    }
                },
            )
            if (controller.functionalReached()) {
                Spacer(Modifier.height(8.dp))
                AppButton(text = "Настроить позже", style = AppButtonStyle.Plain, onClick = onPostpone)
            }
        }
    }
}

@Composable
private fun DisposableEffectRecheck(controller: WizardController) {
    val lifecycleOwner = LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) controller.refresh()
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}

private fun launchSettings(launcher: androidx.activity.result.ActivityResultLauncher<Intent>, intent: Intent) {
    runCatching { launcher.launch(intent) }
}

private fun actionLabel(requirement: ProtectionRequirement): String = when (requirement) {
    ProtectionRequirement.NOTIFICATIONS, ProtectionRequirement.LOCATION_FOREGROUND -> "Разрешить"
    ProtectionRequirement.VENDOR_AUTOSTART -> "Открыть настройки и я включил"
    else -> "Открыть настройки"
}
