package app.kite.parent.tasks

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.kite.core.approval.ApprovalRequest
import app.kite.core.approval.ApprovalsRemote
import app.kite.core.commands.CommandsRemote
import app.kite.core.commands.DeviceCommand
import app.kite.core.design.LocalAppColors
import app.kite.core.design.LocalAppTypography
import app.kite.core.design.components.AppButton
import app.kite.core.design.components.AppButtonStyle
import app.kite.core.design.components.AppDialog
import app.kite.core.design.components.AppIcon
import app.kite.core.design.components.AppSpinner
import app.kite.core.design.components.IconTile
import app.kite.core.design.components.InsetGroup
import app.kite.core.design.components.InsetGroupedList
import app.kite.core.design.components.KiteIcons
import app.kite.core.family.FamilyMember
import app.kite.core.tasks.ChildTask
import app.kite.core.tasks.TasksRemote
import app.kite.parent.home.ChildSwitcher
import app.kite.parent.rules.daysSummary
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.OffsetDateTime

/**
 * «Задания» tab (Kids360 «Ваши задания»): what the child asked for, what awaits
 * confirmation («Ребёнок выполнил задание … [Отклонить] [Подтвердить]»), the open tasks,
 * and «Создать задание». Confirming grants the reward instantly as a grant_time device
 * command (the same path as approved extra time) and re-creates a recurring task as open.
 */
