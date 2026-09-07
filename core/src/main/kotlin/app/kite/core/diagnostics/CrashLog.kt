package app.kite.core.diagnostics

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.system.exitProcess

/**
 * The last crash, kept on the device so it can be read back without a cable.
 *
 * There is no crash reporter in this app on purpose — Crashlytics needs GMS, which is exactly
 * the phone we cannot debug — and the APKs are sideloaded, so Play Console never sees them.
 * When the owner says «выкидывает из приложения», this file is the only evidence there is.
 *
 * With [install] `restartUi = true` the app also comes back on its own instead of vanishing
 * into the launcher (owner, 07.09.2026): the report is saved, the launcher activity is
 * scheduled a moment later, and the fresh start says out loud what happened. A crash that
 * repeats within [RESTART_GUARD_MS] is handed to the system instead — an app that restarts
 * into the same crash forever is worse than one that stops.
 */
class CrashLog(context: Context, private val versionName: String) {
    private val appContext = context.applicationContext
    private val file = File(appContext.filesDir, FILE_NAME)
    private val prefs = appContext.getSharedPreferences("crash_log", Context.MODE_PRIVATE)

    fun install(restartUi: Boolean = false) {
        val system = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { save(thread, error) }
            val restarted = restartUi && runCatching { scheduleRestart() }.getOrDefault(false)
            if (restarted) {
                // Not handed to the system handler: that is what shows «Приложение остановлено»
                // and leaves the child looking at the launcher.
                Process.killProcess(Process.myPid())
                exitProcess(EXIT_CODE)
            }
            system?.uncaughtException(thread, error)
        }
    }

    /** The stored report, or null when the app has not crashed since it was last cleared. */
    fun last(): String? = runCatching {
        file.takeIf { it.exists() }?.readText()?.takeIf { it.isNotBlank() }
    }.getOrNull()

    fun clear() {
        runCatching { file.delete() }
    }

    /**
     * Message for the start that follows a crash-restart, once. Plain and short by request
     * (owner, 07.09.2026): no retry, no dialog — the parent only needs to know the blink was
     * an error, and the full trace is already in «Отчёт о сбое» for whoever asks.
     */
    fun consumeRestartNotice(): String? {
        if (!prefs.getBoolean(KEY_NOTICE, false)) return null
        prefs.edit().putBoolean(KEY_NOTICE, false).apply()
        return "Произошла ошибка. Приложение перезапустилось"
    }

    private fun scheduleRestart(): Boolean {
        val now = System.currentTimeMillis()
        if (now - prefs.getLong(KEY_RESTARTED_AT, 0) < RESTART_GUARD_MS) return false
        val intent =
            appContext.packageManager.getLaunchIntentForPackage(appContext.packageName)?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            } ?: return false
        val alarms = appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return false
        val flags =
            PendingIntent.FLAG_ONE_SHOT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        val pending = PendingIntent.getActivity(appContext, RESTART_REQUEST, intent, flags)
        alarms.set(AlarmManager.RTC, now + RESTART_DELAY_MS, pending)
        prefs.edit().putLong(KEY_RESTARTED_AT, now).putBoolean(KEY_NOTICE, true).apply()
        return true
    }

    private fun save(thread: Thread, error: Throwable) {
        val report =
            buildString {
                append(TIMESTAMP.format(Date())).append('\n')
                append("Kite ").append(versionName).append('\n')
                append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
                append(" · Android ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(")\n")
                append("Поток: ").append(thread.name).append("\n\n")
                append(StringWriter().also { writer -> error.printStackTrace(PrintWriter(writer)) })
            }
        file.writeText(report.take(MAX_CHARS))
    }

    private companion object {
        const val FILE_NAME = "last_crash.txt"
        const val KEY_RESTARTED_AT = "restarted_at"
        const val KEY_NOTICE = "restart_notice"

        /** Enough for the trace plus its causes; a runaway chain must not fill the disk. */
        const val MAX_CHARS = 32_000

        /** Long enough that a screen crashing on every open stops instead of looping. */
        const val RESTART_GUARD_MS = 30_000L
        const val RESTART_DELAY_MS = 400L
        const val RESTART_REQUEST = 4021
        const val EXIT_CODE = 10

        val TIMESTAMP = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault())
    }
}
