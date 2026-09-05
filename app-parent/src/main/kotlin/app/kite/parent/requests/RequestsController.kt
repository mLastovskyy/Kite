package app.kite.parent.requests

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import app.kite.core.approval.ApprovalRequest
import app.kite.core.approval.ApprovalsRemote
import app.kite.core.approval.TimeGrant
import app.kite.core.approval.TimeGrantsRemote
import app.kite.core.commands.CommandsRemote
import app.kite.core.commands.DeviceCommand
import app.kite.core.realtime.RealtimeTable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Every pending request in the family, in one place: the tab badge, «Главная» and the
 * «Запросы» screen all read this, so a request from a child who is not currently selected
 * can never go unnoticed. Answering is instant on the child's side — the effect travels as a
 * device command (Realtime + push) while the request row is only marked resolved.
 */
class RequestsController(
    private val familyId: String,
    private val approvalsRemote: ApprovalsRemote,
    private val commandsRemote: CommandsRemote,
    private val grantsRemote: TimeGrantsRemote,
    private val scope: CoroutineScope,
) {
    var requests by mutableStateOf<List<ApprovalRequest>>(emptyList())
        private set
    var busy by mutableStateOf<String?>(null)
        private set

    var myMemberId: String? = null

    val count: Int get() = requests.size

    fun forChild(childMemberId: String): List<ApprovalRequest> = requests.filter { it.childMemberId == childMemberId }

    fun reload(): Job = scope.launch {
        approvalsRemote.pending(familyId).onSuccess { requests = it }
    }

    fun resolve(
        request: ApprovalRequest,
        approve: Boolean,
        minutes: Int = 15,
        scopeToApp: Boolean = false,
        onDone: (String?) -> Unit = {},
    ) {
        scope.launch {
            busy = request.id
            var note: String? = null
            if (approve) {
                when (request.type) {
                    ApprovalRequest.TYPE_UNLOCK -> {
                        commandsRemote.send(request.childMemberId, familyId, DeviceCommand.UNLOCK)
                        note = "Блокировка снимается"
                    }
                    ApprovalRequest.TYPE_EXTRA_TIME -> {
                        val pkg = request.packageName?.takeIf { scopeToApp }
                        val payload = if (pkg != null) """{"minutes":$minutes,"package":"$pkg"}""" else """{"minutes":$minutes}"""
                        commandsRemote.send(request.childMemberId, familyId, DeviceCommand.GRANT_TIME, payloadJson = payload)
                        grantsRemote.record(
                            familyId = familyId,
                            childMemberId = request.childMemberId,
                            minutes = minutes,
                            grantedBy = myMemberId,
                            packageName = pkg,
                            source = TimeGrant.SOURCE_REQUEST,
                        )
                        note = "Добавлено $minutes мин"
                    }
                    ApprovalRequest.TYPE_REMOVAL -> {
                        commandsRemote.send(request.childMemberId, familyId, DeviceCommand.ALLOW_REMOVAL)
                        note = "Удаление разрешено"
                    }
                }
            }
            approvalsRemote.resolve(request.id, if (approve) ApprovalRequest.STATUS_APPROVED else ApprovalRequest.STATUS_REJECTED)
            requests = requests.filterNot { it.id == request.id }
            busy = null
            onDone(note)
            reload()
        }
    }
}

/** Creates the controller for [familyId] and keeps it fed by Realtime for as long as it is shown. */
@Composable
fun rememberRequestsController(
    familyId: String,
    myMemberId: String?,
    approvalsRemote: ApprovalsRemote,
    commandsRemote: CommandsRemote,
    grantsRemote: TimeGrantsRemote,
    realtime: RealtimeTable,
): RequestsController {
    val scope = rememberCoroutineScope()
    val controller = remember(familyId) { RequestsController(familyId, approvalsRemote, commandsRemote, grantsRemote, scope) }
    controller.myMemberId = myMemberId
    DisposableEffect(familyId) {
        val job = scope.launch {
            controller.reload()
            realtime.subscribe(
                scope = this,
                table = "approval_requests",
                filter = "family_id=eq.$familyId",
                events = listOf(RealtimeTable.EVENT_INSERT, RealtimeTable.EVENT_UPDATE),
            ) { controller.reload() }
        }
        onDispose { job.cancel() }
    }
    return controller
}
