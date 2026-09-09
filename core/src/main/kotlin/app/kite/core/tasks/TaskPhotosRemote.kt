package app.kite.core.tasks

import app.kite.core.auth.AuthException
import app.kite.core.auth.SessionManager
import app.kite.core.config.SupabaseConfig
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess

/**
 * Photos the child attaches to a task, in Supabase Storage (bucket `task-photos`, public read
 * like `avatars`). Every attempt gets its own object — `<member>/<task>_<epoch_ms>.jpg` — so the
 * picture «История заданий» points at can never be overwritten by the next attempt.
 */
class TaskPhotosRemote(
    private val httpClient: HttpClient,
    private val sessionManager: SessionManager,
    private val baseUrl: String = SupabaseConfig.URL,
    private val apiKey: String = SupabaseConfig.PUBLISHABLE_KEY,
) {
    /** Child: upload one JPEG and get back the public URL to store on the task. */
    suspend fun upload(memberId: String, taskId: String, jpeg: ByteArray): Result<String> = runCatching {
        val token = sessionManager.validAccessToken() ?: throw AuthException("Нужно войти заново")
        val path = "$memberId/${taskId}_${System.currentTimeMillis()}.jpg"
        val response =
            httpClient.post("$baseUrl/storage/v1/object/task-photos/$path") {
                header("apikey", apiKey)
                header("Authorization", "Bearer $token")
                contentType(ContentType.Image.JPEG)
                setBody(jpeg)
            }
        if (!response.status.isSuccess()) {
            throw AuthException(
                when (response.status) {
                    HttpStatusCode.PayloadTooLarge -> "Фото слишком большое"
                    HttpStatusCode.Unauthorized -> "Сессия истекла — войди заново"
                    else -> "Не удалось отправить фото (${response.status.value})"
                },
                status = response.status.value,
            )
        }
        "$baseUrl/storage/v1/object/public/task-photos/$path"
    }.recoverCatching { throwable ->
        throw if (throwable is AuthException) throwable else AuthException("Нет соединения с сервером")
    }

    companion object {
        /** Longest side of the uploaded JPEG: a phone camera frame is megabytes, a proof is not. */
        const val MAX_PX = 1280
        const val QUALITY = 80
    }
}
