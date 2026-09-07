package app.kite.core.diagnostics

import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The last crash, kept on the device so it can be read back without a cable.
 *
 * There is no crash reporter in this app on purpose — Crashlytics needs GMS, which is exactly
 * the phone we cannot debug — and the APKs are sideloaded, so Play Console never sees them.
 * When the owner says «выкидывает из приложения», this file is the only evidence there is.
 *
 * Written from the default uncaught-exception handler, then handed to the system one, so the
 * app still dies the way Android expects. Only the newest crash is kept.
 */
class CrashLog(context: Context, private val versionName: String) {
    private val file = File(context.applicationContext.filesDir, FILE_NAME)

    fun install() {
        val system = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { save(thread, error) }
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

        /** Enough for the trace plus its causes; a runaway chain must not fill the disk. */
        const val MAX_CHARS = 32_000

        val TIMESTAMP = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault())
    }
}
