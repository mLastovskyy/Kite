package app.kite.core.location

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

/** One thinned trail point («Маршруты»). */
@Serializable
data class TrailPoint(
    @SerialName("member_id") val memberId: String,
    @SerialName("family_id") val familyId: String,
    val latitude: Double,
    val longitude: Double,
    @SerialName("accuracy_m") val accuracyM: Float? = null,
    @SerialName("recorded_at") val recordedAt: String,
)

/**
 * `location_trail` over PostgREST. The child uploads a THINNED trail (points at least
 * [MIN_GAP_MS] and [MIN_GAP_METERS] apart — the day's movement, not telemetry; raw fixes stay
 * in Room per CLAUDE.md), idempotently by (member_id, recorded_at). The parent reads one day
 * at a time. Retention is 7 days.
 */
class TrailRemote(
    private val httpClient: HttpClient,
    private val json: Json,
    private val sessionManager: SessionManager,
    private val baseUrl: String = SupabaseConfig.URL,
    private val apiKey: String = SupabaseConfig.PUBLISHABLE_KEY,
) {
    private val restUrl get() = "$baseUrl/rest/v1"

    /** Child: upload a batch; duplicates (same member + timestamp) are ignored. */
    suspend fun upload(points: List<TrailPoint>): Result<Unit> = runCatching {
        if (points.isEmpty()) return@runCatching
        val response =
            httpClient.post("$restUrl/location_trail") {
                authHeaders(requireSession())
                parameter("on_conflict", "member_id,recorded_at")
                header("Prefer", "resolution=ignore-duplicates,return=minimal")
                setBody(json.encodeToString(points))
            }
        if (!response.status.isSuccess()) throw restError(response)
    }.mapNetworkError()

    /** Parent: the trail between two ISO instants, oldest first. */
    suspend fun between(memberId: String, fromIso: String, toIso: String): Result<List<TrailPoint>> = runCatching {
        val response =
            httpClient.get("$restUrl/location_trail") {
                authHeaders(requireSession())
                parameter("member_id", "eq.$memberId")
                parameter("recorded_at", "gte.$fromIso")
                parameter("and", "(recorded_at.lt.$toIso)")
                parameter("order", "recorded_at.asc")
                parameter("limit", "2000")
                parameter("select", "member_id,family_id,latitude,longitude,accuracy_m,recorded_at")
            }
        if (!response.status.isSuccess()) throw restError(response)
        json.decodeFromString<List<TrailPoint>>(response.bodyAsText())
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

    companion object {
        /** Thinning thresholds the child applies before uploading. */
        const val MIN_GAP_MS = 5 * 60 * 1000L
        const val MIN_GAP_METERS = 50.0
        const val RETENTION_DAYS = 7
    }
}
