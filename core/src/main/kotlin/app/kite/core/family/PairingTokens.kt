package app.kite.core.family

import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Generation of pairing secrets, all client-side with a CSPRNG.
 *
 * The QR token carries ≥128 bits of entropy and is single-use; only its SHA-256 hash is
 * ever stored server-side (see the redeem_pairing RPC), so a database leak reveals no live
 * token. The 6-digit code is the manual fallback and is rate-limited server-side.
 */
object PairingTokens {
    private val random = SecureRandom()
    private const val BASE_URL = "https://kite.app/j/"
    private val URL_SAFE = ('a'..'z') + ('A'..'Z') + ('0'..'9') + listOf('-', '_')

    /** 24 URL-safe chars ≈ 143 bits of entropy. */
    fun newToken(length: Int = 24): String {
        val sb = StringBuilder(length)
        repeat(length) { sb.append(URL_SAFE[random.nextInt(URL_SAFE.size)]) }
        return sb.toString()
    }

    fun deepLink(token: String): String = BASE_URL + token

    /** Extracts the token from a scanned deep link, or null when the shape is wrong. */
    fun tokenFromDeepLink(value: String): String? {
        val trimmed = value.trim()
        if (!trimmed.startsWith(BASE_URL)) return null
        return trimmed.removePrefix(BASE_URL).takeIf { it.isNotEmpty() }
    }

    /** 6-digit numeric code, zero-padded, uniformly random. */
    fun newCode(): String = random.nextInt(1_000_000).toString().padStart(6, '0')

    fun sha256Hex(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray())
        .joinToString("") { "%02x".format(it) }

    /** 32-byte shared secret for offline approval codes, exchanged when devices link. */
    fun newSharedSecret(): ByteArray = ByteArray(32).also { random.nextBytes(it) }
}
