package app.kite.parent.auth

import app.kite.core.secure.SecureStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Optional 6-digit app code for the parent app. The Supabase session lives on for as long as
 * the app is installed, so the parent never re-types email + password; the code is what
 * stands between a child holding the parent's phone and the rules screen.
 *
 * Lock policy: locked on every cold start, and again after [RELOCK_AFTER_MS] in the
 * background (a quick app switch does not re-prompt). «Забыли код» signs out — the code is a
 * convenience layer over the account, not a second credential the server knows about.
 *
 * The code is stored as PBKDF2-HMAC-SHA256(salt) in [SecureStore] (EncryptedSharedPreferences),
 * never in plain text; verification is constant-time.
 */
class PinLock(private val secureStore: SecureStore, private val now: () -> Long = System::currentTimeMillis) {
    private val _locked = MutableStateFlow(isSet())
    val locked: StateFlow<Boolean> = _locked.asStateFlow()

    /** True right after a fresh sign-in (or from settings) — the UI shows the setup screen. */
    private val _setupRequested = MutableStateFlow(false)
    val setupRequested: StateFlow<Boolean> = _setupRequested.asStateFlow()

    private val _failures = MutableStateFlow(0)
    val failures: StateFlow<Int> = _failures.asStateFlow()

    private var backgroundedAt: Long? = null

    fun isSet(): Boolean = secureStore.getString(KEY_PIN) != null

    fun save(pin: String) {
        require(isValid(pin)) { "pin must be $LENGTH digits" }
        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        val hash = derive(pin, salt)
        secureStore.putString(KEY_PIN, "${encode(salt)}:${encode(hash)}")
        _failures.value = 0
        _locked.value = false
        _setupRequested.value = false
    }

    /** Removes the code entirely; also called on sign-out so a new account starts clean. */
    fun clear() {
        secureStore.remove(KEY_PIN)
        _failures.value = 0
        _locked.value = false
        _setupRequested.value = false
    }

    fun requestSetup() {
        _setupRequested.value = true
    }

    fun dismissSetup() {
        _setupRequested.value = false
    }

    /** Verifies [pin]; unlocks on success, counts a failure otherwise. */
    fun unlock(pin: String): Boolean {
        val ok = verify(pin)
        if (ok) _locked.value = false
        return ok
    }

    /**
     * Checks [pin] against the stored code WITHOUT touching the lock state — used before a
     * change («сначала введите старый код», owner 04.09.2026). Wrong attempts still count
     * towards [MAX_FAILURES], so a child with the unlocked phone cannot brute-force a new code.
     * No code set → true.
     */
    fun verify(pin: String): Boolean {
        val stored = secureStore.getString(KEY_PIN) ?: return true
        val parts = stored.split(':')
        val ok = parts.size == 2 && MessageDigest.isEqual(derive(pin, decode(parts[0])), decode(parts[1]))
        if (ok) _failures.value = 0 else _failures.value += 1
        return ok
    }

    fun onBackground() {
        backgroundedAt = now()
    }

    fun onForeground() {
        val since = backgroundedAt ?: return
        backgroundedAt = null
        if (isSet() && now() - since >= RELOCK_AFTER_MS) _locked.value = true
    }

    private fun derive(pin: String, salt: ByteArray): ByteArray = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        .generateSecret(PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, KEY_BITS))
        .encoded

    private fun encode(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

    private fun decode(text: String): ByteArray = runCatching { Base64.getDecoder().decode(text) }.getOrDefault(ByteArray(0))

    companion object {
        const val LENGTH = 6
        const val MAX_FAILURES = 5

        fun isValid(pin: String): Boolean = pin.length == LENGTH && pin.all(Char::isDigit)

        private const val KEY_PIN = "parent_pin_v1"
        private const val SALT_BYTES = 16
        private const val ITERATIONS = 20_000
        private const val KEY_BITS = 256
        private const val RELOCK_AFTER_MS = 5 * 60_000L
    }
}
