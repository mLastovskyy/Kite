package app.kite.core.family

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Roles: owner and parent both read as «Родитель» in the UI; child runs Kite Jr. */
enum class MemberRole {
    @SerialName("owner")
    OWNER,

    @SerialName("parent")
    PARENT,

    @SerialName("child")
    CHILD,
}

@Serializable
data class FamilyMember(
    val id: String,
    @SerialName("family_id") val familyId: String,
    @SerialName("user_id") val userId: String,
    val role: MemberRole,
    @SerialName("display_name") val displayName: String = "",
    @SerialName("avatar_kind") val avatarKind: String = "kite",
    @SerialName("avatar_url") val avatarUrl: String? = null,
) {
    val isParent: Boolean get() = role == MemberRole.OWNER || role == MemberRole.PARENT
}

@Serializable
data class Family(val id: String, val name: String? = null, @SerialName("owner_user_id") val ownerUserId: String)

/** Two kinds of pairing invite (CLAUDE.md): a child pairs, a second parent is invited. */
enum class PairingKind(val serial: String) {
    PAIR_CHILD("pair_child"),
    INVITE_PARENT("invite_parent"),
}

/**
 * What the parent's screen shows after creating an invite. The QR encodes ONLY [deepLink]
 * (a one-time token, ≥128 bits) — never family_id, names or anything meaningful. The
 * [code] is the manual 6-digit fallback, read out or typed. Both expire at [expiresAt].
 */
data class PairingInvite(val deepLink: String, val code: String, val expiresAt: Long)
