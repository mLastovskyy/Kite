package app.kite.parent.family

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.kite.core.design.LocalAppColors
import app.kite.core.design.LocalAppTypography
import app.kite.core.design.components.AppButton
import app.kite.core.design.components.AvatarPreset
import app.kite.core.design.components.KiteAvatar
import app.kite.core.family.PairingInvite
import app.kite.core.family.PairingKind
import kotlinx.coroutines.delay

/**
 * Shown to the parent after creating an invite. The other phone scans the QR (or types the
 * 6-digit code) within 15 minutes. Direction is fixed: this phone shows, the other scans —
 * the child's phone is the one that needs the app in hand; a second parent scans from
 * «Присоединиться к семье» in their own Kite.
 */
@Composable
fun InviteScreen(invite: PairingInvite, onClose: () -> Unit) {
    val colors = LocalAppColors.current
    val typography = LocalAppTypography.current
    val forChild = invite.kind == PairingKind.PAIR_CHILD

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
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(20.dp))
        Text(
            text = if (forChild) "Добавить ребёнка" else "Пригласить родителя",
            style = typography.title1,
            color = colors.textPrimary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text =
            if (forChild) {
                "Установите Kite Jr на телефон ребёнка и отсканируйте этот код"
            } else {
                "На телефоне второго родителя откройте Kite → «Присоединиться к семье» и отсканируйте код"
            },
            style = typography.subhead,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))

        QrCode(
            content = invite.deepLink,
            size = 240.dp,
            logo = { KiteAvatar(preset = AvatarPreset.KITE, size = 44.dp) },
        )

        Spacer(Modifier.height(28.dp))
        Text(text = "или введите код вручную", style = typography.subhead, color = colors.textSecondary)
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(colors.bgBase)
                .padding(horizontal = 20.dp, vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            // "123 456": one line on any phone, digits at tabular widths.
            Text(
                text = groupedCode(invite.code),
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 4.sp,
                maxLines = 1,
                softWrap = false,
                style = typography.largeTitle,
                color = colors.textPrimary,
            )
        }

        Spacer(Modifier.height(20.dp))
        Text(
            text = if (remaining > 0) "Код действителен ещё ${formatMinutes(remaining)}" else "Код истёк — создайте новый",
            style = typography.footnote,
            color = if (remaining > 0) colors.textSecondary else colors.danger,
        )

        Spacer(Modifier.height(32.dp))
        AppButton(text = "Готово", onClick = onClose)
        Spacer(Modifier.height(24.dp))
    }
}

private fun secondsLeft(expiresAtSeconds: Long): Long = (expiresAtSeconds - System.currentTimeMillis() / 1000).coerceAtLeast(0)

/** 6 digits as two triplets, the way people read codes aloud. */
private fun groupedCode(code: String): String = if (code.length == 6) "${code.take(3)} ${code.drop(3)}" else code

private fun formatMinutes(seconds: Long): String {
    val m = seconds / 60
    val s = seconds % 60
    return "%d:%02d".format(m, s)
}
