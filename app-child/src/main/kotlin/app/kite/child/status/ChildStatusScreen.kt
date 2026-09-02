package app.kite.child.status

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kite.child.identity.MemberIdentity
import app.kite.child.tasks.TasksStore
import app.kite.core.approval.ApprovalRequest
import app.kite.core.approval.ApprovalsRemote
import app.kite.core.design.LocalAppColors
import app.kite.core.design.LocalAppTypography
import app.kite.core.design.components.AppButton
import app.kite.core.design.components.AppDialog
import app.kite.core.design.components.IconTile
import app.kite.core.design.components.InsetGroup
import app.kite.core.design.components.InsetGroupedList
import app.kite.core.design.components.KiteIcons
import app.kite.core.design.components.formatUsageMs
import app.kite.core.design.components.rowIcon
import app.kite.core.killswitch.UpdateStatus
import app.kite.core.platform.PlatformVariant
import app.kite.core.update.ApkInstaller
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * Kite Jr home. Warm accent by design — the child app must not read as a supervision app.
 * The hero answers the only question the child actually has («сколько у меня осталось»),
 * then tasks and its own statistics, then the two transparency screens, and last the honest
 * «Удалить приложение» row: the request goes to the parent and the app stays until they
 * agree. A persistent banner appears while any protection requirement is missing.
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
    summary: TodaySummary,
    tasksStore: TasksStore,
    identity: MemberIdentity,
    approvalsRemote: ApprovalsRemote,
    onOpenHealth: () -> Unit,
    onOpenTransparency: () -> Unit,
    onOpenTasks: () -> Unit,
    onOpenStats: () -> Unit,
    onEnterParentCode: () -> Unit,
) {
    val colors = LocalAppColors.current
    val typography = LocalAppTypography.current
    val scope = rememberCoroutineScope()
    val enforcementDisabled by disableEnforcement.collectAsStateWithLifecycle(initialValue = false)
    val update by updateStatus.collectAsStateWithLifecycle(initialValue = UpdateStatus(0, 0))
    val protectionBroken = protectionGranted < protectionTotal

    var today by remember { mutableStateOf<TodaySummary.Today?>(null) }
    LaunchedEffect(Unit) { today = summary.today() }
    val openTasks = remember { tasksStore.visible().count { it.isOpen } }

    var confirmRemoval by remember { mutableStateOf(false) }
    var removalNote by remember { mutableStateOf<String?>(null) }

    if (confirmRemoval) {
        AppDialog(
            title = "Удалить приложение?",
            message = "Запрос уйдёт родителю. Приложение останется на телефоне, пока он не подтвердит.",
            confirmText = "Удалить",
            destructive = true,
            onConfirm = {
                confirmRemoval = false
                scope.launch {
                    val familyId = identity.familyId()
                    val memberId = identity.memberId()
                    if (familyId == null || memberId == null) {
                        removalNote = "Устройство не привязано — попроси родителя"
                        return@launch
                    }
                    approvalsRemote.create(memberId, familyId, ApprovalRequest.TYPE_REMOVAL)
                        .onSuccess { removalNote = "Запрос отправлен. Ждём ответа родителя." }
                        .onFailure { removalNote = "Нет связи. Можно ввести код родителя." }
                }
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
        Text(
            text = "Kite Jr",
            style = typography.largeTitle,
            color = colors.textPrimary,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(14.dp))

        TimeHero(today = today, enforcementDisabled = enforcementDisabled)

        if (protectionBroken) {
            Spacer(Modifier.height(14.dp))
            Box(
                Modifier
                    .padding(horizontal = 16.dp)
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
                    Text(text = "Защита настроена не полностью", style = typography.headline, color = colors.textPrimary)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "Готово $protectionGranted из $protectionTotal. Нажми, чтобы исправить.",
                        style = typography.subhead,
                        color = colors.textSecondary,
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        InsetGroupedList {
            InsetGroup(footer = "Задание подтверждает родитель — после этого время добавляется на сегодня.") {
                row(
                    title = "Мои задания",
                    value = if (openTasks > 0) "$openTasks открытых" else "Нет новых",
                    icon = rowIcon(KiteIcons.ListChecks, colors.accent),
                    showChevron = true,
                    onClick = onOpenTasks,
                )
                row(
                    title = "Моё время",
                    value = today?.let { formatUsageMs(it.usedMs) },
                    icon = rowIcon(KiteIcons.ChartColumn, colors.info),
                    showChevron = true,
                    onClick = onOpenStats,
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

            InsetGroup(header = "Приложение", footer = removalNote ?: "Удаление возможно только с разрешения родителя.") {
                row(
                    title = "Версия",
                    value = update.currentVersionCode.takeIf { it > 0 }?.let { "сборка $it" },
                    icon = rowIcon(KiteIcons.Smartphone, colors.textTertiary),
                )
                row(
                    title = "Ввести код родителя",
                    icon = rowIcon(KiteIcons.KeyRound, colors.accentDeep),
                    showChevron = true,
                    onClick = onEnterParentCode,
                )
                custom(separatorInset = 57.dp) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { confirmRemoval = true },
                            )
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconTile(icon = KiteIcons.Trash, background = colors.danger)
                        Spacer(Modifier.width(12.dp))
                        Text(text = "Удалить приложение", style = typography.body, color = colors.danger)
                    }
                }
            }
        }

        if (update.updateAvailable) {
            Spacer(Modifier.height(24.dp))
            UpdateCard(update = update, apkInstaller = apkInstaller)
        }

        Spacer(Modifier.height(20.dp))
        Text(
            text = "Сервисы: ${platformVariant.name}",
            style = typography.footnote,
            color = colors.textTertiary,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(32.dp))
    }
}

/**
 * The one number the child looks for. Warm gradient, no chrome, same language as the block
 * screen so the two never contradict each other.
 */
@Composable
private fun TimeHero(today: TodaySummary.Today?, enforcementDisabled: Boolean) {
    val colors = LocalAppColors.current
    val typography = LocalAppTypography.current
    val remaining = today?.remainingMinutes

    val title =
        when {
            enforcementDisabled -> "Ограничения выключены"
            today == null -> "Считаем…"
            remaining == null -> "Лимита на сегодня нет"
            remaining <= 0 -> "Время на сегодня закончилось"
            else -> formatUsageMs(remaining * 60_000L)
        }
    val caption =
        when {
            enforcementDisabled -> "Родитель временно снял все ограничения"
            today == null -> ""
            remaining == null -> "Использовано ${formatUsageMs(today.usedMs)}"
            remaining <= 0 -> "Выполни задание, чтобы получить ещё"
            else -> "Осталось из дневного лимита"
        }

    Column(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.verticalGradient(listOf(colors.accentLight, colors.accent)))
            .padding(horizontal = 20.dp, vertical = 22.dp),
    ) {
        Text(
            text = caption.ifBlank { " " },
            style = typography.subhead,
            color = Color.White.copy(alpha = 0.85f),
        )
        Spacer(Modifier.height(4.dp))
        Text(text = title, style = typography.largeTitle, color = Color.White)
        if (today != null && today.limitMinutes != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                text =
                buildString {
                    append("Использовано ").append(formatUsageMs(today.usedMs))
                    append(" из ").append(formatUsageMs((today.limitMinutes + today.bonusMinutes) * 60_000L))
                    if (today.bonusMinutes > 0) append(" (+").append(today.bonusMinutes).append(" мин за задания)")
                },
                style = typography.footnote,
                color = Color.White.copy(alpha = 0.85f),
            )
        }
    }
}

@Composable
private fun UpdateCard(update: UpdateStatus, apkInstaller: ApkInstaller) {
    val colors = LocalAppColors.current
    val typography = LocalAppTypography.current
    val scope = rememberCoroutineScope()
    var downloading by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier
            .padding(horizontal = 16.dp)
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
                                    ApkInstaller.Outcome.INSTALLER_OPENED -> "Подтверди установку"
                                    ApkInstaller.Outcome.BROWSER_OPENED -> "Файл скачивается в браузере"
                                    ApkInstaller.Outcome.NEEDS_INSTALL_PERMISSION -> "Разреши установку и нажми ещё раз"
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
