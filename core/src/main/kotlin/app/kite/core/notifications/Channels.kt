package app.kite.core.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat

/**
 * Notification channels with SEPARATE importances so one mute does not silence everything
 * (CLAUDE.md): requests HIGH · alerts HIGH · status DEFAULT · reports LOW · service MIN.
 * Shared by both apps — the parent needs `requests`/`alerts` to arrive promptly, the child
 * uses `alerts` for limit warnings and `service` for the foreground service.
 */
object Channels {
    const val REQUESTS = "requests"
    const val ALERTS = "alerts"
    const val STATUS = "status"
    const val REPORTS = "reports"
    const val SERVICE = "service"

    fun create(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(REQUESTS, "Запросы", NotificationManager.IMPORTANCE_HIGH),
                NotificationChannel(ALERTS, "Предупреждения", NotificationManager.IMPORTANCE_HIGH),
                NotificationChannel(STATUS, "Статус защиты", NotificationManager.IMPORTANCE_DEFAULT),
                NotificationChannel(REPORTS, "Отчёты", NotificationManager.IMPORTANCE_LOW),
                NotificationChannel(SERVICE, "Служебные", NotificationManager.IMPORTANCE_MIN),
            ),
        )
    }

    /**
     * A calm, tidy notification (iOS-like): app icon, one-line title, one-line body,
     * tap-to-dismiss, no custom sound or lights — the channel importance alone decides how
     * prominent it is. Long bodies expand to BigText rather than being truncated abruptly.
     */
    fun build(context: Context, channel: String, title: String, body: String): Notification = NotificationCompat.Builder(context, channel)
        .setSmallIcon(app.kite.core.R.drawable.ic_notification)
        .setContentTitle(title)
        .setContentText(body)
        .setStyle(NotificationCompat.BigTextStyle().bigText(body))
        .setAutoCancel(true)
        .setCategory(NotificationCompat.CATEGORY_MESSAGE)
        .setOnlyAlertOnce(true)
        .build()
}
