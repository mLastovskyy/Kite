package app.kite.child.enforce

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import app.kite.child.usage.UsageCollector
import app.kite.core.killswitch.KillSwitchRepository
import app.kite.core.usage.UsageDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * The M5 enforcement loop, driven by [app.kite.child.service.KiteAccessibilityService]:
 * window changes call [onForeground], a 30-second ticker keeps limits fresh while the
 * same app stays open. Every decision uses only local data (cached rules + Room usage) —
 * fully offline. The kill switch (CLAUDE.md safety requirement) disarms everything.
 */
class EnforcementController(
    private val context: Context,
    private val collector: UsageCollector,
    private val dao: UsageDao,
    private val rulesStore: RulesStore,
    private val rulesSyncer: RulesSyncer,
    private val killSwitch: KillSwitchRepository,
    private val overlay: BlockOverlay,
    private val warnings: WarningTracker,
) {
    private var scope: CoroutineScope? = null
    private var tickerJob: Job? = null
    private var currentPackage: String? = null
    private var enforcementDisabled = false
    private val evaluateMutex = Mutex()

    fun start(serviceScope: CoroutineScope) {
        scope = serviceScope
        serviceScope.launch {
            killSwitch.disableEnforcement.collect { disabled ->
                enforcementDisabled = disabled
                if (disabled) overlay.hide() else evaluate()
            }
        }
        serviceScope.launch { rulesSyncer.refresh() }
        tickerJob =
            serviceScope.launch {
                while (true) {
                    evaluate()
                    delay(TICK_MS)
                    // Rules refresh piggybacks on the ticker once an hour.
                    if (System.currentTimeMillis() - lastRulesRefresh > RULES_REFRESH_MS) {
                        lastRulesRefresh = System.currentTimeMillis()
                        launch { rulesSyncer.refresh() }
                    }
                }
            }
    }

    fun stop() {
        tickerJob?.cancel()
        tickerJob = null
        scope = null
        overlay.hide()
    }

    /** Called from the accessibility service on every window change. */
    fun onForeground(packageName: String) {
        // Our own windows (the block overlay itself!) and system UI (shade, recents)
        // must not steer decisions, or showing the overlay would immediately hide it.
        if (packageName == context.packageName || packageName == SYSTEM_UI) return
        currentPackage = packageName
        scope?.launch { evaluate() }
    }

    private var lastRulesRefresh = 0L

    private suspend fun evaluate(): Unit = evaluateMutex.withLock {
        if (enforcementDisabled) {
            overlay.hide()
            return
        }
        val pkg = currentPackage ?: return
        if (pkg in exemptPackages()) {
            overlay.hide()
            return
        }

        // Bring Room up to `now` so limits count the ongoing session too.
        runCatching { collector.collect() }

        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone).toString()
        val usedToday = dao.dayTotals(today, today).firstOrNull()?.totalMs ?: 0L
        val usedApp = dao.appTotals(today, today).firstOrNull { it.packageName == pkg }?.totalMs ?: 0L
        val rules = rulesStore.rules()
        val minuteOfDay = LocalTime.now(zone).let { it.hour * 60 + it.minute }

        when (val verdict = Enforcement.verdict(rules, pkg, minuteOfDay, usedToday, usedApp)) {
            Enforcement.Verdict.Allow -> {
                overlay.hide()
                Enforcement.warningThreshold(rules.dailyLimitMinutes, usedToday)?.let { threshold ->
                    warnings.maybeWarn(today, "day", threshold, appLabel = null)
                }
                Enforcement.warningThreshold(rules.appRules[pkg]?.dailyLimitMinutes, usedApp)?.let { threshold ->
                    warnings.maybeWarn(today, pkg, threshold, appLabel = labelFor(pkg))
                }
            }
            is Enforcement.Verdict.Block -> overlay.show(verdict.reason)
        }
    }

    /**
     * Never blocked: this app, the launcher and the dialer (emergency calls must always
     * work). Deliberately NOT exempting Settings — that fight belongs to M6's guard.
     */
    private fun exemptPackages(): Set<String> {
        val pm = context.packageManager
        val launcher =
            pm.resolveActivity(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
                PackageManager.MATCH_DEFAULT_ONLY,
            )?.activityInfo?.packageName
        val dialer =
            pm.resolveActivity(
                Intent(Intent.ACTION_DIAL),
                PackageManager.MATCH_DEFAULT_ONLY,
            )?.activityInfo?.packageName
        return setOfNotNull(context.packageName, SYSTEM_UI, launcher, dialer)
    }

    private fun labelFor(packageName: String): String = runCatching {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
    }.getOrDefault(packageName)

    private companion object {
        const val SYSTEM_UI = "com.android.systemui"
        const val TICK_MS = 30_000L
        const val RULES_REFRESH_MS = 60L * 60 * 1000
    }
}
