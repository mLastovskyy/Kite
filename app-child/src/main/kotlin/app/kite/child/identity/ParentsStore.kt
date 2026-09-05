package app.kite.child.identity

import android.content.Context
import app.kite.core.family.FamilyMember
import app.kite.core.family.FamilyRepository
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class ChildParent(val memberId: String, val name: String, val avatarKind: String, val avatarUrl: String?, val userId: String? = null)

class ParentsStore(
    context: Context,
    private val identity: MemberIdentity,
    private val familyRepository: FamilyRepository,
    private val json: Json,
) {
    private val prefs = context.getSharedPreferences("parents", Context.MODE_PRIVATE)

    fun parents(): List<ChildParent> = prefs.getString(KEY_PARENTS, null)
        ?.let { raw -> runCatching { json.decodeFromString<List<ChildParent>>(raw) }.getOrNull() }
        .orEmpty()

    fun preferredId(): String? = prefs.getString(KEY_PREFERRED, null)?.takeIf { id -> parents().any { it.memberId == id } }

    fun nameForUser(userId: String?): String? =
        userId?.let { id -> parents().firstOrNull { it.userId == id }?.name?.takeIf(String::isNotBlank) }

    fun preferred(): ChildParent? = preferredId()?.let { id -> parents().firstOrNull { it.memberId == id } }

    fun choose(memberId: String?) {
        prefs.edit().apply { if (memberId == null) remove(KEY_PREFERRED) else putString(KEY_PREFERRED, memberId) }.apply()
    }

    suspend fun refresh() {
        val familyId = identity.familyId() ?: return
        val members = familyRepository.members(familyId).getOrNull() ?: return
        val parents = members.filter(FamilyMember::isParent).map { it.toChildParent() }
        prefs.edit().putString(KEY_PARENTS, json.encodeToString(parents)).apply()
    }

    private fun FamilyMember.toChildParent() = ChildParent(
        memberId = id,
        name = displayName.ifBlank { "Родитель" },
        avatarKind = avatarKind,
        avatarUrl = avatarUrl,
        userId = userId,
    )

    private companion object {
        const val KEY_PARENTS = "parents_json"
        const val KEY_PREFERRED = "preferred_parent"
    }
}
