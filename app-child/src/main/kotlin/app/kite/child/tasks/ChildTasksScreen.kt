package app.kite.child.tasks

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
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.kite.child.request.AskParentDialog
import app.kite.child.request.ChildRequestSender
import app.kite.core.approval.ApprovalRequest
import app.kite.core.design.LocalAppColors
import app.kite.core.design.LocalAppTypography
import app.kite.core.design.components.AppButton
import app.kite.core.design.components.AppButtonStyle
import app.kite.core.design.components.AppIcon
import app.kite.core.design.components.EmptyState
import app.kite.core.design.components.KiteIcons
import app.kite.core.design.components.KiteLoader
import app.kite.core.design.components.PhotoThumbnail
import app.kite.core.design.components.PhotoViewer
import app.kite.core.design.components.rememberTitleCollapse
import app.kite.core.tasks.ChildTask
import kotlinx.coroutines.launch
import java.io.File

/**
 * «Мои задания» on the child device: the same tasks the block screen offers, in a place the
 * child can open at any time — an exhausted limit should never be the only way to find out
 * that time can be earned. The list is read from the offline cache, so it opens with no
 * network; «Выполнил» is queued when the request cannot go out yet.
 */
@Composable
fun ChildTasksScreen(tasksStore: TasksStore, tasksSyncer: TasksSyncer, requestSender: ChildRequestSender, bonusMinutesToday: Int) {
    val colors = LocalAppColors.current
    val typography = LocalAppTypography.current
    val scope = rememberCoroutineScope()

    var tasks by remember { mutableStateOf(tasksStore.visible()) }
    var refreshing by remember { mutableStateOf(true) }
    var requested by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }
    var asking by remember { mutableStateOf(false) }
    // Photos picked but not sent yet, by task id: the child attaches first and presses
    // «Выполнил» after, so the file has to wait somewhere until then.
    var attached by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var attaching by remember { mutableStateOf<String?>(null) }
    var viewing by remember { mutableStateOf<Any?>(null) }

    attaching?.let { taskId ->
        TaskPhotoChooser(
            store = tasksStore,
            taskId = taskId,
            onPicked = {
                attached = attached + (taskId to it)
                attaching = null
            },
            onDismiss = { attaching = null },
        )
    }
    viewing?.let { PhotoViewer(model = it, onDismiss = { viewing = null }) }

    if (asking) {
        AskParentDialog(
            sender = requestSender,
            onPick = { parent ->
                asking = false
                scope.launch {
                    requestSender.send(ApprovalRequest.TYPE_TASK_REQUEST, target = parent)
                        .onSuccess {
                            requested = true
                            note = "Родитель увидит запрос в Kite"
                        }
                        .onFailure { note = "Нет связи — попробуй позже" }
                }
            },
            onDismiss = { asking = false },
        )
    }

    LaunchedEffect(Unit) {
        tasks = tasksSyncer.refresh()
        refreshing = false
    }

    val scroll = rememberScrollState()
    val collapse = rememberTitleCollapse(scroll)
    Column(
        Modifier
            .fillMaxSize()
            .background(colors.bgGrouped)
            .safeDrawingPadding()
            .verticalScroll(scroll)
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Задания",
                style = typography.largeTitle,
                color = colors.textPrimary,
                modifier = Modifier.weight(1f).graphicsLayer { alpha = 1f - collapse.value },
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Выполни задание и нажми «Выполнил». Родитель подтвердит — время добавится на сегодня.",
            style = typography.subhead,
            color = colors.textSecondary,
        )

        if (bonusMinutesToday > 0) {
            Spacer(Modifier.height(14.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.success.copy(alpha = 0.15f))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text(
                    text = "Сегодня уже добавлено +$bonusMinutesToday мин",
                    style = typography.headline,
                    color = colors.textPrimary,
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        when {
            refreshing && tasks.isEmpty() ->
                Box(Modifier.fillMaxWidth().height(140.dp), contentAlignment = Alignment.Center) {
                    KiteLoader(size = 64.dp)
                }

            tasks.isEmpty() ->
                EmptyState(icon = KiteIcons.ListChecks, text = "Пока заданий нет. Можно попросить родителя дать задание.")

            else ->
                tasks.forEach { task ->
                    val queued = tasksStore.pendingPhoto(task.id)?.takeIf { !it.startsWith("http") }
                    val local = attached[task.id] ?: queued
                    TaskCard(
                        task = task,
                        photo = local?.let(::File) ?: task.photoUrl,
                        mine = attached[task.id] != null,
                        onAttach = { attaching = task.id },
                        onDetach = { attached = attached - task.id },
                        onOpenPhoto = { viewing = it },
                        onDone = {
                            scope.launch {
                                tasksSyncer.markDone(task.id, attached[task.id])
                                attached = attached - task.id
                                // Reconcile with the server, not just with the cache: a task the
                                // parent deleted must not sit here waiting for a confirmation
                                // that can never come.
                                tasks = tasksSyncer.refresh()
                            }
                        },
                    )
                    Spacer(Modifier.height(10.dp))
                }
        }

        Spacer(Modifier.height(14.dp))
        AppButton(
            text = if (requested) "Запрос отправлен" else "Попросить задание",
            style = AppButtonStyle.Tinted,
            enabled = !requested,
            onClick = { asking = true },
        )
        note?.let {
            Spacer(Modifier.height(8.dp))
            Text(
                text = it,
                style = typography.footnote,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(28.dp))
    }
}

/**
 * One task. [photo] is whatever should stand as the proof — a local `File` still waiting to go
 * up ([mine], so it can still be taken back), or the URL of the one already sent. Attaching is
 * optional: a task is finished by pressing «Выполнил», with or without a picture.
 */
@Composable
private fun TaskCard(
    task: ChildTask,
    photo: Any?,
    mine: Boolean,
    onAttach: () -> Unit,
    onDetach: () -> Unit,
    onOpenPhoto: (Any) -> Unit,
    onDone: () -> Unit,
) {
    val colors = LocalAppColors.current
    val typography = LocalAppTypography.current
    val waiting = task.isDone

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.bgBase)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(text = task.title, style = typography.headline, color = colors.textPrimary)
                Spacer(Modifier.height(2.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = "+${task.rewardMinutes} мин", style = typography.subhead, color = colors.accent)
                    if (task.isRejected) {
                        Text(text = "не принято", style = typography.subhead, color = colors.warning)
                    }
                    if (task.isRecurring) {
                        Text(text = "повторяется", style = typography.subhead, color = colors.textTertiary)
                    }
                }
            }
            Spacer(Modifier.size(12.dp))
            if (waiting) {
                Text(text = "Ждём подтверждения", style = typography.subhead, color = colors.textSecondary)
            } else {
                // A compact pill, not AppButton: the tinted style is full-width by design.
                Box(
                    Modifier
                        .clip(RoundedCornerShape(11.dp))
                        .background(colors.accent.copy(alpha = 0.15f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onDone,
                        )
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    Text(
                        text = if (task.isRejected) "Сделать снова" else "Выполнил",
                        style = typography.headline,
                        color = colors.accent,
                    )
                }
            }
        }
        if (photo == null && waiting) return@Column
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (photo != null) {
                PhotoThumbnail(model = photo, modifier = Modifier.size(56.dp), onClick = { onOpenPhoto(photo) })
                Spacer(Modifier.width(10.dp))
                // A photo already sent cannot be taken back, only replaced by the next attempt.
                when {
                    waiting -> Unit
                    mine -> PhotoAction(icon = KiteIcons.X, text = "Убрать фото", onClick = onDetach)
                    else -> PhotoAction(icon = KiteIcons.Paperclip, text = "Другое фото", onClick = onAttach)
                }
            } else {
                PhotoAction(icon = KiteIcons.Paperclip, text = "Прикрепить фото", onClick = onAttach)
            }
        }
    }
}

/** A glyph and a word: the two things that can be done to a task's photo. */
@Composable
private fun PhotoAction(icon: Int, text: String, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    val typography = LocalAppTypography.current
    Row(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(icon = icon, tint = colors.textSecondary, size = 16.dp)
        Spacer(Modifier.width(6.dp))
        Text(text = text, style = typography.subhead, color = colors.textSecondary)
    }
}
