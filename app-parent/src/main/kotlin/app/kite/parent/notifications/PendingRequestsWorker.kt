package app.kite.parent.notifications

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.kite.core.approval.ApprovalRequest
import app.kite.core.approval.ApprovalsRemote
import app.kite.core.family.FamilyRepository
import app.kite.core.notifications.Channels
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit

class PendingRequestsWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params),
    KoinComponent {
    private val familyRepository: FamilyRepository by inject()
    private val approvalsRemote: ApprovalsRemote by inject()

    override suspend fun doWork(): Result {
        val familyId = familyRepository.myFamilies().getOrNull()?.firstOrNull()?.id ?: return Result.success()
        val pending = approvalsRemote.pending(familyId).getOrNull() ?: return Result.retry()
        val members = familyRepository.members(familyId).getOrNull().orEmpty().associateBy { it.id }
        val prefs = applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val known = prefs.getStringSet(KEY_SEEN, emptySet()).orEmpty()
        val fresh = pending.filter { it.id !in known }

        if (fresh.isNotEmpty() && NotificationManagerCompat.from(applicationContext).areNotificationsEnabled()) {
            fresh.forEach { request ->
                val name = members[request.childMemberId]?.displayName?.ifBlank { null } ?: "Ребёнок"
                NotificationManagerCompat.from(applicationContext).notify(
                    request.id.hashCode(),
                    Channels.build(applicationContext, Channels.REQUESTS, "Запрос от ребёнка", "$name ${requestText(request)}"),
                )
            }
        }
        prefs.edit().putStringSet(KEY_SEEN, pending.map { it.id }.toSet()).apply()
        return Result.success()
    }

    private fun requestText(request: ApprovalRequest): String = when (request.type) {
        ApprovalRequest.TYPE_UNLOCK -> "просит разблокировать телефон"
        ApprovalRequest.TYPE_EXTRA_TIME -> "просит ещё немного времени"
        ApprovalRequest.TYPE_REMOVAL -> "просит удалить Kite Jr"
        ApprovalRequest.TYPE_TASK_REQUEST -> "просит новое задание"
        else -> "отправил запрос"
    }

    private companion object {
        const val PREFS = "pending_requests"
        const val KEY_SEEN = "seen_ids"
    }
}

object PendingRequestsScheduler {
    private const val NAME = "kite-pending-requests"

    fun schedule(context: Context) {
        val request =
            PeriodicWorkRequestBuilder<PendingRequestsWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }
}
