package app.kite.core.approval

import app.kite.core.auth.AuthException
import app.kite.core.auth.SessionManager
import app.kite.core.config.SupabaseConfig
import io.ktor.client.HttpClient
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
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Extra screen time that was actually handed out: how much, when, by whom and why. */
@Serializable
data class TimeGrant(
    val id: String,
    @SerialName("child_member_id") val childMemberId: String,
    @SerialName("granted_by") val grantedBy: String? = null,
    val minutes: Int,
    @SerialName("package_name") val packageName: String? = null,
    val source: String = SOURCE_REQUEST,
    @SerialName("created_at") val createdAt: String? = null,
) {
    companion object {
        const val SOURCE_REQUEST = "request"
        const val SOURCE_TASK = "task"
        const val SOURCE_MANUAL = "manual"
        const val SOURCE_OFFLINE_CODE = "offline_code"
    }
}

/**
 * `time_grants` over PostgREST: the log behind «Добавленное время». Writing a row is never
 * allowed to fail a grant — the minutes have already reached the child by then — so [record]
 * reports its failure and nothing else.
 */
class TimeGrantsRemote(
    private val httpClient: HttpClient,
    private val json: Json,
    private val sessionManager: SessionManager,
    private val baseUrl: String = SupabaseConfig.URL,
    private val apiKey: String = SupabaseConfig.PUBLISHABLE_KEY,
) {
    private val restUrl get() = "$baseUrl/rest/v1"

    suspend fun record(
        familyId: String,
        childMemberId: String,
        minutes: Int,
        grantedBy: String?,
        packageName: String? = null,
        source: String = TimeGrant.SOURCE_REQUEST,
    ): Result<Unit> = runCatching {
        val body =
            buildJsonObject {
                put("family_id", familyId)
                put("child_member_id", childMemberId)
                put("minutes", minutes)
                put("source", source)
                put("granted_by", grantedBy?.let(::JsonPrimitive) ?: JsonPrimitive(null as String?))
                put("package_name", packageName?.let(::JsonPrimitive) ?: JsonPrimitive(null as String?))
            }
        val response =
            httpClient.post("$restUrl/time_grants") {
                authHeaders(requireSession())
                header("Prefer", "return=minimal")
                setBody(body)
            }
        if (!response.status.isSuccess()) throw restError(response)
    }.mapNetworkError()

    /** Newest grants for one child, for the history list. */
    suspend fun forChild(childMemberId: String, limit: Int = 50): Result<List<TimeGrant>> = runCatching {
        val response =
            httpClient.get("$restUrl/time_grants") {
                authHeaders(requireSession())
                parameter("child_member_id", "eq.$childMemberId")
                parameter("order", "created_at.desc")
                parameter("limit", limit.toString())
                parameter("select", "id,child_member_id,granted_by,minutes,package_name,source,created_at")
            }
        if (!response.status.isSuccess()) throw restError(response)
        json.decodeFromString<List<TimeGrant>>(response.bodyAsText())
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
