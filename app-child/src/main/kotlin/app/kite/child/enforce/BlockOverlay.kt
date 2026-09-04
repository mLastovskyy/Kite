package app.kite.child.enforce

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import app.kite.core.tasks.ChildTask

/**
 * The screen the child sees most, so it follows DESIGN_SYSTEM.md to the letter: full-bleed
 * warm gradient, no chrome, the kite mark, the rule that fired in plain words, one action —
 * and never red, never an exclamation mark. Below it the parent-assigned tasks: an
 * exhausted limit is an invitation to earn time, not a dead end (CLAUDE.md).
 *
 * It lives in a SYSTEM_ALERT_WINDOW because an Activity cannot be started from the
 * background on Android 10+, so the views are built in code — there is no Compose host
 * outside an Activity. Idempotent: [show] rebuilds only when the content actually changed.
 */
class BlockOverlay(private val context: Context) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var root: View? = null
    private var signature: String? = null

    /** Set by the enforcement controller: the child asks the parent for the given reason. */
    var onRequest: ((Enforcement.BlockReason) -> Unit)? = null

    /** Set by the enforcement controller: «Выполнил» on one task. */
    var onTaskDone: ((ChildTask) -> Unit)? = null

    val isShown: Boolean get() = root != null

    /**
     * [appLabel] names the app for a per-app limit, [ruleText] states the rule that fired,
     * [tasks] are shown only when finishing one can actually give time back.
     */
    fun show(reason: Enforcement.BlockReason, appLabel: String? = null, ruleText: String? = null, tasks: List<ChildTask> = emptyList()) {
        if (!Settings.canDrawOverlays(context)) return // permission revoked; health screen nags
        val next = signatureOf(reason, appLabel, ruleText, tasks)
        if (root != null && next == signature) return
        val view = buildView(reason, appLabel, ruleText, tasks)
        val params =
            WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR,
                PixelFormat.TRANSLUCENT,
            ).apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }
        val previous = root
        runCatching { windowManager.addView(view, params) }
            .onSuccess {
                previous?.let { old -> runCatching { windowManager.removeView(old) } }
                root = view
                signature = next
            }
    }

    fun hide() {
        root?.let { runCatching { windowManager.removeView(it) } }
        root = null
        signature = null
    }

    private fun signatureOf(reason: Enforcement.BlockReason, appLabel: String?, ruleText: String?, tasks: List<ChildTask>): String =
        buildString {
            append(reason.name).append('|').append(appLabel).append('|').append(ruleText)
            tasks.forEach { append('|').append(it.id).append(it.status) }
        }

    // ── Copy ────────────────────────────────────────────────────────────────
    private fun title(reason: Enforcement.BlockReason, appLabel: String?): String = when (reason) {
        Enforcement.BlockReason.AppBlocked -> if (appLabel != null) "«$appLabel» сейчас закрыто" else "Приложение закрыто"
        Enforcement.BlockReason.QuietHours -> "Сейчас время без экрана"
        Enforcement.BlockReason.AppLimit ->
            if (appLabel != null) "Время в «$appLabel» закончилось" else "Время в приложении закончилось"
        Enforcement.BlockReason.DailyLimit -> "Время на сегодня закончилось"
        Enforcement.BlockReason.RemoteLocked -> "Телефон на паузе"
    }

    private fun fallbackRule(reason: Enforcement.BlockReason): String = when (reason) {
        Enforcement.BlockReason.AppBlocked -> "Родитель закрыл это приложение."
        Enforcement.BlockReason.QuietHours -> "Сейчас тихие часы — экран отдыхает."
        Enforcement.BlockReason.AppLimit -> "Лимит для этого приложения на сегодня исчерпан."
        Enforcement.BlockReason.DailyLimit -> "Дневной лимит экрана исчерпан."
        Enforcement.BlockReason.RemoteLocked -> "Родитель поставил телефон на паузу."
    }

    private fun actionText(reason: Enforcement.BlockReason): String? = when (reason) {
        Enforcement.BlockReason.AppBlocked -> null // a fully closed app is not requestable
        Enforcement.BlockReason.RemoteLocked -> "Попросить разблокировать"
        else -> "Попросить разрешение"
    }

    // ── Views ───────────────────────────────────────────────────────────────
    private val dark: Boolean
        get() = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).toInt()

    private fun font(weight: Int): Typeface? = runCatching {
        ResourcesCompat.getFont(
            context,
            when {
                weight >= 700 -> app.kite.core.R.font.inter_bold
                weight >= 600 -> app.kite.core.R.font.inter_semibold
                else -> app.kite.core.R.font.inter_regular
            },
        )
    }.getOrNull()

    private fun label(text: String, sizeSp: Float, color: Int, weight: Int = 400, topDp: Int = 0): TextView = TextView(context).apply {
        this.text = text
        setTextColor(color)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        typeface = font(weight)
        gravity = Gravity.CENTER
        setPadding(0, dp(topDp), 0, 0)
    }

    private fun buildView(reason: Enforcement.BlockReason, appLabel: String?, ruleText: String?, tasks: List<ChildTask>): View {
        val onGradient = if (dark) Color.parseColor("#F2FFFFFF") else Color.WHITE
        val secondary = if (dark) Color.parseColor("#99FFFFFF") else Color.parseColor("#D9FFFFFF")
        val tertiary = if (dark) Color.parseColor("#73FFFFFF") else Color.parseColor("#B3FFFFFF")

        val content =
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(dp(28), dp(56), dp(28), dp(36))

                addView(
                    KiteMarkView(context, if (dark) Color.parseColor("#FFC44D") else Color.WHITE),
                    LinearLayout.LayoutParams(dp(76), dp(76)),
                )
                addView(label(title(reason, appLabel), 28f, onGradient, weight = 700, topDp = 22))
                addView(label(ruleText ?: fallbackRule(reason), 16f, secondary, topDp = 10))

                // Tasks: the way out of an exhausted limit.
                if (tasks.isNotEmpty()) addView(tasksCard(tasks, onGradient, secondary))
            }

        val scroll =
            ScrollView(context).apply {
                isFillViewport = true
                addView(
                    content,
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    ).apply { gravity = Gravity.CENTER_VERTICAL },
                )
            }

        val footer =
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(dp(28), 0, dp(28), dp(28))

                actionText(reason)?.let { action ->
                    addView(
                        TextView(context).apply {
                            text = action
                            setTextColor(if (dark) Color.parseColor("#3A2200") else Color.parseColor("#E86A00"))
                            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
                            typeface = font(600)
                            gravity = Gravity.CENTER
                            background =
                                GradientDrawable().apply {
                                    cornerRadius = dp(14).toFloat()
                                    setColor(if (dark) Color.parseColor("#FFC44D") else Color.WHITE)
                                }
                            setPadding(dp(24), dp(15), dp(24), dp(15))
                            setOnClickListener {
                                onRequest?.invoke(reason)
                                text = "Запрос отправлен"
                                isEnabled = false
                            }
                        },
                        LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT),
                    )
                }
                addView(
                    TextView(context).apply {
                        text = "На главный экран"
                        setTextColor(onGradient)
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                        typeface = font(600)
                        gravity = Gravity.CENTER
                        background =
                            GradientDrawable().apply {
                                cornerRadius = dp(14).toFloat()
                                setColor(if (dark) Color.parseColor("#26FFFFFF") else Color.parseColor("#33FFFFFF"))
                            }
                        setPadding(dp(24), dp(14), dp(24), dp(14))
                        setOnClickListener { goHome() }
                    },
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    ).apply { topMargin = dp(10) },
                )
                addView(label("Звонки, сообщения и камера работают всегда", 13f, tertiary, topDp = 14))
            }

        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            isClickable = true // consume touches so the app underneath gets nothing
            background =
                GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    if (dark) {
                        intArrayOf(Color.parseColor("#4A2E10"), Color.parseColor("#2A1A08"), Color.parseColor("#140C03"))
                    } else {
                        intArrayOf(Color.parseColor("#FFC24D"), Color.parseColor("#FF9F1A"), Color.parseColor("#F58500"))
                    },
                )
            addView(
                scroll,
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0).apply { weight = 1f },
            )
            addView(footer, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }
    }

    /** Translucent card listing the tasks the child can finish to get minutes back. */
    private fun tasksCard(tasks: List<ChildTask>, onGradient: Int, secondary: Int): View = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        background =
            GradientDrawable().apply {
                cornerRadius = dp(16).toFloat()
                setColor(if (dark) Color.parseColor("#26FFFFFF") else Color.parseColor("#33FFFFFF"))
            }
        setPadding(dp(16), dp(14), dp(16), dp(14))

        addView(
            label("Задания — выполни и получи время", 13f, secondary).apply {
                gravity = Gravity.START
            },
        )
        tasks.take(MAX_TASKS).forEach { task ->
            addView(taskRow(task, onGradient, secondary))
        }
        if (tasks.size > MAX_TASKS) {
            addView(label("И ещё ${tasks.size - MAX_TASKS} в Kite Jr", 13f, secondary, topDp = 8))
        }
    }.also { card ->
        card.layoutParams =
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(22) }
    }

    private fun taskRow(task: ChildTask, onGradient: Int, secondary: Int): View = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, dp(10), 0, dp(2))

        addView(
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(
                    label(task.title, 16f, onGradient, weight = 600).apply {
                        gravity = Gravity.START
                    },
                )
                addView(
                    label("+${task.rewardMinutes} мин", 13f, secondary).apply {
                        gravity = Gravity.START
                    },
                )
            },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT).apply { weight = 1f },
        )
        addView(
            TextView(context).apply {
                val waiting = !task.isOpen
                text = if (waiting) "Ждём родителя" else "Выполнил"
                setTextColor(if (waiting) secondary else onGradient)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                typeface = font(600)
                gravity = Gravity.CENTER
                background =
                    GradientDrawable().apply {
                        cornerRadius = dp(11).toFloat()
                        setColor(if (waiting) Color.TRANSPARENT else Color.parseColor("#40FFFFFF"))
                    }
                setPadding(dp(14), dp(9), dp(14), dp(9))
                if (!waiting) {
                    setOnClickListener {
                        text = "Ждём родителя"
                        setTextColor(secondary)
                        background = null
                        isEnabled = false
                        onTaskDone?.invoke(task)
                    }
                }
            },
        )
    }

    private fun goHome() {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_HOME)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    /**
     * The Kite mark: a diamond with its cross and a short tail, the same shape as the app
     * icon and the preset avatar, drawn here with plain Canvas because this window has no
     * Compose host.
     */
    private class KiteMarkView(context: Context, private val tint: Int) : View(context) {
        private val paint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                color = tint
            }
        private val diamond = Path()
        private val tail = Path()

        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            paint.strokeWidth = w * 0.075f
            diamond.reset()
            diamond.moveTo(w * 0.5f, h * 0.06f)
            diamond.lineTo(w * 0.88f, h * 0.44f)
            diamond.lineTo(w * 0.5f, h * 0.78f)
            diamond.lineTo(w * 0.12f, h * 0.44f)
            diamond.close()
            canvas.drawPath(diamond, paint)
            canvas.drawLine(w * 0.5f, h * 0.06f, w * 0.5f, h * 0.78f, paint)
            canvas.drawLine(w * 0.12f, h * 0.44f, w * 0.88f, h * 0.44f, paint)
            tail.reset()
            tail.moveTo(w * 0.5f, h * 0.78f)
            tail.cubicTo(w * 0.38f, h * 0.88f, w * 0.64f, h * 0.92f, w * 0.5f, h * 0.99f)
            paint.strokeWidth = w * 0.055f
            canvas.drawPath(tail, paint)
        }
    }

    private companion object {
        const val MAX_TASKS = 3
    }
}
