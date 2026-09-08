package app.kite.child.enforce

import android.content.Context
import app.kite.core.approval.OfflineApprovalCode
import java.time.LocalDate
import java.time.ZoneId

class OfflineTimeGrant(context: Context, private val bonusStore: BonusStore) {
    private val prefs = context.getSharedPreferences("offline_time_grant", Context.MODE_PRIVATE)

    enum class Outcome { Granted, WrongCode, AlreadyUsed, NoSecret }

    /**
     * [packageName] is the app the child was looking at when the block screen appeared. The
     * minutes are granted to the day AND to that app: a code read out over the phone means «let
     * them in», and the parent has no way to know which of the two limits was the one in the way
     * (owner, 08.09.2026 — entered the code and an app with its own limit stayed shut).
     */
    fun redeem(secret: ByteArray?, code: String, packageName: String? = null, now: Long = System.currentTimeMillis()): Outcome {
        if (secret == null) return Outcome.NoSecret
        if (!OfflineApprovalCode(secret).verify(code)) return Outcome.WrongCode
        if (prefs.getString(KEY_LAST_CODE, null) == code) return Outcome.AlreadyUsed
        val today = LocalDate.now(ZoneId.systemDefault()).toString()
        bonusStore.add(today, MINUTES)
        packageName?.takeIf { it.isNotBlank() }?.let { bonusStore.addApp(today, it, MINUTES) }
        prefs.edit().putString(KEY_LAST_CODE, code).putLong(KEY_LAST_AT, now).apply()
        return Outcome.Granted
    }

    companion object {
        const val MINUTES = 15

        private const val KEY_LAST_CODE = "last_code"
        private const val KEY_LAST_AT = "last_at"
    }
}
