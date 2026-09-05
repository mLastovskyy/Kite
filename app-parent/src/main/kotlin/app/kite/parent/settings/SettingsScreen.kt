package app.kite.parent.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kite.core.appearance.AppearanceRepository
import app.kite.core.appearance.ThemeMode
import app.kite.core.auth.SessionManager
import app.kite.core.avatar.AvatarRemote
import app.kite.core.design.LocalAppColors
import app.kite.core.design.LocalAppTypography
import app.kite.core.design.components.AppButton
import app.kite.core.design.components.AppButtonStyle
import app.kite.core.design.components.AppDialog
import app.kite.core.design.components.AppIcon
import app.kite.core.design.components.AppSpinner
import app.kite.core.design.components.AvatarPreset
import app.kite.core.design.components.InsetGroup
import app.kite.core.design.components.InsetGroupedList
import app.kite.core.design.components.KiteAvatar
import app.kite.core.design.components.KiteIcons
import app.kite.core.design.components.NotificationsCheckScreen
import app.kite.core.design.components.ProfileEditorScreen
import app.kite.core.design.components.rowIcon
import app.kite.core.family.FamilyMember
import app.kite.core.family.FamilyRepository
import app.kite.core.killswitch.KillSwitchRepository
import app.kite.core.killswitch.UpdateStatus
import app.kite.core.push.PushDiagnostics
import app.kite.core.update.ApkInstaller
import app.kite.parent.auth.PinLock
import kotlinx.coroutines.launch

/**
 * «Настройки» tab: profile, appearance, security (PIN), notifications, updates, account.
 * Every row is a plain statement of state with one obvious action; nothing here needs the
 * network except «Проверить обновления», the profile save and linking an email.
 *
 * Account: an anonymous parent (no [email]) sees «Привязать email» — the only reason to have
 * an account is signing in from another phone. Signing out always asks first; for an
 * anonymous session it warns that the family cannot be reached again.
 */
