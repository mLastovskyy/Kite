package app.kite.child.request

import app.kite.child.identity.ChildParent
import app.kite.child.identity.MemberIdentity
import app.kite.child.identity.ParentsStore
import app.kite.core.approval.ApprovalsRemote

/**
 * The one place a child request is sent from. Every entry point — the block screen, «Задания»,
 * «Ещё» — resolves the same way: the chosen parent becomes the request's addressee and the
 * default for next time, and a family without a second parent never sees a picker at all.
 */
class ChildRequestSender(
    private val identity: MemberIdentity,
    private val approvalsRemote: ApprovalsRemote,
    private val parentsStore: ParentsStore,
) {
    fun parents(): List<ChildParent> = parentsStore.parents()

    fun needsChoice(): Boolean = parents().size > 1

    suspend fun refreshParents() = parentsStore.refresh()

    suspend fun send(type: String, payloadJson: String? = null, target: ChildParent?): Result<Unit> {
        val familyId = identity.familyId() ?: return Result.failure(NotLinked)
        val memberId = identity.memberId() ?: return Result.failure(NotLinked)
        val targetId = target?.memberId ?: parentsStore.preferredId().takeIf { !needsChoice() }
        if (target != null) parentsStore.choose(target.memberId)
        return approvalsRemote.create(
            childMemberId = memberId,
            familyId = familyId,
            type = type,
            payloadJson = payloadJson,
            childName = identity.displayName(),
            targetMemberId = targetId,
        )
    }

    object NotLinked : Exception("Устройство ещё не привязано")
}
