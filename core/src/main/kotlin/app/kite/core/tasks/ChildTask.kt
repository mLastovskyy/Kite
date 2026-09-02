package app.kite.core.tasks

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A parent-assigned task («Задание») rewarded with screen-time minutes. Lifecycle:
 * `open` → `done` (child taps «Выполнил») → `confirmed` (parent; minutes granted as today's
 * bonus through a grant_time device command) or back to `open` (parent rejected).
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
) {
    val isOpen: Boolean get() = status == STATUS_OPEN
    val isDone: Boolean get() = status == STATUS_DONE
    val isConfirmed: Boolean get() = status == STATUS_CONFIRMED
    val isRecurring: Boolean get() = repeatDays.isNotEmpty()

    companion object {
        const val STATUS_OPEN = "open"
        const val STATUS_DONE = "done"
        const val STATUS_CONFIRMED = "confirmed"
        const val STATUS_REJECTED = "rejected"

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
