package app.kite.core.diagnostics

import android.os.Build
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
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant

/**
 * One crash, as the family sees it. [authorName] and [deviceModel] are written at upload time
 * and kept even if the member is later removed: a report that cannot say where it came from is
 * not worth reading (owner, 08.09.2026).
 */
@Serializable
data class CrashReport(
    val id: String,
    @SerialName("member_id") val memberId: String? = null,
    val app: String,
    @SerialName("author_name") val authorName: String? = null,
    @SerialName("device_model") val deviceModel: String? = null,
    @SerialName("os_version") val osVersion: String? = null,
    @SerialName("version_name") val versionName: String? = null,
    @SerialName("happened_at") val happenedAt: String,
    val report: String,
) {
    val isChild: Boolean get() = app == APP_CHILD

    /** «Мама · HUAWEI JNY-LX1», falling back to whichever half is known. */
    val source: String
        get() = listOfNotNull(authorName?.ifBlank { null }, deviceModel?.ifBlank { null }).joinToString(" · ")
            .ifBlank { if (isChild) "Телефон ребёнка" else "Телефон родителя" }

    companion object {
        const val APP_PARENT = "parent"
        const val APP_CHILD = "child"
    }
}

/** `crash_reports` over PostgREST: a device files its own, the whole family reads them all. */
class CrashReportsRemote(
    private val httpClient: HttpClient,
    private val json: Json,
    private val sessionManager: SessionManager,
    private val baseUrl: String = SupabaseConfig.URL,
    private val apiKey: String = SupabaseConfig.PUBLISHABLE_KEY,
) {
    private val restUrl get() = "$baseUrl/rest/v1"

    suspend fun upload(
        familyId: String,
        memberId: String?,
        app: String,
        authorName: String?,
        versionName: String,
        happenedAtMs: Long,
        report: String,
    ): Result<Unit> = runCatching {
        val body =
            buildJsonObject {
                put("family_id", familyId)
                put("member_id", memberId)
                put("actor", sessionUserId())
                put("app", app)
                put("author_name", authorName)
                put("device_model", "${Build.MANUFACTURER} ${Build.MODEL}")
                put("os_version", "Android ${Build.VERSION.RELEASE}")
                put("version_name", versionName)
                put("happened_at", Instant.ofEpochMilli(happenedAtMs).toString())
                put("report", report.take(MAX_REPORT_CHARS))
            }
        val response =
            httpClient.post("$restUrl/crash_reports") {
                authHeaders(requireSession())
                // The same stored crash may be offered again after a failed upload.
                header("Prefer", "resolution=ignore-duplicates,return=minimal")
                setBody(body)
            }
        if (!response.status.isSuccess()) throw restError(response)
    }.mapNetworkError()

    /** Everything the family filed, newest crash first. */
    suspend fun list(familyId: String, limit: Int = LIST_LIMIT): Result<List<CrashReport>> = runCatching {
        val response =
            httpClient.get("$restUrl/crash_reports") {
                authHeaders(requireSession())
                parameter("family_id", "eq.$familyId")
                parameter("order", "happened_at.desc")
                parameter("limit", limit.toString())
                parameter("select", SELECT)
            }
        if (!response.status.isSuccess()) throw restError(response)
        json.decodeFromString<List<CrashReport>>(response.bodyAsText())
    }.mapNetworkError()

    private suspend fun requireSession(): String = sessionManager.validAccessToken() ?: throw AuthException("Нужно войти заново")

    private fun sessionUserId(): String = (sessionManager.authState.value as? AuthState.SignedIn)?.session?.userId
        ?: throw AuthException("Нет активной сессии")

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

    private companion object {
        const val SELECT = "id,member_id,app,author_name,device_model,os_version,version_name,happened_at,report"
        const val LIST_LIMIT = 50

        /** Matches the column's check constraint; the head of a trace is what gets read anyway. */
        const val MAX_REPORT_CHARS = 20_000
    }
}
