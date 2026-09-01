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
