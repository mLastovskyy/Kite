package app.kite.core.rules

import app.kite.core.auth.AuthException
import app.kite.core.auth.AuthState
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

@Serializable
data class MemberRulesRow(
    @SerialName("member_id") val memberId: String,
    @SerialName("family_id") val familyId: String,
    val rules: ChildRules = ChildRules(),
    @SerialName("updated_by") val updatedBy: String? = null,
)

/**
 * member_rules over PostgREST: the parent upserts one jsonb document per child, the child
 * fetches its own copy to cache and enforce offline. RLS restricts writes to family
 * parents and reads to family members.
 */
const val ACTION_RULES = "rules"

class RulesRemote(
    private val httpClient: HttpClient,
    private val json: Json,
    private val sessionManager: SessionManager,
    private val baseUrl: String = SupabaseConfig.URL,
    private val apiKey: String = SupabaseConfig.PUBLISHABLE_KEY,
) {
    private val restUrl get() = "$baseUrl/rest/v1"

    /** The stored row, including who last changed it — the child names that parent on the block screen. */
    suspend fun fetchRow(memberId: String): Result<MemberRulesRow?> = runCatching {
        val response =
            httpClient.get("$restUrl/member_rules") {
                authHeaders(requireSession())
                parameter("member_id", "eq.$memberId")
                parameter("select", "member_id,family_id,rules,updated_by")
            }
        if (!response.status.isSuccess()) throw restError(response)
        json.decodeFromString<List<MemberRulesRow>>(response.bodyAsText()).firstOrNull()
    }.mapNetworkError()

    /** null = no rules saved for this member yet. */
    suspend fun fetch(memberId: String): Result<ChildRules?> = fetchRow(memberId).map { it?.rules }

    suspend fun upsert(memberId: String, familyId: String, rules: ChildRules): Result<Unit> = runCatching {
        val row = MemberRulesRow(memberId = memberId, familyId = familyId, rules = rules, updatedBy = sessionUserId())
        val response =
            httpClient.post("$restUrl/member_rules") {
                authHeaders(requireSession())
                header("Prefer", "resolution=merge-duplicates,return=minimal")
                setBody(json.encodeToString(listOf(row)))
            }
        if (!response.status.isSuccess()) throw restError(response)
        notifyChild(memberId)
    }.mapNetworkError()

    private suspend fun notifyChild(memberId: String) {
        runCatching {
            httpClient.post("$baseUrl/functions/v1/send-push") {
                authHeaders(requireSession())
                setBody("""{"member_id":"$memberId","data":{"action":"$ACTION_RULES"}}""")
            }
        }
    }

    private fun sessionUserId(): String? = (sessionManager.authState.value as? AuthState.SignedIn)?.session?.userId

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
