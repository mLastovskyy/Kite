package app.kite.child.enforce

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Settings
import android.provider.Telephony
import android.util.Log
import androidx.core.content.ContextCompat
import app.kite.child.identity.DeviceReporter
import app.kite.child.identity.MemberIdentity
import app.kite.child.identity.ParentsStore
import app.kite.child.location.LocationPolicy
import app.kite.child.request.AskParentActivity
import app.kite.child.request.ChildRequestSender
import app.kite.child.tasks.TasksStore
import app.kite.child.tasks.TasksSyncer
import app.kite.child.usage.UsageCollector
import app.kite.core.approval.ApprovalRequest
import app.kite.core.approval.ApprovalsRemote
import app.kite.core.commands.DeviceCommand
import app.kite.core.commands.RealtimeCommands
import app.kite.core.killswitch.KillSwitchRepository
import app.kite.core.net.ConnectivityObserver
import app.kite.core.realtime.RealtimeTable
import app.kite.core.rules.ChildRules
import app.kite.core.rules.Essentials
import app.kite.core.usage.UsageDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
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
    private val protectionState: ProtectionState,
    private val deviceReporter: DeviceReporter,
    private val realtimeTable: RealtimeTable,
    private val parentsStore: ParentsStore,
    private val requestSender: ChildRequestSender,
    private val locationPolicy: LocationPolicy,
    private val connectivity: ConnectivityObserver,
) {
    private val requestPrefs = context.getSharedPreferences("approval_requests", Context.MODE_PRIVATE)
    private var scope: CoroutineScope? = null
    private var tickerJob: Job? = null
    private var currentPackage: String? = null
    private var wakeReceiver: BroadcastReceiver? = null

    @Volatile private var knownRules: ChildRules = ChildRules()

    @Volatile private var exemptCache: Set<String> = emptySet()
    private var exemptCachedAt = 0L

    @Volatile private var limitBlocked: Set<String> = emptySet()

    @Volatile private var dayLimitReached = false
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
        serviceScope.launch {
            protectionState.released.collect { released ->
                if (released) {
                    overlay.hide()
                    runCatching { locationPolicy.release() }
                } else {
                    evaluate()
                }
            }
        }
        // Doze can silence the sockets for hours. Unlocking the phone is the moment the child
        // would notice a stale answer, so that is where the state is pulled fresh.
        wakeReceiver =
            object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    serviceScope.launch {
                        runCatching { remoteLock.pollPending() }
                        runCatching { rulesSyncer.refresh() }
                        runCatching { deviceReporter.report() }
                        evaluate()
                    }
                }
            }.also {
                val filter = IntentFilter(Intent.ACTION_SCREEN_ON).apply { addAction(Intent.ACTION_USER_PRESENT) }
                ContextCompat.registerReceiver(context, it, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
            }
        runCatching { locationPolicy.enforce() }
        serviceScope.launch {
            tasksSyncer.refresh()
            evaluate()
        }
        // This service starts with the phone, usually long before the child has been paired,
        // so everything that needs a member id waits for one instead of silently doing nothing
        // until the next reboot — that gap is what left a freshly paired phone unenforced.
        serviceScope.launch {
            runCatching { remoteLock.pollPending() }
            evaluate()
            val memberId = awaitMemberId() ?: return@launch
            runCatching { deviceReporter.report() }
            runCatching { parentsStore.refresh() }
            runCatching { rulesSyncer.refresh() }
            runCatching { remoteLock.pollPending() }
            evaluate()
            realtime.listen(memberId, serviceScope) { command ->
                serviceScope.launch {
                    runCatching { remoteLock.apply(command) }
                    if (command.command == DeviceCommand.REFRESH) runCatching { deviceReporter.report() }
                    evaluate()
                }
            }
            realtimeTable.subscribe(
                scope = serviceScope,
                table = "member_rules",
                filter = "member_id=eq.$memberId",
                events = listOf(RealtimeTable.EVENT_INSERT, RealtimeTable.EVENT_UPDATE),
            ) {
                serviceScope.launch {
                    rulesSyncer.refresh()
                    evaluate()
                }
            }
        }
        tickerJob =
            serviceScope.launch {
                while (true) {
                    evaluate()
                    val blocked = overlay.isShown
                    delay(if (blocked) BLOCKED_TICK_MS else TICK_MS)
                    if (blocked || System.currentTimeMillis() - lastCommandPoll > COMMAND_POLL_MS) {
                        lastCommandPoll = System.currentTimeMillis()
                        launch { runCatching { remoteLock.pollPending() } }
                    }
                    // Rules refresh piggybacks on the ticker once an hour.
                    if (System.currentTimeMillis() - lastRulesRefresh > RULES_REFRESH_MS) {
                        lastRulesRefresh = System.currentTimeMillis()
                        launch { rulesSyncer.refresh() }
                        launch { runCatching { deviceReporter.report() } }
                    }
                    // Tasks change more often than rules and drive the block screen.
                    if (System.currentTimeMillis() - lastTasksRefresh > TASKS_REFRESH_MS) {
                        launch { refreshTasks() }
                    }
                }
            }
    }

    private suspend fun awaitMemberId(): String? {
        while (currentCoroutineContext().isActive) {
            identity.memberId()?.let { return it }
            delay(IDENTITY_RETRY_MS)
        }
        return null
    }

    fun stop() {
        wakeReceiver?.let { runCatching { context.unregisterReceiver(it) } }
        wakeReceiver = null
        tickerJob?.cancel()
        tickerJob = null
        scope = null
        overlay.hide()
    }

    /**
     * Sends the child's over-the-network request for the current block reason. With a second
     * parent in the family the child picks who to ask first, which needs a real window — the
     * block screen is a raw system overlay, so [AskParentActivity] carries the choice.
     */
    private suspend fun requestFromParent(reason: Enforcement.BlockReason) {
        val type = requestType(reason) ?: return
        val payload = requestPayload(reason)
        if (!isOnline()) {
            overlay.requestOutcome("Нет интернета — введи код родителя")
            return
        }
        if (requestSender.needsChoice()) {
            overlay.requestOutcome(actionLabel(reason))
            runCatching { context.startActivity(AskParentActivity.intent(context, type, payload)) }
            return
        }
        if (!allowRequest(reason.name)) {
            overlay.requestOutcome("Запрос уже отправлен")
            return
        }
        val outcome =
            requestSender.send(type, payload, target = null)
                .fold(onSuccess = { "Запрос отправлен" }, onFailure = { "Не отправилось — введи код родителя" })
        overlay.requestOutcome(outcome)
    }

    private fun actionLabel(reason: Enforcement.BlockReason): String =
        if (reason == Enforcement.BlockReason.RemoteLocked) "Попросить разблокировать" else "Попросить разрешение"

    private fun isOnline(): Boolean = scope?.let { connectivity.online(it).value } ?: false

    private fun requestType(reason: Enforcement.BlockReason): String? = when (reason) {
        Enforcement.BlockReason.RemoteLocked -> ApprovalRequest.TYPE_UNLOCK
        Enforcement.BlockReason.AppLimit, Enforcement.BlockReason.DailyLimit, Enforcement.BlockReason.QuietHours ->
            ApprovalRequest.TYPE_EXTRA_TIME
        Enforcement.BlockReason.AppBlocked -> null
    }

    /** An app-limit request names the app, so the parent can grant time to it specifically. */
    private fun requestPayload(reason: Enforcement.BlockReason): String? {
        val pkg = currentPackage
        return when (reason) {
            Enforcement.BlockReason.AppLimit ->
                """{"minutes":15,"package":${jsonStr(pkg)},"label":${jsonStr(pkg?.let(::labelFor))}}"""
            Enforcement.BlockReason.DailyLimit, Enforcement.BlockReason.QuietHours -> """{"minutes":15}"""
            else -> null
        }
    }

    private fun allowRequest(kind: String): Boolean {
        val now = System.currentTimeMillis()
        val last = requestPrefs.getLong(kind, 0L)
        if (now - last < REQUEST_COOLDOWN_MS) return false
        requestPrefs.edit().putLong(kind, now).apply()
        return true
    }

    private fun jsonStr(value: String?): String = if (value == null) "null" else "\"" + value.replace("\"", "\\\"") + "\""

    /** Called from the accessibility service on every window change. */
    fun onForeground(packageName: String) {
        // Our own windows (the block overlay itself!) and system UI (shade, recents)
        // must not steer decisions, or showing the overlay would immediately hide it.
        if (packageName == context.packageName || packageName == SYSTEM_UI) return
        currentPackage = packageName
        Log.d(TAG, "foreground=$packageName")
        // The window is already on screen by the time this arrives, so the cover has to go up
        // in this call — reading usage from Room first is what let the child see the app.
        instantBlock(packageName)?.let { reason -> showBlock(reason, packageName, knownRules) }
        scope?.launch { evaluate() }
    }

    /**
     * The part of the decision that needs nothing but memory: an explicitly blocked app, an
     * active schedule, a remote lock, or a limit that was already spent the last time the
     * numbers were read. [evaluate] still runs right after and corrects anything finer.
     */
    private fun instantBlock(packageName: String): Enforcement.BlockReason? {
        if (enforcementDisabled || protectionState.isReleased()) return null
        if (packageName in cachedExempt()) return null
        val rules = knownRules
        val appRule = rules.appRules[packageName]
        if (appRule?.alwaysAllowed == true) return null
        if (remoteLock.locked) return Enforcement.BlockReason.RemoteLocked
        if (Essentials.isEssential(packageName)) return null
        if (appRule?.blocked == true) return Enforcement.BlockReason.AppBlocked
        val zone = ZoneId.systemDefault()
        val date = LocalDate.now(zone)
        val minuteOfDay = LocalTime.now(zone).let { it.hour * 60 + it.minute }
        if (rules.scheduleBlocking(packageName, date.dayOfWeek.value, minuteOfDay) != null) {
            return Enforcement.BlockReason.QuietHours
        }
        if (packageName in limitBlocked) return Enforcement.BlockReason.AppLimit
        if (dayLimitReached && rules.limitFor(date.dayOfWeek.value) != null) return Enforcement.BlockReason.DailyLimit
        return null
    }

    private fun cachedExempt(): Set<String> {
        val now = System.currentTimeMillis()
        if (exemptCache.isEmpty() || now - exemptCachedAt > EXEMPT_CACHE_MS) {
            exemptCache = exemptPackages()
            exemptCachedAt = now
        }
        return exemptCache
    }

    private var lastRulesRefresh = 0L
    private var lastTasksRefresh = 0L
    private var lastCommandPoll = 0L

    private suspend fun evaluate(): Unit = evaluateMutex.withLock {
        if (enforcementDisabled || protectionState.isReleased()) {
            overlay.hide()
            return
        }
        val rules = rulesStore.rules()
        knownRules = rules
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

        val verdict = Enforcement.verdict(rules, pkg, isoDayOfWeek, minuteOfDay, usedToday, usedApp, dayBonus, appBonus)
        Log.d(TAG, "verdict $pkg -> $verdict")
        when (verdict) {
            Enforcement.Verdict.Allow -> {
                limitBlocked = limitBlocked - pkg
                dayLimitReached = false
                overlay.hide()
                Enforcement.warningThreshold(rules.limitFor(isoDayOfWeek)?.plus(dayBonus), usedToday)?.let { threshold ->
                    warnings.maybeWarn(today, "day", threshold, appLabel = null)
                }
                Enforcement.warningThreshold(rules.appRules[pkg]?.dailyLimitMinutes?.plus(appBonus), usedApp)?.let { threshold ->
                    warnings.maybeWarn(today, pkg, threshold, appLabel = labelFor(pkg))
                }
            }
            is Enforcement.Verdict.Block -> {
                when (verdict.reason) {
                    Enforcement.BlockReason.AppLimit -> limitBlocked = limitBlocked + pkg
                    Enforcement.BlockReason.DailyLimit -> dayLimitReached = true
                    else -> Unit
                }
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
        isoDayOfWeek: Int = LocalDate.now(ZoneId.systemDefault()).dayOfWeek.value,
        minuteOfDay: Int = LocalTime.now(ZoneId.systemDefault()).let { it.hour * 60 + it.minute },
        dayBonus: Int = 0,
        appBonus: Int = 0,
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
                    rules.scheduleBlocking(packageName, isoDayOfWeek, minuteOfDay)?.let { interval ->
                        val window = "с ${clock(interval.startMinutes)} до ${clock(interval.endMinutes)}"
                        if (interval.name.isBlank()) "Сейчас расписание: $window." else "Расписание «${interval.name}»: $window."
                    }
                else -> null
            }
        val author =
            when (reason) {
                Enforcement.BlockReason.RemoteLocked -> parentsStore.nameForUser(remoteLock.lockedBy)
                else -> parentsStore.nameForUser(rulesStore.author())
            }
        val text =
            listOfNotNull(ruleText, author?.let { "Ограничение поставил(а) $it" })
                .joinToString(separator = "\n")
                .ifBlank { null }
        val earnable = reason == Enforcement.BlockReason.DailyLimit || reason == Enforcement.BlockReason.AppLimit
        overlay.show(
            reason = reason,
            appLabel = labelFor(packageName),
            ruleText = text,
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
     * Never blocked by limits or schedules — the phone must stay a phone (CLAUDE.md, «never
     * a brick»): this app, the launcher, system UI, and the device's essentials — dialer,
     * SMS, contacts, camera, files, clock and Settings — plus the well-known messenger,
     * camera and file-manager packages from [Essentials] (owner, 04.09.2026: «мессенджеры и
     * звонки … камера и файлы тоже»). The parent's own «всегда доступны» list is applied on
     * top by [Enforcement.verdict]. Settings is safe to leave open here: during allowed time
     * the child can open it anyway, and the dangerous screens (app details, admin
     * deactivation) are guarded separately by [UninstallGuard]. The same set survives the
     * explicit remote lock (see [evaluate]).
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
            resolvePackage(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)),
            resolvePackage(Intent(AlarmClock.ACTION_SHOW_ALARMS)),
            resolvePackage(Intent(Settings.ACTION_SETTINGS)),
            // The system file manager (DocumentsUI) is the handler for «show downloads».
            resolvePackage(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS)),
        ) + Essentials.OWN_PACKAGES + Essentials.MESSENGER_PACKAGES + Essentials.CAMERA_PACKAGES + Essentials.FILES_PACKAGES
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
        const val BLOCKED_TICK_MS = 5_000L
        const val COMMAND_POLL_MS = 60_000L
        const val RULES_REFRESH_MS = 60L * 60 * 1000
        const val TASKS_REFRESH_MS = 5L * 60 * 1000
        const val TAG = "KiteEnforce"
        const val EXEMPT_CACHE_MS = 5L * 60 * 1000
        const val IDENTITY_RETRY_MS = 10_000L
        const val REQUEST_COOLDOWN_MS = 5L * 60 * 1000
    }
}
