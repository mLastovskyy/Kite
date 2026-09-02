package app.kite.child.enforce

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Settings
import android.provider.Telephony
import app.kite.child.identity.MemberIdentity
import app.kite.child.tasks.TasksStore
import app.kite.child.tasks.TasksSyncer
import app.kite.child.usage.UsageCollector
import app.kite.core.approval.ApprovalRequest
import app.kite.core.approval.ApprovalsRemote
import app.kite.core.commands.RealtimeCommands
import app.kite.core.killswitch.KillSwitchRepository
import app.kite.core.rules.ChildRules
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
    private val remoteLock: RemoteLock,
    private val realtime: RealtimeCommands,
    private val identity: MemberIdentity,
    private val bonusStore: BonusStore,
    private val approvalsRemote: ApprovalsRemote,
    private val tasksStore: TasksStore,
    private val tasksSyncer: TasksSyncer,
) {
    private var scope: CoroutineScope? = null
    private var tickerJob: Job? = null
    private var currentPackage: String? = null
    private var enforcementDisabled = false
    private val evaluateMutex = Mutex()

    fun start(serviceScope: CoroutineScope) {
        scope = serviceScope
        // The block screen can ask the parent (extra time / unlock) over the network.
        overlay.onRequest = { reason -> serviceScope.launch { requestFromParent(reason) } }
        // … and it can send «Выполнил» on a task, which is what earns the minutes back.
        overlay.onTaskDone = { task ->
            serviceScope.launch {
                tasksSyncer.markDone(task.id)
                evaluate()
            }
        }
        serviceScope.launch {
            killSwitch.disableEnforcement.collect { disabled ->
                enforcementDisabled = disabled
                if (disabled) overlay.hide() else evaluate()
            }
        }
        serviceScope.launch { rulesSyncer.refresh() }
        serviceScope.launch {
            tasksSyncer.refresh()
            evaluate()
        }
        serviceScope.launch {
            // Drain any commands queued while offline, then listen for instant ones.
            runCatching { remoteLock.pollPending() }
            evaluate()
            identity.memberId()?.let { memberId ->
                realtime.listen(memberId, serviceScope) { command ->
                    serviceScope.launch {
                        runCatching { remoteLock.apply(command) }
                        evaluate()
                    }
                }
            }
        }
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
                    // Tasks change more often than rules and drive the block screen.
                    if (System.currentTimeMillis() - lastTasksRefresh > TASKS_REFRESH_MS) {
                        launch { refreshTasks() }
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

    /** Sends the child's over-the-network request for the current block reason. */
    private suspend fun requestFromParent(reason: Enforcement.BlockReason) {
        val familyId = identity.familyId() ?: return
        val memberId = identity.memberId() ?: return
        val pkg = currentPackage
        when (reason) {
            Enforcement.BlockReason.RemoteLocked ->
                approvalsRemote.create(memberId, familyId, ApprovalRequest.TYPE_UNLOCK)
            // An app-limit request names the app (so the parent can grant to it specifically);
            // a daily/quiet request is for everything.
            Enforcement.BlockReason.AppLimit ->
                approvalsRemote.create(
                    memberId,
                    familyId,
                    ApprovalRequest.TYPE_EXTRA_TIME,
                    """{"minutes":15,"package":${jsonStr(pkg)},"label":${jsonStr(pkg?.let(::labelFor))}}""",
                )
            Enforcement.BlockReason.DailyLimit, Enforcement.BlockReason.QuietHours ->
                approvalsRemote.create(memberId, familyId, ApprovalRequest.TYPE_EXTRA_TIME, """{"minutes":15}""")
            Enforcement.BlockReason.AppBlocked -> Unit // fully blocked apps are not requestable
        }
    }

    private fun jsonStr(value: String?): String = if (value == null) "null" else "\"" + value.replace("\"", "\\\"") + "\""

    /** Called from the accessibility service on every window change. */
    fun onForeground(packageName: String) {
        // Our own windows (the block overlay itself!) and system UI (shade, recents)
        // must not steer decisions, or showing the overlay would immediately hide it.
        if (packageName == context.packageName || packageName == SYSTEM_UI) return
        currentPackage = packageName
        scope?.launch { evaluate() }
    }

    private var lastRulesRefresh = 0L
    private var lastTasksRefresh = 0L

    private suspend fun evaluate(): Unit = evaluateMutex.withLock {
        if (enforcementDisabled) {
            overlay.hide()
            return
        }
        val rules = rulesStore.rules()
        // Remote lock («Заблокировать сейчас») blocks the pool the way Kids360 does: the
        // phone stays a phone — essentials and the parent's «Доступны всегда» list keep
        // working — and it applies even before any window event arrives.
        if (remoteLock.locked) {
            val pkg = currentPackage
            if (pkg != null && (pkg in exemptPackages() || rules.appRules[pkg]?.alwaysAllowed == true)) {
                overlay.hide()
            } else {
                overlay.show(Enforcement.BlockReason.RemoteLocked)
            }
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
        val date = LocalDate.now(zone)
        val today = date.toString()
        val isoDayOfWeek = date.dayOfWeek.value
        val usedToday = dao.dayTotals(today, today).firstOrNull()?.totalMs ?: 0L
        val usedApp = dao.appTotals(today, today).firstOrNull { it.packageName == pkg }?.totalMs ?: 0L
        // Parent-granted "extra time" — for all apps and/or for this specific app.
        val dayBonus = bonusStore.minutesFor(today)
        val appBonus = bonusStore.appMinutesFor(today, pkg)
        val minuteOfDay = LocalTime.now(zone).let { it.hour * 60 + it.minute }

        when (val verdict = Enforcement.verdict(rules, pkg, isoDayOfWeek, minuteOfDay, usedToday, usedApp, dayBonus, appBonus)) {
            Enforcement.Verdict.Allow -> {
                overlay.hide()
                Enforcement.warningThreshold(rules.limitFor(isoDayOfWeek)?.plus(dayBonus), usedToday)?.let { threshold ->
                    warnings.maybeWarn(today, "day", threshold, appLabel = null)
                }
                Enforcement.warningThreshold(rules.appRules[pkg]?.dailyLimitMinutes?.plus(appBonus), usedApp)?.let { threshold ->
                    warnings.maybeWarn(today, pkg, threshold, appLabel = labelFor(pkg))
                }
            }
            is Enforcement.Verdict.Block ->
                showBlock(
                    reason = verdict.reason,
                    packageName = pkg,
                    rules = rules,
                    isoDayOfWeek = isoDayOfWeek,
                    minuteOfDay = minuteOfDay,
                    dayBonus = dayBonus,
                    appBonus = appBonus,
                )
        }
    }

    /**
     * Fills the block screen in: which app it was, the rule in plain words, and the tasks
     * that can give the time back. Tasks are offered only where finishing one actually
     * helps — extra minutes lift a limit, they do not open a fully closed app or end quiet
     * hours.
     */
    private fun showBlock(
        reason: Enforcement.BlockReason,
        packageName: String,
        rules: ChildRules,
        isoDayOfWeek: Int,
        minuteOfDay: Int,
        dayBonus: Int,
        appBonus: Int,
    ) {
        val ruleText =
            when (reason) {
                Enforcement.BlockReason.DailyLimit ->
                    rules.limitFor(isoDayOfWeek)?.let { limit ->
                        "Лимит на день — ${formatMinutes(limit + dayBonus)}. Экран снова откроется завтра утром."
                    }
                Enforcement.BlockReason.AppLimit ->
                    rules.appRules[packageName]?.dailyLimitMinutes?.let { limit ->
                        "Лимит для этого приложения — ${formatMinutes(limit + appBonus)} в день."
                    }
                Enforcement.BlockReason.QuietHours ->
                    rules.quietHours.firstOrNull { it.isActive(isoDayOfWeek, minuteOfDay) }?.let { interval ->
                        val window = "с ${clock(interval.startMinutes)} до ${clock(interval.endMinutes)}"
                        if (interval.name.isBlank()) "Сейчас расписание: $window." else "Расписание «${interval.name}»: $window."
                    }
                else -> null
            }
        val earnable = reason == Enforcement.BlockReason.DailyLimit || reason == Enforcement.BlockReason.AppLimit
        overlay.show(
            reason = reason,
            appLabel = labelFor(packageName),
            ruleText = ruleText,
            tasks = if (earnable) tasksStore.visible() else emptyList(),
        )
        if (earnable) scope?.launch { refreshTasks() }
    }

    /** Rate-limited task pull; redraws the block screen if it is up when fresher data lands. */
    private suspend fun refreshTasks() {
        if (System.currentTimeMillis() - lastTasksRefresh < TASKS_REFRESH_MS) return
        lastTasksRefresh = System.currentTimeMillis()
        tasksSyncer.refresh()
        if (overlay.isShown) evaluate()
    }

    private fun formatMinutes(minutes: Int): String {
        val hours = minutes / 60
        val rest = minutes % 60
        return when {
            hours > 0 && rest > 0 -> "$hours ч $rest мин"
            hours > 0 -> "$hours ч"
            else -> "$rest мин"
        }
    }

    private fun clock(minuteOfDay: Int): String = "%02d:%02d".format(minuteOfDay / 60, minuteOfDay % 60)

    /**
     * Never blocked by limits or quiet hours — the phone must stay a phone (CLAUDE.md, «never
     * a brick»): this app, the launcher, system UI, and the device's essentials — dialer,
     * SMS, contacts, camera, clock and Settings. The parent's own «всегда доступны» list is
     * applied on top by [Enforcement.verdict]. Settings is safe to leave open here: during
     * allowed time the child can open it anyway, and the dangerous screens (app details,
     * admin deactivation) are guarded separately by [UninstallGuard]. The explicit remote
     * lock is the one exception and keeps only the dialer (see [evaluate]).
     */
    private fun exemptPackages(): Set<String> {
        val launcher = resolvePackage(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))
        return setOfNotNull(
            context.packageName,
            SYSTEM_UI,
            launcher,
            dialerPackage(),
            runCatching { Telephony.Sms.getDefaultSmsPackage(context) }.getOrNull(),
            resolvePackage(Intent(Intent.ACTION_VIEW, ContactsContract.Contacts.CONTENT_URI)),
            resolvePackage(Intent(MediaStore.ACTION_IMAGE_CAPTURE)),
            resolvePackage(Intent(AlarmClock.ACTION_SHOW_ALARMS)),
            resolvePackage(Intent(Settings.ACTION_SETTINGS)),
        )
    }

    private fun dialerPackage(): String? = resolvePackage(Intent(Intent.ACTION_DIAL))

    /** The default handler's package for [intent], or null when nothing on the device handles it. */
    private fun resolvePackage(intent: Intent): String? = runCatching {
        context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
    }.getOrNull()

    private fun labelFor(packageName: String): String = runCatching {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
    }.getOrDefault(packageName)

    private companion object {
        const val SYSTEM_UI = "com.android.systemui"
        const val TICK_MS = 30_000L
        const val RULES_REFRESH_MS = 60L * 60 * 1000
        const val TASKS_REFRESH_MS = 5L * 60 * 1000
    }
}
