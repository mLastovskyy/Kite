package app.kite.child.usage

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit

/**
 * Periodic screen-time collection. Every 4 hours keeps us far inside the ~1 week system
 * event retention even when EMUI defers background work. KoinComponent for the same reason
 * as KillSwitchWorker — no koin-workmanager artifact for a single binding.
 */
class UsageCollectWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params),
    KoinComponent {
    private val collector: UsageCollector by inject()
    private val syncer: UsageSyncer by inject()

    override suspend fun doWork(): Result = runCatching { collector.collect() }.fold(
        onSuccess = {
            // Aggregate upload is best-effort: offline is normal, the next run re-upserts.
            runCatching { syncer.sync() }
            Result.success()
        },
        onFailure = { Result.retry() },
    )
}

object UsageCollectScheduler {
    private const val UNIQUE_NAME = "usage-collect"

    /** Enqueues the 4-hour collection. KEEP policy makes repeated calls harmless. */
    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<UsageCollectWorker>(4, TimeUnit.HOURS).build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }
}
