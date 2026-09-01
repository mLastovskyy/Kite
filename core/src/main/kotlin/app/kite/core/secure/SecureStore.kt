package app.kite.core.secure

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * EncryptedSharedPreferences wrapper for secrets: the Supabase session tokens and the TOTP
 * shared secret (CLAUDE.md — secrets never go in plain prefs). AES-256 GCM, key in the
 * Android Keystore.
 */
class SecureStore(context: Context) {
    private val prefs: SharedPreferences by lazy {
        val appContext = context.applicationContext
        val masterKey =
            MasterKey.Builder(appContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
        EncryptedSharedPreferences.create(
            appContext,
            FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun getString(key: String): String? = prefs.getString(key, null)

    fun putString(key: String, value: String?) {
        prefs.edit().apply {
            if (value == null) remove(key) else putString(key, value)
        }.apply()
    }

    fun remove(key: String) = prefs.edit().remove(key).apply()

    fun clear() = prefs.edit().clear().apply()

    companion object {
        private const val FILE_NAME = "kite_secure"
    }
}
