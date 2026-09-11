package app.kite.child.tasks

import android.content.Context
import app.kite.child.identity.MemberIdentity
import app.kite.child.status.ChildNotices
import app.kite.core.tasks.ChildTask
import app.kite.core.tasks.TaskPhotosRemote
import app.kite.core.tasks.TasksRemote
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

/**
 * Local copy of this child's tasks («Задания»). The block screen must be able to list them
 * with no network — an exhausted limit has to stay an invitation to earn time, offline too
 * (CLAUDE.md) — so the cache is the only thing the UI reads, and «Выполнил» is queued when
 * the request cannot go out right now.
 */
class TasksStore(private val context: Context, private val json: Json) {
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
    fun visible(): List<ChildTask> {
        val today = LocalDate.now(ZoneId.systemDefault()).dayOfWeek.value
        return tasks()
            .filterNot { it.isConfirmed }
            // A rejected task waits for the child whatever weekday it was set for: it is
            // already started business, not a new chore for its own day.
            .filter { !it.canDo || it.isRejected || it.isForToday(today) }
            .sortedBy { if (it.canDo) 0 else 1 }
    }

    /**
     * Something happened to the tasks the child has not looked at yet — the tab bar puts a dot
     * on «Задания» so a rejection is not discovered by accident (owner, 07.09.2026).
     */
    fun hasUnseen(): Boolean = prefs.getBoolean(KEY_UNSEEN, false)

    fun markUnseen() {
        prefs.edit().putBoolean(KEY_UNSEEN, true).apply()
    }

    fun markSeen() {
        prefs.edit().putBoolean(KEY_UNSEEN, false).apply()
    }

    fun lastAskedAt(): Long = prefs.getLong(KEY_ASKED_AT, 0L)

    fun markAsked(now: Long = System.currentTimeMillis()) {
        prefs.edit().putLong(KEY_ASKED_AT, now).apply()
    }

    /** Ids marked done locally whose PATCH has not gone through yet. */
    fun pendingDone(): Set<String> = prefs.getStringSet(KEY_PENDING, emptySet())?.toSet() ?: emptySet()

    /**
     * Optimistic «Выполнил»: the row flips to «ждёт родителя» immediately and the id is
     * queued for the next successful sync. [photoPath] is the file the child attached, kept on
     * the phone until it has been uploaded — the proof has to survive a night with no network.
     */
    fun markDoneLocally(taskId: String, photoPath: String? = null) {
        save(tasks().map { if (it.id == taskId) it.copy(status = ChildTask.STATUS_DONE) else it })
        prefs.edit().putStringSet(KEY_PENDING, pendingDone() + taskId).apply()
        if (photoPath != null) putPhoto(taskId, photoPath)
    }

    fun clearPending(ids: Set<String>) {
        if (ids.isEmpty()) return
        prefs.edit().putStringSet(KEY_PENDING, pendingDone() - ids).apply()
        ids.forEach(::clearPhoto)
    }

    /**
     * The attached photo of a queued «Выполнил»: a local file path while it is still waiting,
     * an `https` URL once Storage has it (so a retry of the PATCH does not upload it twice).
     */
    fun pendingPhoto(taskId: String): String? = photos()[taskId]

    fun putPhoto(taskId: String, pathOrUrl: String) {
        savePhotos(photos() + (taskId to pathOrUrl))
    }

    /** Forgets the photo and deletes the file it was holding on the phone. */
    fun clearPhoto(taskId: String) {
        val held = photos()[taskId] ?: return
        if (!held.startsWith("http")) runCatching { File(held).delete() }
        savePhotos(photos() - taskId)
    }

    /** Where a photo waits for its upload. Files, not cache: the queue can be days old. */
    fun photoFile(taskId: String): File = File(dir, "$taskId.jpg")

    private val dir: File get() = File(context.filesDir, "task_photos").apply { mkdirs() }

