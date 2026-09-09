package app.kite.core.tasks

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A parent-assigned task («Задание») rewarded with screen-time minutes. Lifecycle:
 * `open` → `done` (child taps «Выполнил») → `confirmed` (parent; minutes granted as today's
 * bonus through a grant_time device command) or `rejected` (parent said no). A rejected task
 * stays on the child's list and can be done again — it disappears only when it is confirmed
 * or the parent deletes it (owner, 07.09.2026).
 * [repeatDays] (ISO 1 = Mon … 7 = Sun) makes the task recur: on confirmation the parent app
 * recreates it as `open`; empty = one-time.
 */
@Serializable
data class ChildTask(
    val id: String,
    @SerialName("family_id") val familyId: String,
    @SerialName("child_member_id") val childMemberId: String,
    val title: String,
    @SerialName("reward_minutes") val rewardMinutes: Int,
    val status: String = STATUS_OPEN,
    @SerialName("repeat_days") val repeatDays: List<Int> = emptyList(),
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("done_at") val doneAt: String? = null,
    /** Photo the child attached to this attempt, if any — public Storage URL. */
    @SerialName("photo_url") val photoUrl: String? = null,
    @SerialName("resolved_at") val resolvedAt: String? = null,
    /** Auth user id of the parent who confirmed, rejected or deleted it. */
    @SerialName("resolved_by") val resolvedBy: String? = null,
) {
    val isOpen: Boolean get() = status == STATUS_OPEN
    val isDone: Boolean get() = status == STATUS_DONE
    val isConfirmed: Boolean get() = status == STATUS_CONFIRMED
    val isRejected: Boolean get() = status == STATUS_REJECTED

    /** Deleted by a parent: gone from both lists, kept for the history. */
    val isDeleted: Boolean get() = status == STATUS_DELETED

    /** «Выполнил» is offered for a fresh task and for one that was sent back. */
    val canDo: Boolean get() = isOpen || isRejected
    val isRecurring: Boolean get() = repeatDays.isNotEmpty()

    /** A repeating task waits for its own weekdays; a one-off is always for today. */
    fun isForToday(isoDayOfWeek: Int): Boolean = repeatDays.isEmpty() || isoDayOfWeek in repeatDays

    companion object {
        const val STATUS_OPEN = "open"
        const val STATUS_DONE = "done"
        const val STATUS_CONFIRMED = "confirmed"
        const val STATUS_REJECTED = "rejected"
        const val STATUS_DELETED = "deleted"

        /** Reward chips in the parent UI, minutes (Kids360 offers the same six). */
        val REWARD_OPTIONS = listOf(5, 10, 15, 20, 30, 40)
        const val MIN_REWARD = 5
        const val MAX_REWARD = 240
        const val MAX_TITLE = 80

        /** Suggestion chips for the title field. */
        val TITLE_SUGGESTIONS =
            listOf(
                "Выучить 5 слов на английском",
                "Почитать книгу",
                "Выучить стихотворение",
                "Погулять",
                "Прибраться в комнате",
                "Сделать домашнее задание",
            )

        val WEEKDAY_SHORT = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")
    }
}
