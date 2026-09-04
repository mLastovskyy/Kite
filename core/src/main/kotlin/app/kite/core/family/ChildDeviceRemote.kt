package app.kite.core.family

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

@Serializable
data class ChildDevice(
    @SerialName("member_id") val memberId: String,
    @SerialName("family_id") val familyId: String,
    val platform: String = "android",
    val services: String? = null,
    val model: String? = null,
    @SerialName("os_version") val osVersion: String? = null,
    @SerialName("app_version_code") val appVersionCode: Int? = null,
    @SerialName("protection_missing") val protectionMissing: List<String> = emptyList(),
    @SerialName("last_seen_at") val lastSeenAt: String? = null,
) {
    val isHealthy: Boolean get() = protectionMissing.isEmpty()
}

class ChildDeviceRemote(
    private val httpClient: HttpClient,
    private val json: Json,
    private val sessionManager: SessionManager,
    private val baseUrl: String = SupabaseConfig.URL,
    private val apiKey: String = SupabaseConfig.PUBLISHABLE_KEY,
) {
    private val restUrl get() = "$baseUrl/rest/v1"

    suspend fun report(device: ChildDevice): Result<Unit> = runCatching {
        val response =
            httpClient.post("$restUrl/devices") {
                authHeaders(requireSession())
                parameter("on_conflict", "member_id")
                header("Prefer", "resolution=merge-duplicates,return=minimal")
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(listOf(device)))
            }
        if (!response.status.isSuccess()) throw restError(response)
    }.mapNetworkError()

    suspend fun forFamily(familyId: String): Result<List<ChildDevice>> = runCatching {
        val response =
            httpClient.get("$restUrl/devices") {
                authHeaders(requireSession())
                parameter("family_id", "eq.$familyId")
                parameter("select", SELECT)
            }
        if (!response.status.isSuccess()) throw restError(response)
        json.decodeFromString<List<ChildDevice>>(response.bodyAsText())
    }.mapNetworkError()

    suspend fun forChild(memberId: String): Result<ChildDevice?> = runCatching {
        val response =
            httpClient.get("$restUrl/devices") {
                authHeaders(requireSession())
                parameter("member_id", "eq.$memberId")
                parameter("select", SELECT)
            }
        if (!response.status.isSuccess()) throw restError(response)
        json.decodeFromString<List<ChildDevice>>(response.bodyAsText()).firstOrNull()
    }.mapNetworkError()

    suspend fun forget(memberId: String): Result<Unit> = runCatching {
        val response =
            httpClient.delete("$restUrl/devices") {
                authHeaders(requireSession())
                parameter("member_id", "eq.$memberId")
            }
        if (!response.status.isSuccess()) throw restError(response)
    }.mapNetworkError()

    private fun io.ktor.client.request.HttpRequestBuilder.authHeaders(token: String) {
        header("apikey", apiKey)
        header("Authorization", "Bearer $token")
    }

    private suspend fun requireSession(): String = sessionManager.validAccessToken() ?: throw AuthException("Нужно войти заново")

    private suspend fun restError(response: HttpResponse): Exception =
        AuthException("Ошибка сервера (${response.status.value}): ${response.bodyAsText().take(200)}")

    private fun <T> Result<T>.mapNetworkError(): Result<T> = recoverCatching { throwable ->
        throw if (throwable is AuthException) throwable else AuthException("Нет соединения с сервером")
    }

    private companion object {
        const val SELECT = "member_id,family_id,platform,services,model,os_version,app_version_code,protection_missing,last_seen_at"
    }
}
