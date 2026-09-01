package app.kite.parent.family

import androidx.compose.foundation.background
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
import app.kite.core.approval.ApprovalRequest
import app.kite.core.approval.ApprovalsRemote
import app.kite.core.commands.CommandsRemote
import app.kite.core.commands.DeviceCommand
import app.kite.core.design.LocalAppColors
import app.kite.core.design.LocalAppTypography
import app.kite.core.design.components.AppButton
import app.kite.core.design.components.AppButtonStyle
import app.kite.core.design.components.AppSpinner
import app.kite.core.family.FamilyMember
import kotlinx.coroutines.launch

/**
 * Parent's pending approval requests (unlock / extra time). Approving delivers the effect
 * as a device_command (instant via Realtime + push) and resolves the request; denying just
 * resolves it. The child sees the outcome through the same command channel.
 */
@Composable
fun ApprovalsScreen(
    familyId: String,
    members: List<FamilyMember>,
    approvalsRemote: ApprovalsRemote,
    commandsRemote: CommandsRemote,
    onClose: () -> Unit,
) {
    val colors = LocalAppColors.current
    val typography = LocalAppTypography.current
    val scope = rememberCoroutineScope()

    var requests by remember { mutableStateOf<List<ApprovalRequest>?>(null) }
    var reloadKey by remember { mutableStateOf(0) }
    var busyId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(reloadKey) {
        requests = approvalsRemote.pending(familyId).getOrNull() ?: emptyList()
    }

    fun childName(memberId: String): String = members.firstOrNull { it.id == memberId }?.displayName?.ifBlank { "Ребёнок" } ?: "Ребёнок"

    fun resolve(request: ApprovalRequest, approve: Boolean) {
        scope.launch {
            busyId = request.id
            if (approve) {
                when (request.type) {
                    ApprovalRequest.TYPE_UNLOCK ->
                        commandsRemote.send(request.childMemberId, familyId, DeviceCommand.UNLOCK)
                    ApprovalRequest.TYPE_EXTRA_TIME ->
                        commandsRemote.send(
                            request.childMemberId,
                            familyId,
                            DeviceCommand.GRANT_TIME,
                            payloadJson = """{"minutes":${request.minutes ?: 15}}""",
                        )
                }
            }
            approvalsRemote.resolve(request.id, if (approve) ApprovalRequest.STATUS_APPROVED else ApprovalRequest.STATUS_REJECTED)
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
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(text = "Запросы", style = typography.title1, color = colors.textPrimary, modifier = Modifier.weight(1f))
            AppButton(text = "Закрыть", style = AppButtonStyle.Plain, onClick = onClose)
        }
        Spacer(Modifier.height(16.dp))

        val list = requests
        when {
            list == null ->
                Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                    AppSpinner(color = colors.accent, size = 28.dp)
                }

            list.isEmpty() ->
                Text(
                    text = "Нет новых запросов",
                    style = typography.body,
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
                )

            else ->
                list.forEach { request ->
                    RequestCard(
                        title = childName(request.childMemberId),
                        subtitle = describe(request),
                        busy = busyId == request.id,
                        onApprove = { resolve(request, approve = true) },
                        onDeny = { resolve(request, approve = false) },
                    )
                    Spacer(Modifier.height(10.dp))
                }
        }
        Spacer(Modifier.height(24.dp))
    }
}

private fun describe(request: ApprovalRequest): String = when (request.type) {
    ApprovalRequest.TYPE_UNLOCK -> "Просит разблокировать телефон"
    ApprovalRequest.TYPE_EXTRA_TIME -> "Просит ещё ${request.minutes ?: 15} минут"
    ApprovalRequest.TYPE_REMOVAL -> "Просит разрешить удаление"
    else -> "Запрос"
}

@Composable
private fun RequestCard(title: String, subtitle: String, busy: Boolean, onApprove: () -> Unit, onDeny: () -> Unit) {
    val colors = LocalAppColors.current
    val typography = LocalAppTypography.current
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(colors.bgBase).padding(16.dp)) {
        Text(text = title, style = typography.headline, color = colors.textPrimary)
        Spacer(Modifier.height(2.dp))
        Text(text = subtitle, style = typography.subhead, color = colors.textSecondary)
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.weight(1f)) {
                AppButton(text = "Одобрить", loading = busy, onClick = onApprove)
            }
            Box(Modifier.weight(1f)) {
                AppButton(text = "Отклонить", style = AppButtonStyle.Tinted, onClick = onDeny)
            }
        }
    }
}
