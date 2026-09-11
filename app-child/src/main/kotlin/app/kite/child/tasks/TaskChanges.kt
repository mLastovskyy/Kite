package app.kite.child.tasks

import app.kite.core.tasks.ChildTask

object TaskChanges {
    data class Change(val task: ChildTask, val kind: String)

    fun diff(before: List<ChildTask>, after: List<ChildTask>): List<Change> {
        val previous = before.associateBy { it.id }
        return after.mapNotNull { task ->
            val old = previous[task.id]
            when {
                old == null && task.isOpen -> Change(task, ChildTask.STATUS_OPEN)
                old != null && old.status != task.status && task.isConfirmed -> Change(task, ChildTask.STATUS_CONFIRMED)
                old != null && old.status != task.status && task.isRejected -> Change(task, ChildTask.STATUS_REJECTED)
                else -> null
            }
        }
    }
}
