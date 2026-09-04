package app.kite.child.enforce

import android.content.Context
import app.kite.core.approval.OfflineApprovalCode
import java.time.LocalDate
import java.time.ZoneId

class OfflineTimeGrant(context: Context, private val bonusStore: BonusStore) {
    private val prefs = context.getSharedPreferences("offline_time_grant", Context.MODE_PRIVATE)

    enum class Outcome { Granted, WrongCode, AlreadyUsed, NoSecret }

    fun redeem(secret: ByteArray?, code: String, now: Long = System.currentTimeMillis()): Outcome {
        if (secret == null) return Outcome.NoSecret
        if (!OfflineApprovalCode(secret).verify(code)) return Outcome.WrongCode
        if (prefs.getString(KEY_LAST_CODE, null) == code) return Outcome.AlreadyUsed
        val today = LocalDate.now(ZoneId.systemDefault()).toString()
        bonusStore.add(today, MINUTES)
        prefs.edit().putString(KEY_LAST_CODE, code).putLong(KEY_LAST_AT, now).apply()
        return Outcome.Granted
    }

    companion object {
        const val MINUTES = 15

        private const val KEY_LAST_CODE = "last_code"
        private const val KEY_LAST_AT = "last_at"
    }
}
