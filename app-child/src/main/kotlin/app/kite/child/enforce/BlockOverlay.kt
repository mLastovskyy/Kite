package app.kite.child.enforce

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.setPadding

/**
 * Full-screen block window over the offending app (SYSTEM_ALERT_WINDOW — an Activity
 * cannot be started from the background on Android 10+, CLAUDE.md). Views are built in
 * code: this window lives outside any Activity/Compose lifecycle, and programmatic views
 * are the plainest thing that works there. Idempotent: show() updates the text in place.
 */
class BlockOverlay(private val context: Context) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var root: LinearLayout? = null
    private var titleView: TextView? = null
    private var subtitleView: TextView? = null

    fun show(reason: Enforcement.BlockReason) {
        if (!Settings.canDrawOverlays(context)) return // permission revoked; health screen nags
        val (title, subtitle) = texts(reason)
        root?.let {
            titleView?.text = title
            subtitleView?.text = subtitle
            return
        }
        val view = buildView(title, subtitle)
        val params =
            WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT,
            )
        runCatching { windowManager.addView(view, params) }.onSuccess { root = view }
    }

    fun hide() {
        root?.let { runCatching { windowManager.removeView(it) } }
        root = null
        titleView = null
        subtitleView = null
    }

    val isShown: Boolean get() = root != null

    private fun texts(reason: Enforcement.BlockReason): Pair<String, String> = when (reason) {
        Enforcement.BlockReason.AppBlocked -> "Приложение заблокировано" to "Родитель ограничил это приложение"
        Enforcement.BlockReason.QuietHours -> "Тихие часы" to "Сейчас время без экрана"
        Enforcement.BlockReason.AppLimit -> "Лимит приложения исчерпан" to "Время для этого приложения на сегодня вышло"
        Enforcement.BlockReason.DailyLimit -> "Время вышло" to "Дневной лимит экрана исчерпан"
    }

    private fun buildView(title: String, subtitle: String): LinearLayout {
        val density = context.resources.displayMetrics.density
        fun dp(value: Int): Int = (value * density).toInt()

        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#F2000000"))
            setPadding(dp(32))
            isClickable = true // consume touches so the app underneath gets nothing

            addView(
                TextView(context).apply {
                    titleView = this
                    text = title
                    setTextColor(Color.WHITE)
                    textSize = 28f
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                },
            )
            addView(
                TextView(context).apply {
                    subtitleView = this
                    text = subtitle
                    setTextColor(Color.parseColor("#B3FFFFFF"))
                    textSize = 16f
                    gravity = Gravity.CENTER
                    setPadding(0, dp(8), 0, dp(28))
                },
            )
            addView(
                TextView(context).apply {
                    text = "На главный экран"
                    setTextColor(Color.WHITE)
                    textSize = 17f
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                    background =
                        GradientDrawable().apply {
                            cornerRadius = dp(12).toFloat()
                            setColor(Color.parseColor("#33FFFFFF"))
                        }
                    setPadding(dp(24), dp(14), dp(24), dp(14))
                    setOnClickListener {
                        context.startActivity(
                            Intent(Intent.ACTION_MAIN)
                                .addCategory(Intent.CATEGORY_HOME)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                },
            )
        }
    }
}
