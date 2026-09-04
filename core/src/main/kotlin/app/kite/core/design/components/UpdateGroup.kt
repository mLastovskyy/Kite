package app.kite.core.design.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kite.core.design.LocalAppColors
import app.kite.core.design.LocalAppTypography
import app.kite.core.killswitch.KillSwitchRepository
import app.kite.core.killswitch.UpdateStatus
import app.kite.core.update.ApkInstaller
import kotlinx.coroutines.launch

@Composable
fun ColumnScope.UpdateGroup(
    killSwitch: KillSwitchRepository,
    apkInstaller: ApkInstaller,
    versionName: String,
    header: String = "Обновления",
) {
    val colors = LocalAppColors.current
    val typography = LocalAppTypography.current
    val scope = rememberCoroutineScope()
    val update by killSwitch.updateStatus.collectAsStateWithLifecycle(initialValue = UpdateStatus(0, 0))

    var checking by remember { mutableStateOf(false) }
    var downloading by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var note by remember { mutableStateOf<String?>(null) }

    InsetGroup(header = header, footer = note) {
        row(title = "Версия", value = "$versionName (${update.currentVersionCode})")
        row(
            title = if (checking) "Проверяем…" else "Проверить обновления",
            enabled = !checking,
            onClick = {
                scope.launch {
                    checking = true
                    note = null
                    val result = killSwitch.refresh()
                    checking = false
                    note =
                        result.fold(
                            onSuccess = { manifest ->
                                if (manifest.latestVersionCode > update.currentVersionCode) null else "У вас последняя версия"
                            },
                            onFailure = { "Не удалось проверить — нет связи с сервером" },
                        )
                }
            },
            trailing = { if (checking) AppSpinner(color = colors.accent, size = 18.dp) },
        )
        if (update.updateAvailable) {
            custom {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(
                        text = "Доступна версия ${update.latestVersionName ?: update.latestVersionCode.toString()}",
                        style = typography.headline,
                        color = colors.textPrimary,
                    )
                    update.message?.let {
                        Spacer(Modifier.height(4.dp))
                        Text(text = it, style = typography.subhead, color = colors.textSecondary)
                    }
                    Spacer(Modifier.height(12.dp))
                    AppButton(
                        text =
                        when {
                            downloading -> "Загрузка ${(progress * 100).toInt()}%"
                            apkInstaller.canInstallDirectly() -> "Скачать и установить"
                            else -> "Скачать"
                        },
                        loading = downloading,
                        onClick = {
                            scope.launch {
                                downloading = true
                                progress = 0f
                                apkInstaller.update(update) { progress = it }
                                    .onSuccess { note = outcomeNote(it) }
                                    .onFailure { note = "Не удалось скачать: ${it.message ?: "ошибка"}" }
                                downloading = false
                            }
                        },
                    )
                }
            }
        }
    }
}

private fun outcomeNote(outcome: ApkInstaller.Outcome): String = when (outcome) {
    ApkInstaller.Outcome.INSTALLER_OPENED -> "Подтвердите установку в открывшемся окне"
    ApkInstaller.Outcome.BROWSER_OPENED -> "Файл скачивается в браузере — откройте его, когда загрузка завершится"
    ApkInstaller.Outcome.NEEDS_INSTALL_PERMISSION -> "Разрешите установку из этого приложения и нажмите ещё раз"
}
