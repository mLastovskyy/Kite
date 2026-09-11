package app.kite.child.tasks

import app.kite.core.tasks.ChildTask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskChangesTest {
    @Test
    fun `a task the child has not seen is announced as new`() {
        val before = listOf(task("a", ChildTask.STATUS_OPEN))
        val after = before + task("b", ChildTask.STATUS_OPEN)
        val changes = TaskChanges.diff(before, after)
        assertEquals(listOf("b" to ChildTask.STATUS_OPEN), changes.map { it.task.id to it.kind })
    }

    @Test
    fun `a task sent back is announced every time it comes back`() {
        val first = TaskChanges.diff(listOf(task("a", ChildTask.STATUS_DONE)), listOf(task("a", ChildTask.STATUS_REJECTED)))
        assertEquals(ChildTask.STATUS_REJECTED, first.single().kind)
        val redone = TaskChanges.diff(listOf(task("a", ChildTask.STATUS_REJECTED)), listOf(task("a", ChildTask.STATUS_DONE)))
        assertTrue(redone.isEmpty())
        val again = TaskChanges.diff(listOf(task("a", ChildTask.STATUS_DONE)), listOf(task("a", ChildTask.STATUS_REJECTED)))
        assertEquals(ChildTask.STATUS_REJECTED, again.single().kind)
    }

    @Test
    fun `confirmation is announced once and an unchanged task is silent`() {
        val confirmed = TaskChanges.diff(listOf(task("a", ChildTask.STATUS_DONE)), listOf(task("a", ChildTask.STATUS_CONFIRMED)))
        assertEquals(ChildTask.STATUS_CONFIRMED, confirmed.single().kind)
        assertTrue(TaskChanges.diff(listOf(task("a", ChildTask.STATUS_CONFIRMED)), listOf(task("a", ChildTask.STATUS_CONFIRMED))).isEmpty())
    }

    private fun task(id: String, status: String) = ChildTask(
        id = id,
        familyId = "f",
        childMemberId = "c",
        title = "Задание $id",
        rewardMinutes = 15,
        status = status,
    )
}
