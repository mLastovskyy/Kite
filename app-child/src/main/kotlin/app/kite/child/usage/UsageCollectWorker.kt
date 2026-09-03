package app.kite.child.usage

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.kite.child.apps.InstalledAppsPublisher
import app.kite.child.enforce.RemoteLock
import app.kite.child.enforce.RulesSyncer
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit

/**
 * Periodic screen-time collection. Every 4 hours keeps us far inside the ~1 week system
 * event retention even when EMUI defers background work. KoinComponent for the same reason
 * as KillSwitchWorker — no koin-workmanager artifact for a single binding.
 *
 * The network parts run even when collection fails (no Usage Access yet, right after pairing):
 * the installed-app list and the rules must reach the parent as soon as the phone is paired,
 * not four hours later. [KEY_FORCE_APPS] (set by [UsageCollectScheduler.runNow]) bypasses the
 * publisher's daily throttle.
 */
class UsageCollectWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params),
    KoinComponent {
    private val collector: UsageCollector by inject()
    private val syncer: UsageSyncer by inject()
    private val rulesSyncer: RulesSyncer by inject()
    private val remoteLock: RemoteLock by inject()
    private val appsPublisher: InstalledAppsPublisher by inject()

    override suspend fun doWork(): Result {
        val collected = runCatching { collector.collect() }
        // Network parts are best-effort: offline is normal, the next run re-upserts.
        if (collected.isSuccess) runCatching { syncer.sync() }
        runCatching { appsPublisher.publish(force = inputData.getBoolean(KEY_FORCE_APPS, false)) }
        runCatching { rulesSyncer.refresh() }
        // Command polling backs up the Realtime socket (CLAUDE.md: WebSocket + polling).
        runCatching { remoteLock.pollPending() }
        return if (collected.isSuccess) Result.success() else Result.retry()
    }

    companion object {
        const val KEY_FORCE_APPS = "force_apps"
    }
}

object UsageCollectScheduler {
    private const val UNIQUE_NAME = "usage-collect"
    private const val NOW_NAME = "usage-collect-now"

    /** Enqueues the 4-hour collection. KEEP policy makes repeated calls harmless. */
    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<UsageCollectWorker>(4, TimeUnit.HOURS).build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /**
     * One immediate run — right after pairing and right after the permission wizard finishes —
     * so the parent's map, statistics and app list fill in within a minute of setup instead of
     * at the next 4-hour tick. Expedited where the OS allows it; a plain run otherwise.
     */
    fun runNow(context: Context) {
        val request =
            OneTimeWorkRequestBuilder<UsageCollectWorker>()
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setInputData(workDataOf(UsageCollectWorker.KEY_FORCE_APPS to true))
                .build()
        WorkManager.getInstance(context).enqueueUniqueWork(NOW_NAME, ExistingWorkPolicy.REPLACE, request)
    }
}