@Composable
fun SettingsScreen(
    me: FamilyMember?,
    email: String?,
    childrenCount: Int,
    sessionManager: SessionManager,
    familyRepository: FamilyRepository,
    avatarRemote: AvatarRemote,
    pinLock: PinLock,
    pushDiagnostics: PushDiagnostics,
    appearance: AppearanceRepository,
    apkInstaller: ApkInstaller,
    killSwitch: KillSwitchRepository,
    versionName: String,
    openLinkEmail: Boolean,
    onLinkEmailShown: () -> Unit,
    onOpenFamily: () -> Unit,
    onProfileChanged: () -> Unit,
    onSignOut: () -> Unit,
) {
    val colors = LocalAppColors.current
    val typography = LocalAppTypography.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var editingProfile by remember { mutableStateOf(false) }
    BackHandler(enabled = editingProfile) { editingProfile = false }
    if (editingProfile) {
        ProfileEditorScreen(
            me = me,
            familyRepository = familyRepository,
            avatarRemote = avatarRemote,
            onSaved = {
                editingProfile = false
                onProfileChanged()
            },
            onCancel = { editingProfile = false },
        )
        return
    }

    var linkingEmail by remember { mutableStateOf(false) }
    // Главная's «Привяжите email» card lands here with the link screen already open.
    if (openLinkEmail) {
        linkingEmail = true
        onLinkEmailShown()
    }
    if (linkingEmail) {
        LinkEmailScreen(
            sessionManager = sessionManager,
            onLinked = { linkingEmail = false },
            onCancel = { linkingEmail = false },
        )
        return
    }

    val anonymous = email.isNullOrBlank()
    var confirmSignOut by remember { mutableStateOf(false) }
    if (confirmSignOut) {
        AppDialog(
            title = if (anonymous) "Выйти без email?" else "Выйти из аккаунта?",
            message =
            if (anonymous) {
                "Аккаунт нужен только для входа с другого телефона. Email не привязан — после выхода вернуться к этой семье будет невозможно."
            } else {
                "Для входа понадобятся email и пароль."
            },
            confirmText = "Выйти",
            destructive = true,
            onConfirm = {
                confirmSignOut = false
                onSignOut()
            },
            onDismiss = { confirmSignOut = false },
        )
    }

    val themeMode by appearance.themeMode.collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)
    val update by killSwitch.updateStatus.collectAsStateWithLifecycle(initialValue = UpdateStatus(0, 0))
    var pinSet by remember { mutableStateOf(pinLock.isSet()) }
    var checkingNotifications by remember { mutableStateOf(false) }
    val setupRequested by pinLock.setupRequested.collectAsStateWithLifecycle()
    // Re-read after the setup screen closes (it is shown by the parent of this tab).
    if (!setupRequested) pinSet = pinLock.isSet()

    var notificationsGranted by remember { mutableStateOf(notificationsAllowed(context)) }
    val notificationsLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { notificationsGranted = it }

    var checkingUpdates by remember { mutableStateOf(false) }
    var updateNote by remember { mutableStateOf<String?>(null) }
    var downloading by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }

    if (checkingNotifications) {
        NotificationsCheckScreen(diagnostics = pushDiagnostics, onBack = { checkingNotifications = false })
        return
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.bgGrouped)
            .safeContentPadding()
            .verticalScroll(rememberScrollState()),
    ) {
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Ещё",
            style = typography.largeTitle,
            color = colors.textPrimary,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(16.dp))

        InsetGroupedList {
            InsetGroup {
                row(
                    title = "Семья",
                    value = if (childrenCount == 0) "Добавить ребёнка" else "Детей: $childrenCount",
                    icon = rowIcon(KiteIcons.Users, colors.accent),
                    showChevron = true,
                    onClick = onOpenFamily,
                )
            }
            InsetGroup {
                custom(separatorInset = 58.dp) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { editingProfile = true }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        KiteAvatar(
                            preset = AvatarPreset.entries.firstOrNull { it.id == me?.avatarKind } ?: AvatarPreset.KITE,
                            size = 34.dp,
                            avatarUrl = me?.avatarUrl,
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = me?.displayName?.ifBlank { null } ?: "Профиль",
                                style = typography.body,
                                color = colors.textPrimary,
                                maxLines = 1,
                            )
                            Text(
                                text = listOfNotNull(email?.takeIf { it.isNotBlank() }, deviceModel()).joinToString(" · "),
                                style = typography.footnote,
                                color = colors.textSecondary,
                                maxLines = 1,
                            )
                        }
                        AppIcon(icon = KiteIcons.ChevronRight, tint = colors.textTertiary, size = 18.dp)
                    }
                }
            }

            InsetGroup(header = "Внешний вид") {
                ThemeMode.entries.forEach { mode ->
                    row(
                        title = mode.label,
                        onClick = { scope.launch { appearance.setThemeMode(mode) } },
                        trailing = { if (mode == themeMode) Check(colors.accent) },
                    )
                }
            }

            InsetGroup(
                header = "Безопасность",
                footer =
                "Код спрашивается при открытии приложения и после 5 минут в фоне, " +
                    "чтобы ребёнок не зашёл в Kite с вашего телефона.",
            ) {
                row(
                    title = "Код входа",
                    value = if (pinSet) "Изменить" else "Задать",
                    showChevron = true,
                    onClick = { pinLock.requestSetup() },
                )
            }

            InsetGroup(header = "Уведомления") {
                row(
                    title = "Проверить уведомления",
                    showChevron = true,
                    onClick = { checkingNotifications = true },
                )
                row(
                    title = "Запросы и оповещения",
                    value = if (notificationsGranted) "Разрешены" else "Выключены",
                    showChevron = true,
                    onClick = {
                        if (!notificationsGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            openNotificationSettings(context)
                        }
                    },
                )
            }

            InsetGroup(header = "Обновления", footer = updateNote) {
                row(title = "Версия", value = "$versionName (${update.currentVersionCode})")
                row(
                    title = if (checkingUpdates) "Проверяем…" else "Проверить обновления",
                    enabled = !checkingUpdates,
                    onClick = {
                        scope.launch {
                            checkingUpdates = true
                            updateNote = null
                            val result = killSwitch.refresh()
                            checkingUpdates = false
                            updateNote =
                                result.fold(
                                    onSuccess = { m ->
                                        if (m.latestVersionCode > update.currentVersionCode) null else "У вас последняя версия"
                                    },
                                    onFailure = { "Не удалось проверить — нет связи с сервером" },
                                )
                        }
                    },
                    trailing = { if (checkingUpdates) AppSpinner(color = colors.accent, size = 18.dp) },
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
                                            .onSuccess { outcome ->
                                                updateNote =
                                                    when (outcome) {
                                                        ApkInstaller.Outcome.INSTALLER_OPENED -> "Подтвердите установку в открывшемся окне"
                                                        ApkInstaller.Outcome.BROWSER_OPENED ->
                                                            "Файл скачивается в браузере — откройте его, когда загрузка завершится"
                                                        ApkInstaller.Outcome.NEEDS_INSTALL_PERMISSION ->
                                                            "Разрешите установку из этого приложения и нажмите ещё раз"
                                                    }
                                            }
                                            .onFailure { updateNote = "Не удалось скачать: ${it.message ?: "ошибка"}" }
                                        downloading = false
                                    }
                                },
                            )
                        }
                    }
                }
            }

            InsetGroup(
                header = "Аккаунт",
                footer =
                if (anonymous) {
                    "Email нужен только для входа с другого телефона. Без него доступ к семье есть лишь с этого устройства."
                } else {
                    null
                },
            ) {
                if (anonymous) {
                    row(title = "Привязать email", value = "Не привязан", showChevron = true, onClick = { linkingEmail = true })
                } else {
                    row(title = "Email", value = email.orEmpty())
                }
                custom {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { confirmSignOut = true }
                            .padding(horizontal = 16.dp, vertical = 13.dp),
                    ) {
                        Text(text = if (anonymous) "Выйти" else "Выйти из аккаунта", style = typography.body, color = colors.danger)
                    }
                }
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun Check(color: androidx.compose.ui.graphics.Color) {
    Canvas(Modifier.size(18.dp)) {
        val w = size.width
        val path =
            Path().apply {
                moveTo(w * 0.15f, w * 0.55f)
                lineTo(w * 0.4f, w * 0.8f)
                lineTo(w * 0.87f, w * 0.25f)
            }
        drawPath(path, color, style = Stroke(width = w * 0.13f, cap = StrokeCap.Round))
    }
}

private fun notificationsAllowed(context: android.content.Context): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

private fun openNotificationSettings(context: android.content.Context) {
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

/** Unused-import guard for styles referenced only in some branches. */
@Suppress("unused")
private val unusedStyleRef = AppButtonStyle.Plain

private fun deviceModel(): String {
    val manufacturer = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
    val model = Build.MODEL
    val name = if (model.startsWith(manufacturer, ignoreCase = true)) model else "$manufacturer $model"
    return name.take(28)
}
