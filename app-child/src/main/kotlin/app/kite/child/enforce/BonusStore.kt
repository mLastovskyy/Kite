package app.kite.child.enforce

import android.content.Context

/**
 * Per-day bonus screen-time minutes granted by the parent (approved "extra time" requests,
 * delivered as a grant_time command). Added on top of the daily limit for that day only.
 */
class BonusStore(context: Context) {
    private val prefs = context.getSharedPreferences("time_bonus", Context.MODE_PRIVATE)

    fun minutesFor(day: String): Int = prefs.getInt(day, 0)

    fun add(day: String, minutes: Int) {
        if (minutes <= 0) return
        prefs.edit().putInt(day, minutesFor(day) + minutes).apply()
    }
}
