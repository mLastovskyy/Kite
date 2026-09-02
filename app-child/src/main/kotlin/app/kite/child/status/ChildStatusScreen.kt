package app.kite.child.status

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kite.core.design.LocalAppColors
import app.kite.core.design.LocalAppTypography
import app.kite.core.design.components.AppButton
import app.kite.core.killswitch.UpdateStatus
import app.kite.core.platform.PlatformVariant
import app.kite.core.update.ApkInstaller
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * Kite Jr home. Warm accent by design — the child app must not read as a supervision app.
 * A persistent banner appears while any protection requirement is missing; two rows lead
 * to «Здоровье защиты» and the mandatory «Что видит родитель» transparency screen.
 * The theme is provided by [app.kite.child.ChildRoot], not here.
 */
@Composable
fun ChildStatusScreen(
    platformVariant: PlatformVariant,
    disableEnforcement: Flow<Boolean>,
    updateStatus: Flow<UpdateStatus>,
    apkInstaller: ApkInstaller,
    protectionGranted: Int,
    protectionTotal: Int,
    onOpenHealth: () -> Unit,
    onOpenTransparency: () -> Unit,
) {
    val colors = LocalAppColors.current
    val typography = LocalAppTypography.current
    val enforcementDisabled by disableEnforcement.collectAsStateWithLifecycle(initialValue = false)
    val update by updateStatus.collectAsStateWithLifecycle(initialValue = UpdateStatus(0, 0))
    val protectionBroken = protectionGranted < protectionTotal

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.bgGrouped)
            .safeContentPadding()
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(12.dp))
        Text(text = "Kite Jr", style = typography.largeTitle, color = colors.textPrimary)
        Spacer(Modifier.height(4.dp))
        Text(
            text = if (enforcementDisabled) "Ограничения временно отключены" else "Защита активна",
            style = typography.body,
            color = colors.textSecondary,
        )

        if (protectionBroken) {
            Spacer(Modifier.height(16.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.warning.copy(alpha = 0.15f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onOpenHealth,
                    )
                    .padding(16.dp),
            ) {
                Column {
                    Text(
                        text = "Защита настроена не полностью",
                        style = typography.headline,
                        color = colors.textPrimary,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "Готово $protectionGranted из $protectionTotal. Нажмите, чтобы исправить.",
                        style = typography.subhead,
                        color = colors.textSecondary,
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(colors.bgBase),
        ) {
            NavRow(title = "Здоровье защиты", onClick = onOpenHealth)
            Box(Modifier.padding(start = 16.dp).fillMaxWidth().height(1.dp).background(colors.separator))
            NavRow(title = "Что видит родитель", onClick = onOpenTransparency)
        }

        Spacer(Modifier.height(20.dp))
        Text(
            text = "Сервисы: ${platformVariant.name}",
            style = typography.footnote,
            color = colors.textTertiary,
        )
        if (update.updateAvailable) {
            val scope = rememberCoroutineScope()
            var downloading by remember { mutableStateOf(false) }
            var note by remember { mutableStateOf<String?>(null) }
            Spacer(Modifier.height(12.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.bgBase)
                    .padding(16.dp),
            ) {
                Text(
                    text = "Доступна версия ${update.latestVersionName ?: update.latestVersionCode.toString()}",
                    style = typography.headline,
                    color = colors.textPrimary,
                )
                Spacer(Modifier.height(10.dp))
                AppButton(
                    text = if (apkInstaller.canInstallDirectly()) "Скачать и установить" else "Скачать",
                    loading = downloading,
                    onClick = {
                        scope.launch {
                            downloading = true
                            apkInstaller.update(update)
                                .onSuccess { outcome ->
                                    note =
                                        when (outcome) {
                                            ApkInstaller.Outcome.INSTALLER_OPENED -> "Подтвердите установку"
                                            ApkInstaller.Outcome.BROWSER_OPENED -> "Файл скачивается в браузере"
                                            ApkInstaller.Outcome.NEEDS_INSTALL_PERMISSION -> "Разрешите установку и нажмите ещё раз"
                                        }
                                }
                                .onFailure { note = it.message ?: "Не удалось скачать" }
                            downloading = false
                        }
                    },
                )
                note?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(text = it, style = typography.footnote, color = colors.textSecondary)
                }
            }
        }
    }
}

@Composable
private fun NavRow(title: String, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    val typography = LocalAppTypography.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = title, style = typography.body, color = colors.textPrimary)
        Text(text = "›", style = typography.body, color = colors.textTertiary)
    }
}
