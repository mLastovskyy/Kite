package app.kite.child.status

import android.annotation.SuppressLint
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.kite.child.nav.ChildScreens
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
        once("task_new_$taskId", "Новое задание", "$title · +$rewardMinutes мин", ChildScreens.TASKS)

    fun taskConfirmed(taskId: String, title: String, rewardMinutes: Int) =
        once("task_ok_$taskId", "Задание принято", "$title · +$rewardMinutes мин к лимиту", ChildScreens.TASKS)

    /** Not [once]: the same task can be sent back more than once, and each time is news. */
    fun taskRejected(taskId: String, title: String) =
        post("Задание не принято", "$title · можно сделать снова", "task_no_$taskId".hashCode(), ChildScreens.TASKS)

    fun timeGranted(minutes: Int, by: String?) = post("Больше времени", withParent("Добавлено $minutes мин", by))

    fun unlocked(by: String?) = post("Телефон разблокирован", withParent("Можно пользоваться приложениями", by))

    fun locked(by: String?) = post("Телефон заблокирован", withParent("Звонки и сообщения работают", by))

    fun removalAllowed() = post("Удаление разрешено", "Kite Jr можно удалить в течение 10 минут")

    fun released(by: String?) = post("Ограничения сняты", withParent("Лимиты и расписания больше не действуют", by))

    fun protectionRestored(by: String?) = post("Ограничения снова работают", withParent("Лимиты и расписания вернулись", by))

    /**
     * Names the change the way the child would describe it, not the way the code stores it.
     *
     * Deliberately hard to trigger (owner, 06.09.2026: «не на каждый чих»). The parent app
     * saves the whole document after every touch of a switch, so a single evening of tuning
     * is a dozen writes; only a change the child can actually SEE in «Настроенные правила»
     * says anything, and the same sentence is not repeated within [REPEAT_QUIET_MS].
     */
    fun rulesChanged(before: ChildRules, after: ChildRules, by: String?) {
        if (visibleShape(before) == visibleShape(after)) return
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
                else -> "Правила обновились"
            }
        val title = by?.takeIf { it.isNotBlank() }?.let { "$it обновил(а) правила" } ?: "Правила изменились"
        val now = System.currentTimeMillis()
        val repeat = prefs.getString(KEY_RULES_TEXT, null) == "$title|$text" &&
            now - prefs.getLong(KEY_RULES_AT, 0) < REPEAT_QUIET_MS
        if (repeat) return
        prefs.edit().putString(KEY_RULES_TEXT, "$title|$text").putLong(KEY_RULES_AT, now).apply()
        post(title, text, screen = ChildScreens.RULES)
    }

    /**
     * Everything «Настроенные правила» puts on screen, and nothing else: two documents with
     * the same shape look identical to the child, so there is nothing to announce.
     */
    private fun visibleShape(rules: ChildRules): String = buildString {
        (1..7).forEach { day -> append(rules.limitFor(day)).append(',') }
        append('|')
        rules.quietHours.filter { it.enabled }.sortedBy { it.name + it.startMinutes }.forEach { interval ->
            append(interval.name).append(interval.startMinutes).append('-').append(interval.endMinutes)
                .append(interval.days.sorted()).append(interval.packages.sorted()).append(';')
        }
        append('|')
        rules.appRules.entries.sortedBy { it.key }.forEach { (packageName, rule) ->
            append(packageName).append(rule.blocked).append(rule.dailyLimitMinutes).append(rule.alwaysAllowed).append(';')
        }
    }

    private fun withParent(text: String, by: String?): String = if (by.isNullOrBlank()) text else "$text · $by"

    private fun once(key: String, title: String, text: String, screen: String = ChildScreens.STATUS) {
        if (prefs.getBoolean(key, false)) return
        prefs.edit().putBoolean(key, true).apply()
        post(title, text, key.hashCode(), screen)
    }

    @SuppressLint("MissingPermission")
    private fun post(title: String, text: String, id: Int = title.hashCode(), screen: String = ChildScreens.STATUS) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        val notification =
            NotificationCompat.Builder(context, Channels.ALERTS)
                .setSmallIcon(app.kite.core.R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(text)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setContentIntent(ChildScreens.tap(context, screen))
                .build()
        manager.notify(id, notification)
    }

    private companion object {
        const val KEY_RULES_TEXT = "rules_last_text"
        const val KEY_RULES_AT = "rules_last_at"

        /** The same sentence twice in half an hour is noise, not news. */
        const val REPEAT_QUIET_MS = 30 * 60 * 1000L
    }
}
