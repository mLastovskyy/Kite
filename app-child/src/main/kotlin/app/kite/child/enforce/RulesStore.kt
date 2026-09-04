package app.kite.child.enforce

import android.content.Context
import app.kite.child.identity.MemberIdentity
import app.kite.core.rules.ChildRules
import app.kite.core.rules.RulesRemote
import kotlinx.serialization.json.Json

/**
 * Local cache of this device's [ChildRules]. Enforcement reads ONLY this cache, so every
 * block decision works offline; [RulesSyncer.refresh] replaces it when the network allows.
 * Rules are not secrets — plain prefs, and the child can inspect them (transparency).
 */
class RulesStore(context: Context, private val json: Json) {
    private val prefs = context.getSharedPreferences("rules", Context.MODE_PRIVATE)

    fun rules(): ChildRules = prefs.getString(KEY_RULES, null)
        ?.let { raw -> runCatching { json.decodeFromString<ChildRules>(raw) }.getOrNull() }
        ?: ChildRules()

    fun summary(): String {
        val rules = rules()
        val blocked = rules.appRules.count { it.value.blocked }
        val limited = rules.appRules.count { it.value.dailyLimitMinutes != null }
        val schedules = rules.quietHours.count { it.enabled }
        val dayLimits = (1..7).mapNotNull { rules.limitFor(it) }
        val parts =
            buildList {
                if (dayLimits.isNotEmpty()) add("лимит на день есть") else add("лимита на день нет")
                if (blocked > 0) add("запрещено $blocked")
                if (limited > 0) add("с лимитом $limited")
                if (schedules > 0) add("расписаний $schedules")
            }
        val empty = dayLimits.isEmpty() && blocked == 0 && limited == 0 && schedules == 0
        return if (empty) "Пока пусто — родитель ещё не задал ограничения" else parts.joinToString(", ")
    }

    fun save(rules: ChildRules) {
        prefs.edit().putString(KEY_RULES, json.encodeToString(ChildRules.serializer(), rules)).apply()
    }

    private companion object {
        const val KEY_RULES = "rules_json"
    }
}

/** Pulls the freshest rules for this member; failures keep the cached copy. */
class RulesSyncer(private val identity: MemberIdentity, private val remote: RulesRemote, private val store: RulesStore) {
    suspend fun refresh() {
        val memberId = identity.memberId() ?: return
        remote.fetch(memberId).getOrNull()?.let(store::save)
    }
}
