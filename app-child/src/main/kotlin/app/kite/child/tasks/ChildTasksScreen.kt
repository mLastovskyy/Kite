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
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.kite.child.identity.MemberIdentity
import app.kite.core.approval.ApprovalRequest
import app.kite.core.approval.ApprovalsRemote
import app.kite.core.design.LocalAppColors
import app.kite.core.design.LocalAppTypography
import app.kite.core.design.components.AppButton
import app.kite.core.design.components.AppButtonStyle
import app.kite.core.design.components.AppSpinner
import app.kite.core.tasks.ChildTask
import kotlinx.coroutines.launch

/**
 * «Мои задания» on the child device: the same tasks the block screen offers, in a place the
 * child can open at any time — an exhausted limit should never be the only way to find out
 * that time can be earned. The list is read from the offline cache, so it opens with no
 * network; «Выполнил» is queued when the request cannot go out yet.
 */
@Composable
fun ChildTasksScreen(
    tasksStore: TasksStore,
    tasksSyncer: TasksSyncer,
    identity: MemberIdentity,
    approvalsRemote: ApprovalsRemote,
    bonusMinutesToday: Int,
    onClose: () -> Unit,
) {
    val colors = LocalAppColors.current
    val typography = LocalAppTypography.current
    val scope = rememberCoroutineScope()

    var tasks by remember { mutableStateOf(tasksStore.visible()) }
    var refreshing by remember { mutableStateOf(true) }
    var requested by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        tasks = tasksSyncer.refresh()
        refreshing = false
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
            Text(text = "Задания", style = typography.largeTitle, color = colors.textPrimary, modifier = Modifier.weight(1f))
            AppButton(text = "Закрыть", style = AppButtonStyle.Plain, onClick = onClose)
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
                    AppSpinner(color = colors.accent, size = 26.dp)
                }

            tasks.isEmpty() ->
                Text(
                    text = "Пока заданий нет. Можно попросить родителя дать задание.",
                    style = typography.body,
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp),
                )

            else ->
                tasks.forEach { task ->
                    TaskCard(
                        task = task,
                        onDone = {
                            scope.launch {
                                tasksSyncer.markDone(task.id)
                                tasks = tasksStore.visible()
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
            onClick = {
                scope.launch {
                    val familyId = identity.familyId()
                    val memberId = identity.memberId()
                    if (familyId == null || memberId == null) {
                        note = "Устройство ещё не привязано"
                        return@launch
                    }
                    approvalsRemote.create(memberId, familyId, ApprovalRequest.TYPE_TASK_REQUEST)
                        .onSuccess {
                            requested = true
                            note = "Родитель увидит запрос в Kite"
                        }
                        .onFailure { note = "Нет связи — попробуй позже" }
                }
            },
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

@Composable
private fun TaskCard(task: ChildTask, onDone: () -> Unit) {
    val colors = LocalAppColors.current
    val typography = LocalAppTypography.current
    val waiting = !task.isOpen

    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.bgBase)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(text = task.title, style = typography.headline, color = colors.textPrimary)
            Spacer(Modifier.height(2.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = "+${task.rewardMinutes} мин", style = typography.subhead, color = colors.accent)
                if (task.isRecurring) {
                    Text(text = "повторяется", style = typography.subhead, color = colors.textTertiary)
                }
            }
        }
        Spacer(Modifier.size(12.dp))
        if (waiting) {
            Text(text = "Ждём родителя", style = typography.subhead, color = colors.textSecondary)
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
                Text(text = "Выполнил", style = typography.headline, color = colors.accent)
            }
        }
    }
}
