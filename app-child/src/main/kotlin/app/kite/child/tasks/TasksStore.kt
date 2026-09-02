package app.kite.child.tasks

import android.content.Context
import app.kite.child.identity.MemberIdentity
import app.kite.core.tasks.ChildTask
import app.kite.core.tasks.TasksRemote
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Local copy of this child's tasks («Задания»). The block screen must be able to list them
 * with no network — an exhausted limit has to stay an invitation to earn time, offline too
 * (CLAUDE.md) — so the cache is the only thing the UI reads, and «Выполнил» is queued when
 * the request cannot go out right now.
 */
class TasksStore(context: Context, private val json: Json) {
    private val prefs = context.getSharedPreferences("tasks", Context.MODE_PRIVATE)

    fun tasks(): List<ChildTask> = prefs.getString(KEY_TASKS, null)
        ?.let { raw -> runCatching { json.decodeFromString(ListSerializer(ChildTask.serializer()), raw) }.getOrNull() }
        ?: emptyList()

    fun save(tasks: List<ChildTask>) {
        prefs.edit()
            .putString(KEY_TASKS, json.encodeToString(ListSerializer(ChildTask.serializer()), tasks))
            .apply()
    }

    /** Tasks still worth showing: open ones first, then those awaiting the parent. */
    fun visible(): List<ChildTask> = tasks().sortedBy { if (it.isOpen) 0 else 1 }

    /** Ids marked done locally whose PATCH has not gone through yet. */
    fun pendingDone(): Set<String> = prefs.getStringSet(KEY_PENDING, emptySet())?.toSet() ?: emptySet()

    /**
     * Optimistic «Выполнил»: the row flips to «ждёт родителя» immediately and the id is
     * queued for the next successful sync.
     */
    fun markDoneLocally(taskId: String) {
        save(tasks().map { if (it.id == taskId) it.copy(status = ChildTask.STATUS_DONE) else it })
        prefs.edit().putStringSet(KEY_PENDING, pendingDone() + taskId).apply()
    }

    fun clearPending(ids: Set<String>) {
        if (ids.isEmpty()) return
        prefs.edit().putStringSet(KEY_PENDING, pendingDone() - ids).apply()
    }

    private companion object {
        const val KEY_TASKS = "tasks_json"
        const val KEY_PENDING = "pending_done"
    }
}

/**
 * Flushes queued «Выполнил» marks, then replaces the cache with what the server has.
 * A task the child marked done while offline keeps its local `done` status until the flush
 * succeeds, so the block screen never re-offers a task twice.
 */
class TasksSyncer(private val identity: MemberIdentity, private val remote: TasksRemote, private val store: TasksStore) {
    suspend fun refresh(): List<ChildTask> {
        val memberId = identity.memberId() ?: return store.visible()
        val flushed = mutableSetOf<String>()
        store.pendingDone().forEach { id ->
            if (remote.markDone(id).isSuccess) flushed += id
        }
        store.clearPending(flushed)
        val stillPending = store.pendingDone()
        remote.activeFor(memberId).getOrNull()?.let { fetched ->
            store.save(fetched.map { if (it.id in stillPending) it.copy(status = ChildTask.STATUS_DONE) else it })
        }
        return store.visible()
    }

    /** «Выполнил» from the UI or the block screen: local first, then best-effort network. */
    suspend fun markDone(taskId: String) {
        store.markDoneLocally(taskId)
        if (remote.markDone(taskId).isSuccess) store.clearPending(setOf(taskId))
    }
}
