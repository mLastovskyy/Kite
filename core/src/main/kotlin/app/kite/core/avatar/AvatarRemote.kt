package app.kite.core.avatar

import app.kite.core.auth.AuthException
import app.kite.core.auth.AuthState
import app.kite.core.auth.SessionManager
import app.kite.core.config.SupabaseConfig
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.http.parameters
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Uploads a custom avatar image to Supabase Storage (bucket `avatars`, per-user folder)
 * and points the member's avatar_url at the public URL. The bucket is public-read, so the
 * returned URL renders anywhere without a token; writes are RLS-scoped to the user's folder.
 */
class AvatarRemote(
    private val httpClient: HttpClient,
    private val sessionManager: SessionManager,
    private val baseUrl: String = SupabaseConfig.URL,
    private val apiKey: String = SupabaseConfig.PUBLISHABLE_KEY,
) {
    private val json = Json { ignoreUnknownKeys = true }

    /** Uploads JPEG [bytes] and returns the public URL. A fresh filename busts image caches. */
    suspend fun upload(bytes: ByteArray): Result<String> = runCatching {
        val token = sessionManager.validAccessToken() ?: throw AuthException("Нужно войти заново")
        val userId =
            (sessionManager.authState.value as? AuthState.SignedIn)?.session?.userId
                ?: throw AuthException("Нет активной сессии")
        val path = "$userId/avatar_${System.currentTimeMillis()}.jpg"
        val response =
            httpClient.post("$baseUrl/storage/v1/object/avatars/$path") {
                header("apikey", apiKey)
                header("Authorization", "Bearer $token")
                contentType(ContentType.Image.JPEG)
                setBody(bytes)
            }
        if (!response.status.isSuccess()) throw storageError(response)
        "$baseUrl/storage/v1/object/public/avatars/$path"
    }.mapNetworkError()

    /** Points the signed-in member's row(s) at [url] (RLS: members_update_self). */
    suspend fun setMemberAvatarUrl(url: String): Result<Unit> = runCatching {
        val token = sessionManager.validAccessToken() ?: throw AuthException("Нужно войти заново")
        val userId =
            (sessionManager.authState.value as? AuthState.SignedIn)?.session?.userId
                ?: throw AuthException("Нет активной сессии")
        val response =
            httpClient.patch("$baseUrl/rest/v1/family_members") {
                header("apikey", apiKey)
                header("Authorization", "Bearer $token")
                header("Prefer", "return=minimal")
                contentType(ContentType.Application.Json)
                url { parameters.append("user_id", "eq.$userId") }
                setBody("""{"avatar_url":"$url"}""")
            }
        if (!response.status.isSuccess()) throw storageError(response)
    }.mapNetworkError()

    /** Storage / PostgREST error body: `{"statusCode":"400","error":"...","message":"..."}`. */
    @Serializable
    private data class ServerError(val statusCode: String? = null, val error: String? = null, val message: String? = null)

    /**
     * Turns the server's answer into a sentence the parent can act on. Storage reports an RLS
     * refusal as HTTP 400 with the reason only in the body, so the body is what we read.
     */
    private suspend fun storageError(response: HttpResponse): Exception {
        val text = runCatching { response.bodyAsText() }.getOrNull().orEmpty()
        val server = runCatching { json.decodeFromString<ServerError>(text) }.getOrNull()
        val reason = server?.message ?: server?.error
        val message =
            when {
                text.contains("row-level security", ignoreCase = true) ||
                    text.contains("AccessDenied") ||
                    response.status == HttpStatusCode.Forbidden -> "Нет прав на загрузку фото — выйдите и войдите снова"
                text.contains("exceeded the maximum allowed size", ignoreCase = true) ||
                    response.status == HttpStatusCode.PayloadTooLarge -> "Фото слишком большое (лимит 2 МБ)"
                text.contains("mime type", ignoreCase = true) -> "Неподдерживаемый формат фото"
                text.contains("Bucket not found", ignoreCase = true) -> "Хранилище фото не настроено на сервере"
                response.status == HttpStatusCode.Unauthorized -> "Сессия истекла — войдите заново"
                !reason.isNullOrBlank() -> "Не удалось загрузить фото: $reason"
                else -> "Не удалось загрузить фото (${response.status.value})"
            }
        return AuthException(message, status = response.status.value)
    }

    private fun <T> Result<T>.mapNetworkError(): Result<T> = recoverCatching { throwable ->
        throw if (throwable is AuthException) throwable else AuthException("Нет соединения с сервером")
    }
}
