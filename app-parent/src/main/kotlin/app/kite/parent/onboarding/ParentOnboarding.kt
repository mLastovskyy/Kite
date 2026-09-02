package app.kite.parent.onboarding

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import app.kite.core.avatar.AvatarRemote
import app.kite.core.design.LocalAppColors
import app.kite.core.design.LocalAppTypography
import app.kite.core.design.components.AppButton
import app.kite.core.design.components.AppButtonStyle
import app.kite.core.design.components.AvatarPreset
import app.kite.core.design.components.KiteAvatar
import app.kite.core.family.FamilyRepository
import app.kite.parent.auth.PinLock
import app.kite.parent.auth.PinSetupScreen
import app.kite.parent.family.CreateFamilyScreen
import app.kite.parent.family.JoinFamilyScreen

private enum class OnboardingStep { Family, Notifications, Pin }

/**
 * First run, one thing per screen: who you are and your family (create or join), then
 * notifications with a plain reason before the system dialog («Позже» allowed), then the
 * mandatory 6-digit code — it is what keeps a child holding this phone out of Kite, so it
 * cannot be skipped. Steps the device already satisfies are skipped.
 */
@Composable
fun ParentOnboarding(familyRepository: FamilyRepository, avatarRemote: AvatarRemote, pinLock: PinLock, onFinished: () -> Unit) {
    val context = LocalContext.current
    var step by remember { mutableStateOf(OnboardingStep.Family) }
    var joining by remember { mutableStateOf(false) }

    // The post-sign-in PIN offer is folded into this flow as its last step.
    LaunchedEffect(Unit) { pinLock.dismissSetup() }

    fun afterFamily() {
        step = if (notificationsAllowed(context)) OnboardingStep.Pin else OnboardingStep.Notifications
    }

    when (step) {
        OnboardingStep.Family ->
            if (joining) {
                JoinFamilyScreen(familyRepository = familyRepository, onJoined = { afterFamily() }, onBack = { joining = false })
            } else {
                CreateFamilyScreen(
                    familyRepository = familyRepository,
                    avatarRemote = avatarRemote,
                    onCreated = { afterFamily() },
                    onJoinInstead = { joining = true },
                )
            }

        OnboardingStep.Notifications ->
            NotificationsStep(onDone = { step = OnboardingStep.Pin })

        OnboardingStep.Pin ->
            PinSetupScreen(pinLock = pinLock, onDone = onFinished)
    }
}

@Composable
private fun NotificationsStep(onDone: () -> Unit) {
    val colors = LocalAppColors.current
    val typography = LocalAppTypography.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onDone() }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.bgGrouped)
            .safeContentPadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(48.dp))
        KiteAvatar(preset = AvatarPreset.KITE, size = 88.dp)
        Spacer(Modifier.height(20.dp))
        Text(text = "Уведомления", style = typography.title1, color = colors.textPrimary)
        Spacer(Modifier.height(10.dp))
        Text(
            text =
            "Ребёнок попросит ещё времени или разблокировать телефон — вы увидите это сразу. " +
                "Без уведомлений запросы будут ждать, пока вы не откроете Kite.",
            style = typography.body,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.weight(1f))
        AppButton(
            text = "Разрешить",
            onClick = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    onDone()
                }
            },
        )
        Spacer(Modifier.height(8.dp))
        AppButton(text = "Позже", style = AppButtonStyle.Plain, onClick = onDone)
        Spacer(Modifier.height(24.dp))
    }
}

private fun notificationsAllowed(context: android.content.Context): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
