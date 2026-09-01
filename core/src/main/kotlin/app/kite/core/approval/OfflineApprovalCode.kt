package app.kite.core.approval

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.experimental.and

/**
 * TOTP-style offline approval codes (CLAUDE.md, "Offline approval codes — required").
 *
 * A shared secret is generated when the parent and child devices are linked and stored in
 * EncryptedSharedPreferences on both. The parent app shows a rotating 6-digit code computed
 * locally with no network; the child enters it and it is verified locally. The window is
 * deliberately wide (default step 180 s, ±1 step) because the parent reads the code out
 * over a phone call.
 *
 * Pure JDK crypto (HMAC-SHA1, RFC 4226 dynamic truncation) — no dependency, no network.
 * The [secret] is raw key bytes; where they are stored is the caller's concern (M3 linking).
 */
class OfflineApprovalCode(
    private val secret: ByteArray,
    private val stepSeconds: Long = DEFAULT_STEP_SECONDS,
    private val digits: Int = DEFAULT_DIGITS,
) {
    init {
        require(secret.isNotEmpty()) { "secret must not be empty" }
        require(digits in 6..9) { "digits must be 6..9" }
        require(stepSeconds > 0) { "stepSeconds must be positive" }
    }

    /** Current code for [epochMillis] (defaults to now). Zero-padded to [digits]. */
    fun generate(epochMillis: Long = System.currentTimeMillis()): String = codeForCounter(counter(epochMillis))

    /**
     * True when [entered] matches the code for [epochMillis] within ±[toleranceSteps] steps.
     * Constant-time digit comparison to avoid leaking how close a guess was.
     */
    fun verify(entered: String, epochMillis: Long = System.currentTimeMillis(), toleranceSteps: Int = DEFAULT_TOLERANCE_STEPS): Boolean {
        val normalized = entered.trim()
        if (normalized.length != digits) return false
        val current = counter(epochMillis)
        for (offset in -toleranceSteps..toleranceSteps) {
            if (constantTimeEquals(normalized, codeForCounter(current + offset))) return true
        }
        return false
    }

    private fun counter(epochMillis: Long): Long = Math.floorDiv(epochMillis / 1000L, stepSeconds)

    private fun codeForCounter(counter: Long): String {
        val message = ByteArray(8)
        var value = counter
        for (i in 7 downTo 0) {
            message[i] = (value and 0xFF).toByte()
            value = value shr 8
        }
        val hash =
            Mac.getInstance("HmacSHA1").run {
                init(SecretKeySpec(secret, "HmacSHA1"))
                doFinal(message)
            }
        // RFC 4226 dynamic truncation.
        val offset = (hash[hash.size - 1] and 0x0F).toInt()
        val binary =
            ((hash[offset].toInt() and 0x7F) shl 24) or
                ((hash[offset + 1].toInt() and 0xFF) shl 16) or
                ((hash[offset + 2].toInt() and 0xFF) shl 8) or
                (hash[offset + 3].toInt() and 0xFF)
        val modulo = binary % POW10[digits]
        return modulo.toString().padStart(digits, '0')
    }

    private fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var diff = 0
        for (i in a.indices) diff = diff or (a[i].code xor b[i].code)
        return diff == 0
    }

    companion object {
        const val DEFAULT_STEP_SECONDS = 180L
        const val DEFAULT_DIGITS = 6
        const val DEFAULT_TOLERANCE_STEPS = 1

        private val POW10 = intArrayOf(1, 10, 100, 1_000, 10_000, 100_000, 1_000_000, 10_000_000, 100_000_000, 1_000_000_000)
    }
}
