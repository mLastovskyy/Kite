package app.kite.core.design.components

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.kite.core.design.LocalAppColors
import app.kite.core.notifications.Channels
import app.kite.core.push.PushDiagnostics
import kotlinx.coroutines.launch

@Composable
fun NotificationsCheckScreen(diagnostics: PushDiagnostics, onBack: () -> Unit) {
    val context = LocalContext.current
    val colors = LocalAppColors.current
    val scope = rememberCoroutineScope()

    var running by remember { mutableStateOf(false) }
    var report by remember { mutableStateOf<PushDiagnostics.Report?>(null) }
    val allowed = remember(running) { notificationsAllowed(context) }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.bgGrouped)
            .safeContentPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        BackHeader(title = "Проверка уведомлений", onBack = onBack)
        Spacer(Modifier.height(20.dp))

        InsetGroupedList {
            InsetGroup(header = "На этом телефоне") {
                row(title = "Сборка", value = diagnostics.variant)
                row(
                    title = "Разрешение на уведомления",
                    value = if (allowed) "Есть" else "Нет",
                    showChevron = !allowed,
                    onClick = if (allowed) null else ({ openNotificationSettings(context) }),
                )
                row(title = "Канал «Запросы»", value = if (channelEnabled(context, Channels.REQUESTS)) "Включён" else "Выключен")
            }

            report?.let { result ->
                InsetGroup(header = "Проверка", footer = result.error) {
                    row(title = "Токен получен", value = if (result.tokenObtained) "Да" else "Нет")
                    row(title = "Токен на сервере", value = if (result.registered) "Да" else "Нет")
                    row(title = "Доставлено устройств", value = result.delivered?.toString() ?: "0")
                    row(title = "Запросы без push", value = "Проверяем каждые 15 мин")
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        AppButton(
            text = "Отправить тестовое",
            loading = running,
            onClick = {
                scope.launch {
                    running = true
                    report = diagnostics.run()
                    running = false
                    NotificationManagerCompat.from(context).takeIf { it.areNotificationsEnabled() }?.notify(
                        LOCAL_TEST_ID,
                        Channels.build(context, Channels.STATUS, "Проверка уведомлений", "Локальное уведомление доставлено."),
                    )
                }
            },
        )
        Spacer(Modifier.height(32.dp))
    }
}

private const val LOCAL_TEST_ID = 4242

private fun notificationsAllowed(context: Context): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

private fun channelEnabled(context: Context, channelId: String): Boolean = runCatching {
    val manager = NotificationManagerCompat.from(context)
    manager.getNotificationChannel(channelId)?.importance != android.app.NotificationManager.IMPORTANCE_NONE
}.getOrDefault(true)

private fun openNotificationSettings(context: Context) {
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
