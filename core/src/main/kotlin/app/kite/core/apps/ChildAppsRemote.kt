package app.kite.core.apps

import app.kite.core.auth.AuthException
import app.kite.core.auth.SessionManager
import app.kite.core.config.SupabaseConfig
import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** One launchable app installed on the child's phone, as the child device reported it. */
@Serializable
data class ChildApp(
    @SerialName("member_id") val memberId: String,
    @SerialName("family_id") val familyId: String,
    @SerialName("package_name") val packageName: String,
    val label: String,
    @SerialName("is_system") val isSystem: Boolean = false,
    @SerialName("updated_at") val updatedAt: String? = null,
)

/**
 * `child_apps` over PostgREST: the child's installed-app inventory. The child device
 * [replaceAll]s its list (upsert + delete of what disappeared); the parent reads it with
 * [forChild] to offer every app for a toggle or a limit before it is ever opened. Package
 * name + label + system flag only — no icons, no usage (that is `usage_apps`).
 */
class ChildAppsRemote(
    private val httpClient: HttpClient,
    private val json: Json,
    private val sessionManager: SessionManager,
    private val baseUrl: String = SupabaseConfig.URL,
    private val apiKey: String = SupabaseConfig.PUBLISHABLE_KEY,
) {
    private val restUrl get() = "$baseUrl/rest/v1"

    /** Parent: every app the child's phone reported, sorted by label. */
    suspend fun forChild(memberId: String): Result<List<ChildApp>> = runCatching {
        val response =
            httpClient.get("$restUrl/child_apps") {
                authHeaders(requireSession())
                parameter("member_id", "eq.$memberId")
                parameter("order", "label.asc")
                parameter("limit", "1000")
                parameter("select", "member_id,family_id,package_name,label,is_system,updated_at")
            }
        if (!response.status.isSuccess()) throw restError(response)
        json.decodeFromString<List<ChildApp>>(response.bodyAsText())
    }.mapNetworkError()

    /**
     * Child: make the server list equal to [apps] — upsert everything, then delete rows for
     * packages no longer present. Idempotent; safe to re-run after an offline gap.
     */
    suspend fun replaceAll(memberId: String, familyId: String, apps: List<ChildApp>): Result<Unit> = runCatching {
        if (apps.isNotEmpty()) {
            val response =
                httpClient.post("$restUrl/child_apps") {
                    authHeaders(requireSession())
                    parameter("on_conflict", "member_id,package_name")
                    header("Prefer", "resolution=merge-duplicates,return=minimal")
                    setBody(json.encodeToString(apps.map { it.copy(memberId = memberId, familyId = familyId, updatedAt = null) }))
                }
            if (!response.status.isSuccess()) throw restError(response)
        }
        val keep = apps.map { it.packageName }
        val response =
            httpClient.delete("$restUrl/child_apps") {
                authHeaders(requireSession())
                parameter("member_id", "eq.$memberId")
                // PostgREST `not.in.(a,b)`; package names contain only [A-Za-z0-9._], no quoting needed.
                if (keep.isNotEmpty()) parameter("package_name", "not.in.(${keep.joinToString(",")})")
                header("Prefer", "return=minimal")
            }
        if (!response.status.isSuccess()) throw restError(response)
    }.mapNetworkError()

    private suspend fun requireSession(): String = sessionManager.validAccessToken() ?: throw AuthException("Нужно войти заново")

    private fun io.ktor.client.request.HttpRequestBuilder.authHeaders(accessToken: String) {
        header("apikey", apiKey)
        header("Authorization", "Bearer $accessToken")
        contentType(ContentType.Application.Json)
    }

    private suspend fun restError(response: HttpResponse): Exception {
        runCatching { response.bodyAsText() }
        return AuthException("Ошибка сервера (${response.status.value})")
    }

    private fun <T> Result<T>.mapNetworkError(): Result<T> = recoverCatching { throwable ->
        throw if (throwable is AuthException) throwable else AuthException("Нет соединения с сервером")
    }
}
