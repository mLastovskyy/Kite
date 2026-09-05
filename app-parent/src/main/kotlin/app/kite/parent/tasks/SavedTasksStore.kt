package app.kite.parent.tasks

import android.content.Context

/** A task the parent keeps around to hand out again in one tap. */
data class SavedTask(val title: String, val rewardMinutes: Int)

/**
 * «Быстрые задания»: the parent's own shortlist, not a catalogue we invented. A task lands
 * here only when the parent asks for it while creating one, and lives on this phone — it is a
 * convenience, not family data, so it is kept as plain prefs rather than a synced table.
 */
class SavedTasksStore(context: Context) {
    private val prefs = context.getSharedPreferences("saved_tasks", Context.MODE_PRIVATE)

    fun all(): List<SavedTask> = prefs.getStringSet(KEY, emptySet()).orEmpty()
        .mapNotNull { entry ->
            val parts = entry.split(SEPARATOR)
            val title = parts.getOrNull(1)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            SavedTask(title, parts.getOrNull(2)?.toIntOrNull() ?: DEFAULT_REWARD)
        }
        .sortedBy { it.title.lowercase() }

    fun add(title: String, rewardMinutes: Int) {
        val clean = title.trim()
        if (clean.isEmpty()) return
        val kept = all().filterNot { it.title.equals(clean, ignoreCase = true) }.takeLast(MAX - 1)
        save(kept + SavedTask(clean, rewardMinutes))
    }

    fun remove(title: String) {
        save(all().filterNot { it.title.equals(title, ignoreCase = true) })
    }

    private fun save(tasks: List<SavedTask>) {
        val encoded = tasks.mapIndexed { index, task -> "$index$SEPARATOR${task.title}$SEPARATOR${task.rewardMinutes}" }.toSet()
        prefs.edit().putStringSet(KEY, encoded).apply()
    }

    private companion object {
        const val KEY = "saved_tasks"
        const val SEPARATOR = ""
        const val DEFAULT_REWARD = 15
        const val MAX = 12
    }
}
