package app.kite.child.identity

import android.content.Context
import app.kite.child.KEY_PAIRED_FAMILY_ID
import app.kite.core.auth.AuthState
import app.kite.core.auth.SessionManager
import app.kite.core.family.FamilyRepository
import app.kite.core.secure.SecureStore

/**
 * Who this child device is: the paired family id (SecureStore) and this device's own
 * family_members row id, resolved from the server once and cached in plain prefs
 * (an id is not a secret). Shared by the usage syncer and rules syncer.
 */
class MemberIdentity(
    context: Context,
    private val secureStore: SecureStore,
    private val sessionManager: SessionManager,
    private val familyRepository: FamilyRepository,
) {
    private val prefs = context.getSharedPreferences("identity", Context.MODE_PRIVATE)

    fun familyId(): String? = secureStore.getString(KEY_PAIRED_FAMILY_ID)

    suspend fun memberId(): String? {
        prefs.getString(KEY_MEMBER_ID, null)?.let { return it }
        val familyId = familyId() ?: return null
        val userId = (sessionManager.authState.value as? AuthState.SignedIn)?.session?.userId ?: return null
        val member =
            familyRepository.members(familyId).getOrNull()
                ?.firstOrNull { it.userId == userId } ?: return null
        prefs.edit().putString(KEY_MEMBER_ID, member.id).apply()
        return member.id
    }

    private companion object {
        const val KEY_MEMBER_ID = "member_id"
    }
}
