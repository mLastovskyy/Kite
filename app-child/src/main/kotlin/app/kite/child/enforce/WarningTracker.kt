package app.kite.child.enforce

import android.annotation.SuppressLint
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.kite.child.notifications.Channels

/**
 * The two limit warnings — 15 minutes and 1 minute left, nothing else (CLAUDE.md).
 * Each (day, scope, threshold) fires exactly once; state lives in prefs so a service
 * restart does not re-notify.
 */
class WarningTracker(private val context: Context) {
    private val prefs = context.getSharedPreferences("limit_warnings", Context.MODE_PRIVATE)

    /** [scope] is "day" for the daily limit or the package name for a per-app limit. */
    @SuppressLint("MissingPermission") // guarded by areNotificationsEnabled below
    fun maybeWarn(day: String, scope: String, threshold: Int, appLabel: String?) {
        val key = "$day/$scope/$threshold"
        if (prefs.getBoolean(key, false)) return
        prefs.edit().putBoolean(key, true).apply()

        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        val title = if (threshold == 1) "Осталась 1 минута" else "Осталось $threshold минут"
        val text = appLabel?.let { "Лимит приложения «$it» почти исчерпан" } ?: "Дневной лимит экрана почти исчерпан"
        val notification =
            NotificationCompat.Builder(context, Channels.ALERTS)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(title)
                .setContentText(text)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()
        manager.notify(key.hashCode(), notification)
    }
}