    private fun photos(): Map<String, String> = prefs.getString(KEY_PHOTOS, null)
        ?.let { raw -> runCatching { json.decodeFromString(PHOTOS, raw) }.getOrNull() }
        ?: emptyMap()

    private fun savePhotos(value: Map<String, String>) {
        prefs.edit().putString(KEY_PHOTOS, json.encodeToString(PHOTOS, value)).apply()
    }

    private companion object {
        const val KEY_UNSEEN = "tasks_unseen"
        const val KEY_ASKED_AT = "asked_at"
        const val KEY_TASKS = "tasks_json"
        const val KEY_PENDING = "pending_done"
        const val KEY_PHOTOS = "pending_photos"
        val PHOTOS = MapSerializer(String.serializer(), String.serializer())
    }
}

/**
 * Flushes queued «Выполнил» marks, then replaces the cache with what the server has.
 * A task the child marked done while offline keeps its local `done` status until the flush
 * succeeds, so the block screen never re-offers a task twice.
 */
class TasksSyncer(
    private val identity: MemberIdentity,
    private val remote: TasksRemote,
    private val store: TasksStore,
    private val photos: TaskPhotosRemote,
) {
    suspend fun refresh(notices: ChildNotices? = null): List<ChildTask> {
        val memberId = identity.memberId() ?: return store.visible()
        val flushed = mutableSetOf<String>()
        store.pendingDone().forEach { id ->
            if (flush(memberId, id)) flushed += id
        }
        store.clearPending(flushed)
        val stillPending = store.pendingDone()
        remote.activeFor(memberId).getOrNull()?.let { fetched ->
            announce(store.tasks(), fetched, notices)
            store.save(fetched.map { if (it.id in stillPending) it.copy(status = ChildTask.STATUS_DONE) else it })
            // The parent deleted a task the child had already marked done: nothing on the
            // server will ever confirm it, so the queued id goes too instead of keeping a
            // «ждём подтверждения» row for a task that no longer exists.
            store.clearPending(stillPending - fetched.map { it.id }.toSet())
        }
        return store.visible()
    }

    private fun announce(before: List<ChildTask>, after: List<ChildTask>, notices: ChildNotices?) {
        if (notices == null || before.isEmpty()) return
        val changes = TaskChanges.diff(before, after)
        changes.forEach { change ->
            val task = change.task
            when (change.kind) {
                ChildTask.STATUS_OPEN -> notices.taskAdded(task.id, task.title, task.rewardMinutes)
                ChildTask.STATUS_CONFIRMED -> notices.taskConfirmed(task.id, task.title, task.rewardMinutes)
                ChildTask.STATUS_REJECTED -> notices.taskRejected(task.id, task.title)
            }
        }
        if (changes.isNotEmpty()) store.markUnseen()
    }

    /**
     * «Выполнил» from the UI or the block screen: local first, then best-effort network.
     * [photoPath] is an optional JPEG the child attached; it goes up before the mark does.
     */
    suspend fun markDone(taskId: String, photoPath: String? = null) {
        store.markDoneLocally(taskId, photoPath)
        val memberId = identity.memberId() ?: return
        if (flush(memberId, taskId)) store.clearPending(setOf(taskId))
    }

    /**
     * Sends one queued mark. The photo goes first and its URL is remembered, so a PATCH that
     * fails afterwards does not upload the same picture again. A mark whose photo cannot be
     * uploaded stays queued: the parent must not be asked to confirm a task whose proof is
     * still sitting on the child's phone. A photo whose file has gone (cleaner, factory reset)
     * is not worth blocking the task forever — the mark then goes without it.
     */
    private suspend fun flush(memberId: String, taskId: String): Boolean {
        val held = store.pendingPhoto(taskId)
        val url =
            when {
                held == null -> null
                held.startsWith("http") -> held
                !File(held).exists() -> null
                else ->
                    photos.upload(memberId, taskId, File(held).readBytes())
                        .onSuccess { store.putPhoto(taskId, it) }
                        .getOrElse { return false }
            }
        return remote.markDone(taskId, url).isSuccess
    }
}
