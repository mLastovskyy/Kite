package app.kite.parent.family

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.kite.core.approval.OfflineApprovalCode
import app.kite.core.design.LocalAppColors
import app.kite.core.design.LocalAppTypography
import app.kite.core.design.components.AppButton
import app.kite.core.design.components.AppButtonStyle
import app.kite.core.design.components.AppSpinner
import app.kite.core.family.FamilyMember
import app.kite.core.family.FamilyRepository
import app.kite.core.secure.SecureStore
import kotlinx.coroutines.delay
import java.util.Base64

/**
 * Rotating offline approval code for one child (CLAUDE.md "Offline approval codes").
 * The TOTP secret the child deposited at pairing is fetched once (member_secrets, RLS:
 * parents only) and cached in EncryptedSharedPreferences — after that the code is computed
 * locally with no network: the parent reads it out over a phone call, the child device
 * verifies it locally too.
 */
@Composable
fun ApprovalCodeScreen(member: FamilyMember, familyRepository: FamilyRepository, secureStore: SecureStore, onClose: () -> Unit) {
    val colors = LocalAppColors.current
    val typography = LocalAppTypography.current

    var secret by remember { mutableStateOf<ByteArray?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(member.id) {
        val key = "totp_secret_${member.id}"
        val cached = secureStore.getString(key)
        if (cached != null) {
            secret = runCatching { Base64.getDecoder().decode(cached) }.getOrNull()
            if (secret != null) return@LaunchedEffect
        }
        familyRepository.memberSecret(member.id)
            .onSuccess { base64 ->
                runCatching { Base64.getDecoder().decode(base64) }
                    .onSuccess {
                        secureStore.putString(key, base64)
                        secret = it
                    }
                    .onFailure { error = "Секрет повреждён — привяжите устройство заново" }
            }
            .onFailure { error = it.message ?: "Не удалось получить код" }
    }

    val approval = remember(secret) { secret?.let { OfflineApprovalCode(it) } }
    var nowMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(approval) {
        if (approval == null) return@LaunchedEffect
        while (true) {
            nowMillis = System.currentTimeMillis()
            delay(250)
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
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Код подтверждения",
                style = typography.title1,
                color = colors.textPrimary,
                modifier = Modifier.weight(1f),
            )
            AppButton(text = "Закрыть", style = AppButtonStyle.Plain, onClick = onClose)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = member.displayName.ifBlank { "Ребёнок" },
            style = typography.subhead,
            color = colors.textSecondary,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(48.dp))

        when {
            error != null ->
                Text(text = error!!, style = typography.body, color = colors.danger, textAlign = TextAlign.Center)

            approval == null -> AppSpinner(color = colors.accent, size = 28.dp)

            else -> {
                val stepSeconds = OfflineApprovalCode.DEFAULT_STEP_SECONDS
                val secondsLeft = stepSeconds - (nowMillis / 1000L) % stepSeconds
                Text(
                    text = approval.generate(nowMillis).chunked(3).joinToString(" "),
                    style = typography.largeTitle.copy(fontSize = 56.sp, letterSpacing = 4.sp),
                    color = colors.textPrimary,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Обновится через $secondsLeft с",
                    style = typography.subhead,
                    color = colors.textSecondary,
                )
                Spacer(Modifier.height(40.dp))
                Text(
                    text = "Работает без интернета. Назовите код ребёнку — он введёт его на своём телефоне, чтобы подтвердить запрос.",
                    style = typography.footnote,
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
