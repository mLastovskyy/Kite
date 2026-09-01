package app.kite.core.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

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
}
