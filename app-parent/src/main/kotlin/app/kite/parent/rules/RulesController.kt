package app.kite.parent.rules

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.kite.core.family.FamilyMember
import app.kite.core.rules.ChildRules
import app.kite.core.rules.RulesRemote
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * One child's rules document as edited from the parent's Главная and its sub-screens.
 * Loaded once per child; every edit applies immediately in the UI and is uploaded after a
 * short debounce (the whole jsonb document, like Kids360 — no «Сохранить» buttons). A failed
 * upload keeps the local draft and surfaces [error]; the next edit retries.
 */
class RulesController(private val member: FamilyMember, private val remote: RulesRemote, private val scope: CoroutineScope) {
    var rules by mutableStateOf<ChildRules?>(null)
        private set
    var loading by mutableStateOf(false)
        private set
    var saving by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    private var saveJob: Job? = null

    fun load() {
        if (loading) return
        scope.launch {
            loading = true
            error = null
            remote.fetch(member.id)
                .onSuccess { rules = it ?: ChildRules() }
                .onFailure { error = it.message ?: "Не удалось загрузить правила" }
            loading = false
        }
    }

    /** Applies [transform] to the current document and schedules the upload. */
    fun update(transform: (ChildRules) -> ChildRules) {
        val current = rules ?: ChildRules()
        val updated = transform(current).copy(updatedAtEpochSeconds = System.currentTimeMillis() / 1000)
        rules = updated
        saveJob?.cancel()
        saveJob =
            scope.launch {
                delay(SAVE_DEBOUNCE_MS)
                saving = true
                remote.upsert(member.id, member.familyId, updated)
                    .onSuccess { error = null }
                    .onFailure { error = it.message ?: "Не удалось сохранить" }
                saving = false
            }
    }

    private companion object {
        const val SAVE_DEBOUNCE_MS = 700L
    }
}
