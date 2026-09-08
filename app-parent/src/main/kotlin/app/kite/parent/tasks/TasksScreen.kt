package app.kite.parent.tasks

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.kite.core.approval.ApprovalRequest
import app.kite.core.approval.ApprovalsRemote
import app.kite.core.approval.TimeGrant
import app.kite.core.approval.TimeGrantsRemote
import app.kite.core.commands.CommandsRemote
import app.kite.core.commands.DeviceCommand
import app.kite.core.design.AppColors
import app.kite.core.design.LocalAppColors
import app.kite.core.design.LocalAppTypography
import app.kite.core.design.components.AppButton
import app.kite.core.design.components.AppButtonStyle
import app.kite.core.design.components.AppDialog
import app.kite.core.design.components.AppIcon
import app.kite.core.design.components.AvatarPreset
import app.kite.core.design.components.BackHeader
import app.kite.core.design.components.CircleIconButton
import app.kite.core.design.components.EmptyState
import app.kite.core.design.components.IconTile
import app.kite.core.design.components.InsetGroup
import app.kite.core.design.components.InsetGroupedList
import app.kite.core.design.components.KiteAvatar
import app.kite.core.design.components.KiteIcons
import app.kite.core.design.components.ScreenLoading
import app.kite.core.design.components.rowIcon
import app.kite.core.family.FamilyMember
import app.kite.core.tasks.ChildTask
import app.kite.core.tasks.TaskEvent
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
    grantsRemote: TimeGrantsRemote,
    parents: List<FamilyMember>,
    myMemberId: String?,
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
    var showHistory by remember { mutableStateOf(false) }
    BackHandler(enabled = creating || editing != null || showHistory) {
        creating = false
        editing = null
        showHistory = false
    }
    var deleting by remember { mutableStateOf<ChildTask?>(null) }
    // Minutes are handed out (or refused) the moment this is tapped, so it asks first.
    var resolving by remember { mutableStateOf<Pair<ChildTask, Boolean>?>(null) }
    var unpinning by remember { mutableStateOf<SavedTask?>(null) }
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

    val context = LocalContext.current
    val savedTasks = remember { SavedTasksStore(context) }
    var saved by remember { mutableStateOf(savedTasks.all()) }

    val child = selected
    if (child != null && (creating || editing != null)) {
        TaskEditorScreen(
            childName = child.displayName.ifBlank { "Ребёнок" },
            initial = editing,
            onSave = { title, reward, days, pin ->
                if (pin) {
                    savedTasks.add(title, reward)
                    saved = savedTasks.all()
                }
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

    if (showHistory && child != null) {
        TaskHistoryScreen(
            childMemberId = child.id,
            tasksRemote = tasksRemote,
            parents = parents,
            onBack = { showHistory = false },
        )
        return
    }

    unpinning?.let { pinned ->
        AppDialog(
            title = "Убрать из быстрых?",
            message = "«${pinned.title}» пропадёт из списка. Уже выданные задания останутся.",
            confirmText = "Убрать",
            destructive = true,
            onConfirm = {
                savedTasks.remove(pinned.title)
                saved = savedTasks.all()
                unpinning = null
            },
            onDismiss = { unpinning = null },
        )
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
                        grantsRemote.record(
                            familyId = familyId,
                            childMemberId = target.id,
                            minutes = task.rewardMinutes,
                            grantedBy = myMemberId,
                            source = TimeGrant.SOURCE_TASK,
                        )
                        if (task.isRecurring) {
                            tasksRemote.create(
                                familyId,
                                target.id,
                                task.title,
                                task.rewardMinutes,
                                task.repeatDays.toSet(),
                                // «повторилось» in the history — nobody created it a second time.
                                fromRepeat = true,
                            )
                        }
                    }
                }
                .onFailure { error = it.message }
            busyId = null
            reloadKey++
        }
    }

    resolving?.let { (task, confirmed) ->
        AppDialog(
            title = if (confirmed) "Принять задание?" else "Отклонить задание?",
            message =
            if (confirmed) {
                "«${task.title}» · ребёнку добавится +${task.rewardMinutes} мин к сегодняшнему лимиту."
            } else {
                "«${task.title}» останется в списке — ребёнок сможет выполнить его снова."
            },
            confirmText = if (confirmed) "Принять" else "Отклонить",
            destructive = !confirmed,
            onConfirm = {
                resolving = null
                resolve(task, confirmed = confirmed)
            },
            onDismiss = { resolving = null },
        )
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
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Задания",
                style = typography.largeTitle,
                color = colors.textPrimary,
                modifier = Modifier.weight(1f),
            )
            CircleIconButton(icon = KiteIcons.Clock, size = 38.dp, onClick = { showHistory = true })
        }
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
            ScreenLoading(caption = "Загружаем задания…", height = 160.dp)
            return@Column
        }

        val today = LocalDate.now()
        val awaiting = list.filter { it.isDone }
        // Rejected tasks stay in «Активные»: the child can still do them (owner, 07.09.2026).
        val open = list.filter { it.isOpen || it.isRejected }
        val confirmedToday = list.filter { it.isConfirmed && it.doneAt?.let(::isoDay) == today }
        val earnedToday = confirmedToday.sumOf { it.rewardMinutes }

        Text(
            text =
            if (earnedToday > 0) {
                "Выполнено сегодня: ${confirmedToday.size} · +$earnedToday мин к лимиту"
            } else {
                "Минуты за задания добавляются к лимиту."
            },
            style = typography.subhead,
            color = colors.textSecondary,
        )
        Spacer(Modifier.height(16.dp))

        // Быстрые задания: only what this parent pinned while creating a task — the app does
        // not invent chores for somebody else's family. A pinned task stays on the shelf even
        // while a copy of it is open, so it can be handed out again tomorrow.
        val handedOut = list.filter { it.isOpen || it.isDone || it.isRejected }.map { it.title.lowercase() }.toSet()
        if (saved.isNotEmpty()) {
            Text(
                text = "Быстрые задания",
                style = typography.footnote,
                color = colors.textSecondary,
                modifier = Modifier.padding(start = 4.dp),
            )
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                saved.forEach { item ->
                    val already = item.title.lowercase() in handedOut
                    ReadyTaskChip(
                        title = item.title,
                        minutes = item.rewardMinutes,
                        already = already,
                        enabled = busyId == null && !already,
                        onRemove = { unpinning = item },
                        onClick = {
                            scope.launch {
                                tasksRemote.create(familyId, child.id, item.title, item.rewardMinutes, emptySet())
                                    .onFailure { error = it.message }
                                reloadKey++
                            }
                        },
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        if (list.isEmpty()) {
            EmptyState(icon = KiteIcons.ListChecks, text = "Заданий пока нет. Придумайте первое — минуты за него добавятся к лимиту.")
            Spacer(Modifier.height(12.dp))
            AppButton(text = "Новое задание", onClick = { creating = true })
            Spacer(Modifier.height(32.dp))
            return@Column
        }

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
                            // One full-width primary action; the secondary is a plain text button
                            // underneath — two half-width buttons cannot hold Russian labels.
                            AppButton(text = "Создать задание", onClick = { creating = true })
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                AppButton(
                                    text = "Отклонить",
                                    style = AppButtonStyle.Plain,
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
                                AppButton(
                                    text = "Подтвердить",
                                    loading = busyId == task.id,
                                    enabled = busyId == null,
                                    onClick = { resolving = task to true },
                                )
                                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                    AppButton(
                                        text = "Отклонить",
                                        style = AppButtonStyle.Plain,
                                        enabled = busyId == null,
                                        onClick = { resolving = task to false },
                                    )
                                }
                            }
                        }
                    }
                }
            }
            if (open.isNotEmpty()) {
                InsetGroup(header = "Активные") {
                    open.forEach { task ->
                        custom(separatorInset = 57.dp) {
                            TaskRow(task = task, onEdit = { editing = task }, onDelete = { deleting = task })
                        }
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

/** Reward a one-tap task carries; the parent can still edit it afterwards. */
private const val QUICK_TASK_MINUTES = 15

@Composable
private fun ReadyTaskChip(title: String, minutes: Int, already: Boolean, enabled: Boolean, onRemove: () -> Unit, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    val typography = LocalAppTypography.current
    Row(
        Modifier
            .widthIn(max = 220.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(colors.bgBase)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, enabled = enabled, onClick = onClick)
            .padding(start = 14.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f, fill = false)) {
            Text(
                text = title,
                style = typography.subhead,
                color = if (already) colors.textTertiary else colors.textPrimary,
                maxLines = 2,
            )
            Text(
                text = if (already) "Уже выдано" else "+$minutes мин",
                style = typography.caption,
                color = if (already) colors.textTertiary else colors.success,
            )
        }
        Spacer(Modifier.width(6.dp))
        Box(
            Modifier
                .size(28.dp)
                .clip(CircleShape)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) {
            AppIcon(icon = KiteIcons.X, tint = colors.textTertiary, size = 14.dp)
        }
    }
}

private fun isoDay(iso: String): LocalDate? = runCatching { OffsetDateTime.parse(iso).toLocalDate() }.getOrNull()

/** «4 сент» for the task history. */
private fun shortDate(iso: String?): String = runCatching {
    OffsetDateTime.parse(
        iso,
    ).toLocalDate().format(java.time.format.DateTimeFormatter.ofPattern("d MMM", java.util.Locale.forLanguageTag("ru")))
}.getOrDefault("")

/** Label, glyph and colour for one kind of task event. */
private data class EventLook(val label: String, val icon: Int, val tint: Color, val verb: String?)

private fun eventLook(event: TaskEvent, colors: AppColors): EventLook = when (event.kind) {
    TaskEvent.CREATED -> EventLook("создано", KiteIcons.Plus, colors.accent, "создал(а)")
    // The recurring task came back by itself, so naming a parent here would be a lie.
    TaskEvent.REPEATED -> EventLook("повторилось", KiteIcons.Refresh, colors.textTertiary, null)
    TaskEvent.UPDATED -> EventLook("изменено", KiteIcons.Pencil, colors.warning, "изменил(а)")
    TaskEvent.DONE -> EventLook("выполнено", KiteIcons.Check, colors.info, null)
    TaskEvent.CONFIRMED -> EventLook("+${event.rewardMinutes} мин", KiteIcons.CircleCheck, colors.success, "подтвердил(а)")
    TaskEvent.REJECTED -> EventLook("не принято", KiteIcons.CircleX, colors.danger, "отклонил(а)")
    TaskEvent.DELETED -> EventLook("удалено", KiteIcons.Trash, colors.textTertiary, "удалил(а)")
    else -> EventLook(event.kind, KiteIcons.Info, colors.textTertiary, null)
}

/**
 * «История заданий» — one line per thing that happened, newest day first: created, edited,
 * done, confirmed, rejected, deleted (owner, 08.09.2026 — the end state alone did not say
 * where a task went). It lives behind the clock button instead of the bottom of the tab: the
 * tab is for what still needs an answer.
 */
@Composable
private fun TaskHistoryScreen(childMemberId: String, tasksRemote: TasksRemote, parents: List<FamilyMember>, onBack: () -> Unit) {
    val colors = LocalAppColors.current
    var events by remember(childMemberId) { mutableStateOf<List<TaskEvent>?>(null) }
    var failed by remember(childMemberId) { mutableStateOf<String?>(null) }
    LaunchedEffect(childMemberId) {
        tasksRemote.events(childMemberId)
            .onSuccess {
                events = it
                failed = null
            }
            .onFailure {
                events = emptyList()
                failed = it.message
            }
    }
    val byUser = parents.associateBy { it.userId }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.bgGrouped)
            .safeContentPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        BackHeader(title = "История заданий", onBack = onBack)
        Spacer(Modifier.height(20.dp))
        val list = events
        if (list == null) {
            ScreenLoading()
            return@Column
        }
        if (list.isEmpty()) {
            EmptyState(icon = KiteIcons.CircleCheck, text = failed ?: "Здесь появится всё, что происходило с заданиями.")
            return@Column
        }
        val today = LocalDate.now()
        InsetGroupedList {
            list.groupBy { isoDay(it.createdAt) }.forEach { (day, items) ->
                val earned = items.filter { it.isConfirmed }.sumOf { it.rewardMinutes }
                InsetGroup(
                    header = if (day == today) "Сегодня" else shortDate(items.first().createdAt),
                    footer = if (earned == 0) null else "+$earned мин к лимиту",
                ) {
                    items.forEach { event ->
                        val look = eventLook(event, colors)
                        val author = event.actor?.let(byUser::get)?.takeIf { parents.size > 1 && look.verb != null }
                        row(
                            title = event.title,
                            value = look.label,
                            // Who did it, and only when there is more than one parent to confuse.
                            subtitle = author?.displayName?.ifBlank { null }?.let { "${look.verb} $it" },
                            icon = rowIcon(look.icon, look.tint),
                            trailing =
                            author?.let { parent ->
                                {
                                    KiteAvatar(
                                        preset = AvatarPreset.byId(parent.avatarKind),
                                        size = 24.dp,
                                        avatarUrl = parent.avatarUrl,
                                    )
                                }
                            },
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}
