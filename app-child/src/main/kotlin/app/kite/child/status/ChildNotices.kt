package app.kite.child.status

import android.annotation.SuppressLint
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.kite.core.notifications.Channels
import app.kite.core.rules.ChildRules

/**
 * What the parent just did, told to the child. Kite Jr is not allowed to be a black box
 * (CLAUDE.md: the child must be able to see what is monitored), so every parent action that
 * changes this phone — extra minutes, a lock lifted, a new rule, a confirmed task — says so
 * out loud, and names the parent when it is known.
 */
class ChildNotices(private val context: Context) {
    private val prefs = context.getSharedPreferences("child_notices", Context.MODE_PRIVATE)

    fun taskAdded(taskId: String, title: String, rewardMinutes: Int) =
        once("task_new_$taskId", "Новое задание", "$title · +$rewardMinutes мин")

    fun taskConfirmed(taskId: String, title: String, rewardMinutes: Int) =
        once("task_ok_$taskId", "Задание принято", "$title · +$rewardMinutes мин к лимиту")

    fun timeGranted(minutes: Int, by: String?) = post("Больше времени", withParent("Добавлено $minutes мин", by))

    fun unlocked(by: String?) = post("Телефон разблокирован", withParent("Можно пользоваться приложениями", by))

    fun locked(by: String?) = post("Телефон заблокирован", withParent("Звонки и сообщения работают", by))

    fun removalAllowed() = post("Удаление разрешено", "Kite Jr можно удалить в течение 10 минут")

    /** Names the change the way the child would describe it, not the way the code stores it. */
    fun rulesChanged(before: ChildRules, after: ChildRules, by: String?) {
        val text =
            when {
                after.quietHours.size > before.quietHours.size -> {
                    val added = after.quietHours.lastOrNull()?.name?.takeIf { it.isNotBlank() }
                    added?.let { "Новое расписание «$it»" } ?: "Новое расписание"
                }
                after.appRules.count { it.value.blocked } > before.appRules.count { it.value.blocked } ->
                    "Часть приложений теперь закрыта"
                after.appRules.count { it.value.dailyLimitMinutes != null } >
                    before.appRules.count { it.value.dailyLimitMinutes != null } -> "У приложений появился лимит"
                (1..7).any { after.limitFor(it) != before.limitFor(it) } -> "Изменился лимит на день"
                after != before -> "Правила обновились"
                else -> return
            }
        post(by?.takeIf { it.isNotBlank() }?.let { "$it обновил(а) правила" } ?: "Правила изменились", text)
    }

    private fun withParent(text: String, by: String?): String = if (by.isNullOrBlank()) text else "$text · $by"

    private fun once(key: String, title: String, text: String) {
        if (prefs.getBoolean(key, false)) return
        prefs.edit().putBoolean(key, true).apply()
        post(title, text, key.hashCode())
    }

    @SuppressLint("MissingPermission")
    private fun post(title: String, text: String, id: Int = title.hashCode()) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        val notification =
            NotificationCompat.Builder(context, Channels.ALERTS)
                .setSmallIcon(app.kite.core.R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(text)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .build()
        manager.notify(id, notification)
    }
}
