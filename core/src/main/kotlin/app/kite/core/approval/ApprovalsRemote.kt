package app.kite.core.approval

import app.kite.core.auth.AuthException
import app.kite.core.auth.SessionManager
import app.kite.core.config.SupabaseConfig
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/** A child's over-the-network request the parent approves or denies. */
@Serializable
data class ApprovalRequest(
    val id: String,
    @SerialName("family_id") val familyId: String,
    @SerialName("child_member_id") val childMemberId: String,
    val type: String,
    val status: String = STATUS_PENDING,
    val payload: JsonObject = JsonObject(emptyMap()),
    @SerialName("created_at") val createdAt: String? = null,
) {
    val minutes: Int? get() = runCatching { payload["minutes"]?.jsonPrimitive?.content?.toInt() }.getOrNull()

    /** For an extra_time request scoped to one app: its package + label (null = all apps). */
    val packageName: String? get() = runCatching { payload["package"]?.jsonPrimitive?.content }.getOrNull()
    val appLabel: String? get() = runCatching { payload["label"]?.jsonPrimitive?.content }.getOrNull()

    companion object {
        const val TYPE_UNLOCK = "unlock"
        const val TYPE_EXTRA_TIME = "extra_time"
        const val TYPE_REMOVAL = "uninstall"

        /** Child asks the parent for a task to earn time (Kids360 «Попросить задание»). */
        const val TYPE_TASK_REQUEST = "task_request"
        const val STATUS_PENDING = "pending"
        const val STATUS_APPROVED = "approved"

        // The server's check constraint spells it `denied` (pending/approved/denied/expired).
        const val STATUS_REJECTED = "denied"
    }
}

/**
 * approval_requests over PostgREST. Child creates (RLS: own member); family reads; parent
 * resolves (RLS: parent). The approval's EFFECT is delivered separately as a device_command
 * so it reaches the child instantly (Realtime + push) — this class only tracks the request.
 */
class ApprovalsRemote(
    private val httpClient: HttpClient,
    private val json: Json,
    private val sessionManager: SessionManager,
    private val baseUrl: String = SupabaseConfig.URL,
    private val apiKey: String = SupabaseConfig.PUBLISHABLE_KEY,
) {
    private val restUrl get() = "$baseUrl/rest/v1"

    /** Child: create a request. [payloadJson] is a raw JSON object, e.g. {"minutes":15}. */
    suspend fun create(childMemberId: String, familyId: String, type: String, payloadJson: String? = null): Result<Unit> = runCatching {
        val body =
            buildString {
                append("{\"child_member_id\":\"").append(childMemberId).append('"')
                append(",\"family_id\":\"").append(familyId).append('"')
                append(",\"type\":\"").append(type).append('"')
                if (payloadJson != null) append(",\"payload\":").append(payloadJson)
                append('}')
            }
        val response =
            httpClient.post("$restUrl/approval_requests") {
                authHeaders(requireSession())
                header("Prefer", "return=minimal")
                setBody(body)
            }
        if (!response.status.isSuccess()) throw restError(response)
    }.mapNetworkError()

    /** Parent: pending requests in the family, newest first. */
    suspend fun pending(familyId: String): Result<List<ApprovalRequest>> = runCatching {
        val response =
            httpClient.get("$restUrl/approval_requests") {
                authHeaders(requireSession())
                parameter("family_id", "eq.$familyId")
                parameter("status", "eq.pending")
                parameter("order", "created_at.desc")
                parameter("select", "id,family_id,child_member_id,type,status,payload,created_at")
            }
        if (!response.status.isSuccess()) throw restError(response)
        json.decodeFromString<List<ApprovalRequest>>(response.bodyAsText())
    }.mapNetworkError()

    /** Parent: resolve a request (approved/rejected). */
    suspend fun resolve(id: String, status: String): Result<Unit> = runCatching {
        val response =
            httpClient.patch("$restUrl/approval_requests") {
                authHeaders(requireSession())
                parameter("id", "eq.$id")
                header("Prefer", "return=minimal")
                setBody("""{"status":"$status","resolved_at":"now"}""")
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
