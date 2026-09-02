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
import app.kite.child.removal.RemovalActivity

/**
 * "Parent permission required" screen shown over Settings when the child tries to remove
 * the app or its admin (M6). Built in code because it lives outside any Activity. The
 * "request removal" button launches [RemovalActivity] on a user tap (a foreground gesture,
 * so starting the activity is allowed even though the guard runs from a service).
 */
class GuardOverlay(private val context: Context) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var root: LinearLayout? = null

    /**
     * Set by the accessibility service: send the parent an uninstall request. The child gets
     * an answer without the parent being in the room, and the app stays until they approve.
     */
    var onRequestRemoval: (() -> Unit)? = null

    val isShown: Boolean get() = root != null

    fun show() {
        if (!Settings.canDrawOverlays(context) || root != null) return
        val view = buildView()
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
    }

    private fun buildView(): LinearLayout {
        val density = context.resources.displayMetrics.density
        fun dp(value: Int): Int = (value * density).toInt()

        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#F2000000"))
            setPadding(dp(32))
            isClickable = true

            addView(
                TextView(context).apply {
                    text = "Нужно разрешение родителя"
                    setTextColor(Color.WHITE)
                    textSize = 26f
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                },
            )
            addView(
                TextView(context).apply {
                    text =
                        "Удаление и отключение защиты доступны только с разрешения родителя. Приложение останется на телефоне, пока он не подтвердит."
                    setTextColor(Color.parseColor("#B3FFFFFF"))
                    textSize = 16f
                    gravity = Gravity.CENTER
                    setPadding(0, dp(10), 0, dp(28))
                },
            )
            addView(
                TextView(context).apply {
                    text = "Попросить разрешение"
                    setTextColor(Color.parseColor("#3A2200"))
                    textSize = 17f
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                    background =
                        GradientDrawable().apply {
                            cornerRadius = dp(12).toFloat()
                            setColor(Color.parseColor("#FFC44D"))
                        }
                    setPadding(dp(24), dp(14), dp(24), dp(14))
                    setOnClickListener {
                        onRequestRemoval?.invoke()
                        text = "Запрос отправлен родителю"
                        isEnabled = false
                    }
                },
            )
            addView(
                TextView(context).apply {
                    text = "Ввести код родителя"
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
                    (layoutParams as? LinearLayout.LayoutParams)?.topMargin = dp(10)
                    setOnClickListener {
                        hide()
                        context.startActivity(
                            Intent(context, RemovalActivity::class.java)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                },
            )
            addView(
                TextView(context).apply {
                    text = "На главный экран"
                    setTextColor(Color.parseColor("#B3FFFFFF"))
                    textSize = 15f
                    gravity = Gravity.CENTER
                    setPadding(0, dp(18), 0, 0)
                    setOnClickListener {
                        hide()
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
