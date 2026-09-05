package app.kite.parent.requests

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.kite.core.approval.ApprovalRequest
import app.kite.core.design.LocalAppColors
import app.kite.core.design.LocalAppTypography
import app.kite.core.design.components.AppButton
import app.kite.core.design.components.AppButtonStyle

/**
 * One pending request from the child, with the actions Kids360 puts on its cards. [askedFor]
 * names the parent the child chose to ask — the request still reaches everyone, so the others
 * can see whose answer is expected instead of guessing.
 */
@Composable
internal fun RequestCard(
    request: ApprovalRequest,
    busy: Boolean,
    askedFor: String? = null,
    onApprove: (minutes: Int, scopedToApp: Boolean) -> Unit,
    onDeny: () -> Unit,
    onOpenTasks: () -> Unit,
) {
    val colors = LocalAppColors.current
    val typography = LocalAppTypography.current
    var minutes by remember(request.id) { mutableIntStateOf(request.minutes ?: 15) }

    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        Text(
            text =
            when (request.type) {
                ApprovalRequest.TYPE_UNLOCK -> "Просит снять блокировку"
                ApprovalRequest.TYPE_EXTRA_TIME -> request.appLabel?.let { "Просит ещё время для «$it»" } ?: "Просит ещё время"
                ApprovalRequest.TYPE_REMOVAL -> "Просит разрешить удаление Kite Jr"
                ApprovalRequest.TYPE_TASK_REQUEST -> "Просит задание, чтобы заработать время"
                else -> "Запрос"
            },
            style = typography.headline,
            color = colors.textPrimary,
        )
        askedFor?.let {
            Spacer(Modifier.height(2.dp))
            Text(text = it, style = typography.footnote, color = colors.textSecondary)
        }
        Spacer(Modifier.height(12.dp))
        when (request.type) {
            ApprovalRequest.TYPE_EXTRA_TIME -> {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(15, 30, 60).forEach { m ->
                        val on = minutes == m
                        Box(
                            Modifier
                                .weight(1f)
                                .height(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (on) colors.accent else colors.fillQuaternary)
                                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { minutes = m },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "$m мин",
                                style = typography.subhead.copy(fontWeight = FontWeight.SemiBold),
                                color = if (on) Color.White else colors.textPrimary,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                // Primary action full width, «Отклонить» as a plain text button below: half-width
                // buttons clipped every Russian label on a 360dp phone.
                AppButton(
                    text = if (request.packageName != null) "Дать приложению" else "Дать время",
                    loading = busy,
                    onClick = { onApprove(minutes, request.packageName != null) },
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    if (request.packageName != null) {
                        AppButton(text = "Дать на все приложения", style = AppButtonStyle.Plain, enabled = !busy, onClick = {
                            onApprove(minutes, false)
                        })
                    }
                    AppButton(text = "Отклонить", style = AppButtonStyle.Plain, enabled = !busy, onClick = onDeny)
                }
            }
            ApprovalRequest.TYPE_TASK_REQUEST -> {
                AppButton(text = "К заданиям", onClick = onOpenTasks)
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    AppButton(text = "Отклонить", style = AppButtonStyle.Plain, enabled = !busy, onClick = onDeny)
                }
            }
            else -> {
                AppButton(
                    text = if (request.type == ApprovalRequest.TYPE_REMOVAL) "Разрешить" else "Разблокировать",
                    loading = busy,
                    onClick = { onApprove(0, false) },
                )
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    AppButton(text = "Отклонить", style = AppButtonStyle.Plain, enabled = !busy, onClick = onDeny)
                }
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(text = "Ребёнок увидит ответ сразу", style = typography.caption, color = colors.textTertiary)
        Spacer(Modifier.width(0.dp))
    }
}
