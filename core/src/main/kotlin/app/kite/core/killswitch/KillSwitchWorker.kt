package app.kite.core.killswitch

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit

/**
 * Hourly refresh of `update.json` (CLAUDE.md requires "at least hourly" on the child).
 * KoinComponent because WorkManager instantiates workers itself and M1 deliberately avoids
 * the koin-workmanager artifact for a single binding.
 */
class KillSwitchWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params),
    KoinComponent {
    private val repository: KillSwitchRepository by inject()

    override suspend fun doWork(): Result = if (repository.refresh().isSuccess) Result.success() else Result.retry()
}

object KillSwitchScheduler {
    private const val UNIQUE_NAME = "kill-switch-refresh"

    /** Enqueues the hourly check. KEEP policy makes repeated calls harmless. */
    fun schedule(context: Context) {
        val request =
            PeriodicWorkRequestBuilder<KillSwitchWorker>(1, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }
}