@Composable
fun TasksScreen(
    familyId: String,
    children: List<FamilyMember>,
    selected: FamilyMember?,
    onSelectChild: (FamilyMember) -> Unit,
    tasksRemote: TasksRemote,
    commandsRemote: CommandsRemote,
    approvalsRemote: ApprovalsRemote,
) {
    val colors = LocalAppColors.current
    val typography = LocalAppTypography.current
    val scope = rememberCoroutineScope()

    var tasks by remember { mutableStateOf<List<ChildTask>?>(null) }
    var requests by remember { mutableStateOf<List<ApprovalRequest>>(emptyList()) }
    var reloadKey by remember { mutableIntStateOf(0) }
    var busyId by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<ChildTask?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<ChildTask?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(selected?.id, reloadKey) {
        val child = selected ?: return@LaunchedEffect
        tasksRemote.list(familyId, child.id)
            .onSuccess {
                tasks = it
                error = null
            }
            .onFailure {
                tasks = tasks ?: emptyList()
                error = it.message
            }
        requests =
            approvalsRemote.pending(familyId).getOrNull().orEmpty()
                .filter { it.type == ApprovalRequest.TYPE_TASK_REQUEST && it.childMemberId == child.id }
    }

    val child = selected
    if (child != null && (creating || editing != null)) {
        TaskEditorScreen(
            childName = child.displayName.ifBlank { "Ребёнок" },
            initial = editing,
            onSave = { title, reward, days ->
                scope.launch {
                    val existing = editing
                    val result =
                        if (existing == null) {
                            tasksRemote.create(familyId, child.id, title, reward, days)
                        } else {
                            tasksRemote.update(existing.id, title, reward, days)
                        }
                    result.onFailure { error = it.message }
                    // A task created in answer to the child's request closes that request.
                    if (existing == null) {
                        requests.forEach { approvalsRemote.resolve(it.id, ApprovalRequest.STATUS_APPROVED) }
                    }
                    creating = false
                    editing = null
                    reloadKey++
                }
            },
            onCancel = {
                creating = false
                editing = null
            },
        )
        return
    }

    deleting?.let { task ->
        AppDialog(
            title = "Удалить задание?",
            message = task.title,
            confirmText = "Удалить",
            destructive = true,
            onConfirm = {
                deleting = null
                scope.launch {
                    tasksRemote.delete(task.id).onFailure { error = it.message }
                    reloadKey++
                }
            },
            onDismiss = { deleting = null },
        )
    }

    fun resolve(task: ChildTask, confirmed: Boolean) {
        val target = child ?: return
        scope.launch {
            busyId = task.id
            tasksRemote.resolve(task.id, confirmed)
                .onSuccess {
                    if (confirmed) {
                        commandsRemote.send(
                            target.id,
                            familyId,
                            DeviceCommand.GRANT_TIME,
                            payloadJson = """{"minutes":${task.rewardMinutes}}""",
                        )
                        if (task.isRecurring) {
                            tasksRemote.create(
                                familyId,
                                target.id,
                                task.title,
                                task.rewardMinutes,
                                task.repeatDays.toSet(),
                            )
                        }
                    }
                }
                .onFailure { error = it.message }
            busyId = null
            reloadKey++
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.bgGrouped)
            .safeContentPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(12.dp))
        Text(text = "Задания", style = typography.largeTitle, color = colors.textPrimary)
        Spacer(Modifier.height(12.dp))
        if (child == null) {
            Text(
                text = "Добавьте ребёнка, чтобы давать задания за экранное время.",
                style = typography.body,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
            )
            return@Column
        }
        ChildSwitcher(children = children, selected = child, onSelect = onSelectChild)
        Spacer(Modifier.height(16.dp))

        val list = tasks
        if (list == null) {
            Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                AppSpinner(color = colors.accent, size = 28.dp)
            }
            return@Column
        }

        val today = LocalDate.now()
        val awaiting = list.filter { it.isDone }
        val open = list.filter { it.isOpen }
        val confirmedToday = list.filter { it.isConfirmed && it.doneAt?.let(::isoDay) == today }
        val earnedToday = confirmedToday.sumOf { it.rewardMinutes }

        Text(
            text =
            if (earnedToday > 0) {
                "Выполнено сегодня: ${confirmedToday.size} · +$earnedToday мин к лимиту"
            } else {
                "Выполняя задания, ребёнок получает минуты к дневному лимиту."
            },
            style = typography.subhead,
            color = colors.textSecondary,
        )
        Spacer(Modifier.height(16.dp))

        InsetGroupedList {
            if (requests.isNotEmpty()) {
                InsetGroup(header = "Просит задание") {
                    custom {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Text(
                                text = "${child.displayName.ifBlank {
                                    "Ребёнок"
                                }} хочет заработать время",
                                style = typography.headline,
                                color = colors.textPrimary,
                            )
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                AppButton(text = "Создать задание", modifier = Modifier.weight(1f), onClick = { creating = true })
                                AppButton(
                                    text = "Отклонить",
                                    style = AppButtonStyle.Tinted,
                                    modifier = Modifier.weight(1f),
                                    onClick = {
                                        scope.launch {
                                            requests.forEach { approvalsRemote.resolve(it.id, ApprovalRequest.STATUS_REJECTED) }
                                            reloadKey++
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
            if (awaiting.isNotEmpty()) {
                InsetGroup(header = "Ждут подтверждения") {
                    awaiting.forEach { task ->
                        custom {
                            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                                Text(text = "Ребёнок выполнил задание", style = typography.footnote, color = colors.textSecondary)
                                Spacer(Modifier.height(2.dp))
                                Text(text = task.title, style = typography.headline, color = colors.textPrimary)
                                Text(text = "+${task.rewardMinutes} мин к лимиту", style = typography.subhead, color = colors.success)
                                Spacer(Modifier.height(12.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    AppButton(
                                        text = "Отклонить",
                                        style = AppButtonStyle.Tinted,
                                        enabled = busyId == null,
                                        modifier = Modifier.weight(1f),
                                        onClick = { resolve(task, confirmed = false) },
                                    )
                                    AppButton(
                                        text = "Подтвердить",
                                        loading = busyId == task.id,
                                        enabled = busyId == null,
                                        modifier = Modifier.weight(1f),
                                        onClick = { resolve(task, confirmed = true) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
            InsetGroup(header = if (open.isEmpty()) null else "Активные") {
                if (open.isEmpty()) {
                    custom {
                        Text(
                            text = "Заданий пока нет. Например: почитать книгу, прибраться в комнате, погулять.",
                            style = typography.body,
                            color = colors.textSecondary,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
                open.forEach { task ->
                    custom(separatorInset = 57.dp) {
                        TaskRow(task = task, onEdit = { editing = task }, onDelete = { deleting = task })
                    }
                }
            }
            if (confirmedToday.isNotEmpty()) {
                InsetGroup(header = "Выполнено сегодня") {
                    confirmedToday.forEach { task ->
                        row(
                            title = task.title,
                            value = "+${task.rewardMinutes} мин",
                            icon = app.kite.core.design.components.rowIcon(KiteIcons.CircleCheck, colors.success),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        AppButton(text = "Создать задание", onClick = { creating = true })
        error?.let {
            Spacer(Modifier.height(12.dp))
            Text(
                text = it,
                style = typography.footnote,
                color = colors.danger,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun TaskRow(task: ChildTask, onEdit: () -> Unit, onDelete: () -> Unit) {
    val colors = LocalAppColors.current
    val typography = LocalAppTypography.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onEdit)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(icon = KiteIcons.ListChecks, background = Color(0xFF5856D6))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(text = task.title, style = typography.body, color = colors.textPrimary)
            Text(
                text = "+${task.rewardMinutes} мин" + if (task.isRecurring) " · ${daysSummary(task.repeatDays)}" else "",
                style = typography.footnote,
                color = colors.textSecondary,
            )
        }
        Box(
            Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDelete)
                .padding(8.dp),
        ) {
            AppIcon(icon = KiteIcons.Trash, tint = colors.textTertiary, size = 20.dp)
        }
    }
}

private fun isoDay(iso: String): LocalDate? = runCatching { OffsetDateTime.parse(iso).toLocalDate() }.getOrNull()
