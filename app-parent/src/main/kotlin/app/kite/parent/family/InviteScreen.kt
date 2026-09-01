package app.kite.parent.family

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.kite.core.design.LocalAppColors
import app.kite.core.design.LocalAppTypography
import app.kite.core.design.components.AppButton
import app.kite.core.family.PairingInvite
import kotlinx.coroutines.delay

/**
 * Shown to the parent after creating an invite. The child scans the QR (or types the
 * 6-digit code) within 15 minutes. Direction is fixed: the parent's phone shows, the
 * child's phone scans — the child is the one that needs the app in hand.
 */
@Composable
fun InviteScreen(invite: PairingInvite, onClose: () -> Unit) {
    val colors = LocalAppColors.current
    val typography = LocalAppTypography.current

    val remaining by produceState(initialValue = secondsLeft(invite.expiresAt), invite.expiresAt) {
        while (value > 0) {
            delay(1000)
            value = secondsLeft(invite.expiresAt)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.bgGrouped)
            .safeContentPadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(16.dp))
        Text(text = "Сканируйте на телефоне ребёнка", style = typography.title3, color = colors.textPrimary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))

        QrCode(content = invite.deepLink, size = 240.dp)

        Spacer(Modifier.height(28.dp))
        Text(text = "или введите код вручную", style = typography.subhead, color = colors.textSecondary)
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(colors.bgBase)
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = spacedCode(invite.code),
                style = typography.largeTitle.copy(fontSize = 40.sp, letterSpacing = 8.sp),
                color = colors.textPrimary,
            )
        }

        Spacer(Modifier.height(20.dp))
        Text(
            text = if (remaining > 0) "Код действителен ещё ${formatMinutes(remaining)}" else "Код истёк — создайте новый",
            style = typography.footnote,
            color = if (remaining > 0) colors.textSecondary else colors.danger,
        )

        Spacer(Modifier.weight(1f))
        AppButton(text = "Готово", onClick = onClose)
        Spacer(Modifier.height(24.dp))
    }
}

private fun secondsLeft(expiresAtSeconds: Long): Long = (expiresAtSeconds - System.currentTimeMillis() / 1000).coerceAtLeast(0)

private fun spacedCode(code: String): String = code.toCharArray().joinToString(" ")

private fun formatMinutes(seconds: Long): String {
    val m = seconds / 60
    val s = seconds % 60
    return "%d:%02d".format(m, s)
}
