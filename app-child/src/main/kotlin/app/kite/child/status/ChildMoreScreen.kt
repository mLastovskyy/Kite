package app.kite.child.status

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.kite.child.identity.DeviceReporter
import app.kite.child.request.AskParentDialog
import app.kite.child.request.ChildRequestSender
import app.kite.core.approval.ApprovalRequest
import app.kite.core.design.LocalAppColors
import app.kite.core.design.LocalAppTypography
import app.kite.core.design.components.AppDialog
import app.kite.core.design.components.IconTile
import app.kite.core.design.components.InsetGroup
import app.kite.core.design.components.InsetGroupedList
import app.kite.core.design.components.KiteIcons
import app.kite.core.design.components.UpdateGroup
import app.kite.core.design.components.rowIcon
import app.kite.core.killswitch.KillSwitchRepository
import app.kite.core.platform.PlatformVariant
import app.kite.core.update.ApkInstaller
import kotlinx.coroutines.launch

@Composable
fun ChildMoreScreen(
    platformVariant: PlatformVariant,
    killSwitch: KillSwitchRepository,
    apkInstaller: ApkInstaller,
    versionName: String,
    released: Boolean,
    protectionGranted: Int,
    protectionTotal: Int,
    requestSender: ChildRequestSender,
    preferredParent: String?,
    onOpenParents: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenHealth: () -> Unit,
    onOpenTransparency: () -> Unit,
    onEnterParentCode: () -> Unit,
    onRestoreProtection: () -> Unit,
) {
    val context = LocalContext.current
    val colors = LocalAppColors.current
    val typography = LocalAppTypography.current
    val scope = rememberCoroutineScope()
    val protectionBroken = protectionGranted < protectionTotal

    var confirmRemoval by remember { mutableStateOf(false) }
    var removalNote by remember { mutableStateOf<String?>(null) }
    var asking by remember { mutableStateOf(false) }

    if (asking) {
        AskParentDialog(
            sender = requestSender,
            onPick = { parent ->
                asking = false
                scope.launch {
                    requestSender.send(ApprovalRequest.TYPE_REMOVAL, target = parent)
                        .onSuccess { removalNote = "Запрос отправлен. Ждём ответа родителя." }
                        .onFailure { removalNote = "Нет связи. Можно ввести код родителя." }
                }
            },
            onDismiss = { asking = false },
        )
    }

    if (confirmRemoval) {
        AppDialog(
            title = "Удалить приложение?",
            message = "Запрос уйдёт родителю. Приложение останется на телефоне, пока он не подтвердит.",
            confirmText = "Отправить запрос",
            destructive = true,
            onConfirm = {
                confirmRemoval = false
                asking = true
            },
            onDismiss = { confirmRemoval = false },
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.bgGrouped)
            .safeContentPadding()
            .verticalScroll(rememberScrollState()),
    ) {
        Spacer(Modifier.height(12.dp))
        Text(text = "Ещё", style = typography.largeTitle, color = colors.textPrimary, modifier = Modifier.padding(horizontal = 16.dp))
        Spacer(Modifier.height(16.dp))

        InsetGroupedList {
            InsetGroup {
                row(
                    title = "Мой профиль",
                    value = "Имя и аватар",
                    icon = rowIcon(KiteIcons.User, colors.accent),
                    showChevron = true,
                    onClick = onOpenProfile,
                )
                row(
                    title = "Мои родители",
                    value = preferredParent ?: "Все",
                    icon = rowIcon(KiteIcons.Users, colors.info),
                    showChevron = true,
                    onClick = onOpenParents,
                )
                row(
                    title = "Код родителя на 15 минут",
                    icon = rowIcon(KiteIcons.KeyRound, colors.accentDeep),
                    showChevron = true,
                    onClick = onEnterParentCode,
                )
            }

            InsetGroup(header = "Честно о защите") {
                row(
                    title = "Что видит родитель",
                    icon = rowIcon(KiteIcons.Eye, colors.info),
                    showChevron = true,
                    onClick = onOpenTransparency,
                )
                row(
                    title = "Здоровье защиты",
                    value = if (protectionBroken) "$protectionGranted из $protectionTotal" else "Всё готово",
                    icon = rowIcon(KiteIcons.ShieldCheck, if (protectionBroken) colors.warning else colors.success),
                    showChevron = true,
                    onClick = onOpenHealth,
                )
            }

            UpdateGroup(killSwitch = killSwitch, apkInstaller = apkInstaller, versionName = versionName)

            InsetGroup(
                header = "Телефон",
                footer =
                removalNote
                    ?: if (released) {
                        "Родитель снял защиту — приложение можно удалить."
                    } else {
                        "Удаление возможно только с разрешения родителя."
                    },
            ) {
                row(
                    title = DeviceReporter.deviceModel(),
                    value = "Сервисы: ${platformVariant.name.lowercase()}",
                    icon = rowIcon(KiteIcons.Smartphone, colors.textTertiary),
                )
                // Without this a phone the parent once released could never be protected again
                // — not even by pairing it to a new family.
                if (released) {
                    row(
                        title = "Включить защиту снова",
                        icon = rowIcon(KiteIcons.ShieldCheck, colors.success),
                        showChevron = true,
                        onClick = onRestoreProtection,
                    )
                }
                custom(separatorInset = 57.dp) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { if (released) openAppDetails(context) else confirmRemoval = true },
                            )
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconTile(icon = KiteIcons.Trash, background = colors.danger)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = if (released) "Удалить приложение" else "Попросить удалить приложение",
                            style = typography.body,
                            color = colors.danger,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}

private fun openAppDetails(context: android.content.Context) {
    runCatching {
        context.startActivity(
            android.content.Intent(
                android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                android.net.Uri.fromParts("package", context.packageName, null),
            ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
