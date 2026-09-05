package app.kite.child.findphone

import android.content.Context
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
 * Full-screen "your phone is being located" overlay with a big STOP button, shown while the
 * find-phone alarm rings. Built in code because it lives outside any Activity. If the
 * overlay permission is missing the ringer still sounds and auto-stops after its timeout.
 */
class FindPhoneOverlay(private val context: Context, private val onStop: () -> Unit) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var root: LinearLayout? = null

    fun show() {
        if (!Settings.canDrawOverlays(context) || root != null) return
        val view = buildView()
        val params =
            WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
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
            // Same warm gradient as the block screen: both are Kite Jr speaking, and the child
            // should never see two different apps looking back.
            background =
                GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    intArrayOf(Color.parseColor("#FFC24D"), Color.parseColor("#FF9F1A"), Color.parseColor("#F58500")),
                )
            setPadding(dp(32))
            isClickable = true
            addView(
                TextView(context).apply {
                    text = "Поиск телефона"
                    setTextColor(Color.WHITE)
                    textSize = 26f
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                    setPadding(0, 0, 0, dp(8))
                },
            )
            addView(
                TextView(context).apply {
                    text = "Родитель ищет телефон"
                    setTextColor(Color.parseColor("#D9FFFFFF"))
                    textSize = 16f
                    gravity = Gravity.CENTER
                    setPadding(0, 0, 0, dp(28))
                },
            )
            addView(
                TextView(context).apply {
                    text = "Остановить"
                    setTextColor(Color.parseColor("#E86A00"))
                    textSize = 18f
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                    background =
                        GradientDrawable().apply {
                            cornerRadius = dp(14).toFloat()
                            setColor(Color.WHITE)
                        }
                    setPadding(dp(40), dp(16), dp(40), dp(16))
                    setOnClickListener { onStop() }
                },
            )
        }
    }
}
