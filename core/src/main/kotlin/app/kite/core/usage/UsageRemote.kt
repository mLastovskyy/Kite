package app.kite.core.usage

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

/** One synced day per child: the day total plus a 24-slot hourly histogram (jsonb). */
@Serializable
data class UsageDayRow(
    @SerialName("member_id") val memberId: String,
    @SerialName("family_id") val familyId: String,
    val day: String,
    @SerialName("total_ms") val totalMs: Long,
    @SerialName("hourly_ms") val hourlyMs: List<Long>,
)

/** Per-app day total; [appLabel] is resolved on the child device where the app exists. */
@Serializable
data class UsageAppRow(
    @SerialName("member_id") val memberId: String,
    @SerialName("family_id") val familyId: String,
    val day: String,
    @SerialName("package_name") val packageName: String,
    @SerialName("app_label") val appLabel: String = "",
    @SerialName("foreground_ms") val foregroundMs: Long,
)

/**
 * Daily-aggregate sync against usage_days/usage_apps (PostgREST). The child upserts its
 * own rows (RLS-checked), any family member reads. ONLY aggregates travel — raw usage_hour
 * telemetry never leaves the device (CLAUDE.md).
 */
class UsageRemote(
    private val httpClient: HttpClient,
    private val json: Json,
    private val sessionManager: SessionManager,
    private val baseUrl: String = SupabaseConfig.URL,
    private val apiKey: String = SupabaseConfig.PUBLISHABLE_KEY,
) {
    private val restUrl get() = "$baseUrl/rest/v1"

    suspend fun upsertDays(rows: List<UsageDayRow>): Result<Unit> = upsert("usage_days", json.encodeToString(rows))

    suspend fun upsertApps(rows: List<UsageAppRow>): Result<Unit> = upsert("usage_apps", json.encodeToString(rows))

    suspend fun days(memberId: String, fromDay: String, toDay: String): Result<List<UsageDayRow>> = runCatching {
        val response =
            httpClient.get("$restUrl/usage_days") {
                authHeaders(requireSession())
                parameter("member_id", "eq.$memberId")
                // Repeated filters on one column are ANDed by PostgREST.
                parameter("day", "gte.$fromDay")
                parameter("day", "lte.$toDay")
                parameter("order", "day.asc")
            }
        if (!response.status.isSuccess()) throw restError(response)
        json.decodeFromString<List<UsageDayRow>>(response.bodyAsText())
    }.mapNetworkError()

    suspend fun apps(memberId: String, fromDay: String, toDay: String): Result<List<UsageAppRow>> = runCatching {
        val response =
            httpClient.get("$restUrl/usage_apps") {
                authHeaders(requireSession())
                parameter("member_id", "eq.$memberId")
                parameter("day", "gte.$fromDay")
                parameter("day", "lte.$toDay")
                parameter("order", "foreground_ms.desc")
            }
        if (!response.status.isSuccess()) throw restError(response)
        json.decodeFromString<List<UsageAppRow>>(response.bodyAsText())
    }.mapNetworkError()

    // ── internals ────────────────────────────────────────────────────────────
    private suspend fun upsert(table: String, body: String): Result<Unit> = runCatching {
        val response =
            httpClient.post("$restUrl/$table") {
                authHeaders(requireSession())
                // Merge on the primary key: re-syncing the same day is idempotent.
                header("Prefer", "resolution=merge-duplicates,return=minimal")
                setBody(body)
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
        runCatching { response.bodyAsText() } // drain the body; details are not user-facing
        return AuthException("Ошибка сервера (${response.status.value})")
    }

    private fun <T> Result<T>.mapNetworkError(): Result<T> = recoverCatching { throwable ->
        throw if (throwable is AuthException) throwable else AuthException("Нет соединения с сервером")
    }
}
