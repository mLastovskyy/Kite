package app.kite.core.diagnostics

/**
 * Sends the crash this phone stored to the family, once. Both apps call it wherever they
 * already know their family and member — the parent from the tab shell, the child from its
 * device report — so a crash on one phone becomes readable on the other without a cable
 * (owner, 08.09.2026).
 *
 * The report is not deleted after upload: «Отчёт о сбое» on this phone still shows it, and the
 * mark that it was sent lives next to it, so a failed upload is retried on the next call and a
 * successful one is never sent twice.
 */
class CrashReportSync(private val crashLog: CrashLog, private val remote: CrashReportsRemote, private val versionName: String) {
    suspend fun push(familyId: String?, memberId: String?, app: String, authorName: String?): Result<Unit> {
        if (familyId == null || !crashLog.needsUpload()) return Result.success(Unit)
        val report = crashLog.last() ?: return Result.success(Unit)
        val happenedAt = crashLog.lastAt() ?: return Result.success(Unit)
        return remote.upload(
            familyId = familyId,
            memberId = memberId,
            app = app,
            authorName = authorName,
            versionName = versionName,
            happenedAtMs = happenedAt,
            report = report,
        ).onSuccess { crashLog.markUploaded() }
    }
}
