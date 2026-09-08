package app.kite.core.tasks

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One line of «История заданий»: something happened to a task and this is what and by whom
 * (owner, 08.09.2026 — creating, editing and deleting each get their own record, not just the
 * end state of the task). Written by a database trigger, never by the client, so the child's
 * «Выполнил» and a second parent acting from another phone land in the same log.
 *
 * [title] and [rewardMinutes] are the snapshot at that moment: editing a task later must not
 * rewrite what the history already said about it.
 */
@Serializable
data class TaskEvent(
    val id: String,
    @SerialName("family_id") val familyId: String,
    @SerialName("task_id") val taskId: String,
    @SerialName("child_member_id") val childMemberId: String,
    /** Auth user id of whoever did it; null only for rows backfilled from older tasks. */
    val actor: String? = null,
    val kind: String,
    val title: String,
    @SerialName("reward_minutes") val rewardMinutes: Int,
    @SerialName("created_at") val createdAt: String,
) {
    val isConfirmed: Boolean get() = kind == CONFIRMED

    companion object {
        const val CREATED = "created"

        /** A recurring task came back after the parent confirmed it — not a new task. */
        const val REPEATED = "repeated"
        const val UPDATED = "updated"
        const val DONE = "done"
        const val CONFIRMED = "confirmed"
        const val REJECTED = "rejected"
        const val DELETED = "deleted"
    }
}
