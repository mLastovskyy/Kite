package app.kite.core.tasks

import app.kite.core.auth.AuthException
import app.kite.core.auth.AuthState
import app.kite.core.auth.SessionManager
import app.kite.core.config.SupabaseConfig
import io.ktor.client.HttpClient
import io.ktor.client.request.delete
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * `tasks` over PostgREST. Parents create/resolve/delete (RLS: family parent); the child
 * reads its own and may only flip an open task to `done` (RLS: own member, open → done).
 * Granting the reward is NOT done here — the parent app sends a grant_time device command
 * after confirming, exactly like an approved extra-time request, so the child's bonus
 * arrives instantly and works with the existing offline bonus store.
 */
class TasksRemote(
    private val httpClient: HttpClient,
    private val json: Json,
    private val sessionManager: SessionManager,
    private val baseUrl: String = SupabaseConfig.URL,
    private val apiKey: String = SupabaseConfig.PUBLISHABLE_KEY,
) {
    private val restUrl get() = "$baseUrl/rest/v1"

    /** Parent: create a task for one child. [repeatDays] are ISO weekdays 1..7; empty = one-time. */
    suspend fun create(familyId: String, childMemberId: String, title: String, rewardMinutes: Int, repeatDays: Set<Int>): Result<Unit> =
        runCatching {
            val body =
                buildJsonObject {
                    put("family_id", familyId)
                    put("child_member_id", childMemberId)
                    put("created_by", sessionUserId())
                    put("title", title.trim().take(ChildTask.MAX_TITLE))
                    put("reward_minutes", rewardMinutes.coerceIn(ChildTask.MIN_REWARD, ChildTask.MAX_REWARD))
                    put("repeat_days", JsonArray(repeatDays.filter { it in 1..7 }.sorted().map(::JsonPrimitive)))
                }
            val response =
                httpClient.post("$restUrl/tasks") {
                    authHeaders(requireSession())
                    header("Prefer", "return=minimal")
                    setBody(body)
                }
            if (!response.status.isSuccess()) throw restError(response)
        }.mapNetworkError()

    /** Parent: edit title, reward and recurrence of an existing task. */
    suspend fun update(taskId: String, title: String, rewardMinutes: Int, repeatDays: Set<Int>): Result<Unit> = patch(
        taskId,
        buildJsonObject {
            put("title", title.trim().take(ChildTask.MAX_TITLE))
            put("reward_minutes", rewardMinutes.coerceIn(ChildTask.MIN_REWARD, ChildTask.MAX_REWARD))
            put("repeat_days", JsonArray(repeatDays.filter { it in 1..7 }.sorted().map(::JsonPrimitive)))
        },
    )

    /** All tasks of a family (parent view), newest first. [childMemberId] narrows to one child. */
    suspend fun list(familyId: String, childMemberId: String? = null): Result<List<ChildTask>> = runCatching {
        val response =
            httpClient.get("$restUrl/tasks") {
                authHeaders(requireSession())
                parameter("family_id", "eq.$familyId")
                if (childMemberId != null) parameter("child_member_id", "eq.$childMemberId")
                parameter("order", "created_at.desc")
                parameter("select", SELECT)
            }
        if (!response.status.isSuccess()) throw restError(response)
        json.decodeFromString<List<ChildTask>>(response.bodyAsText())
    }.mapNetworkError()

    /** Child: its own tasks that still matter — open and awaiting confirmation. */
    suspend fun activeFor(childMemberId: String): Result<List<ChildTask>> = runCatching {
        val response =
            httpClient.get("$restUrl/tasks") {
                authHeaders(requireSession())
                parameter("child_member_id", "eq.$childMemberId")
                parameter("status", "in.(${ChildTask.STATUS_OPEN},${ChildTask.STATUS_DONE})")
                parameter("order", "created_at.asc")
                parameter("select", SELECT)
            }
        if (!response.status.isSuccess()) throw restError(response)
        json.decodeFromString<List<ChildTask>>(response.bodyAsText())
    }.mapNetworkError()

    /** Child: «Выполнил». RLS lets this through only for an open task of its own. */
    suspend fun markDone(taskId: String): Result<Unit> =
        patch(taskId, JsonObject(mapOf("status" to JsonPrimitive(ChildTask.STATUS_DONE), "done_at" to JsonPrimitive("now"))))

    /** Parent: confirm (caller then grants the minutes) or reject (task reopens). */
    suspend fun resolve(taskId: String, confirmed: Boolean): Result<Unit> = patch(
        taskId,
        buildJsonObject {
            put("status", if (confirmed) ChildTask.STATUS_CONFIRMED else ChildTask.STATUS_OPEN)
            put("resolved_at", "now")
            put("resolved_by", sessionUserId())
            if (!confirmed) put("done_at", null as String?)
        },
    )

    /** Parent: delete a task. */
    suspend fun delete(taskId: String): Result<Unit> = runCatching {
        val response =
            httpClient.delete("$restUrl/tasks") {
                authHeaders(requireSession())
                parameter("id", "eq.$taskId")
                header("Prefer", "return=minimal")
            }
        if (!response.status.isSuccess()) throw restError(response)
    }.mapNetworkError()

    private suspend fun patch(taskId: String, body: JsonObject): Result<Unit> = runCatching {
        val response =
            httpClient.patch("$restUrl/tasks") {
                authHeaders(requireSession())
                parameter("id", "eq.$taskId")
                header("Prefer", "return=minimal")
                setBody(body)
            }
        if (!response.status.isSuccess()) throw restError(response)
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
        const val SELECT = "id,family_id,child_member_id,title,reward_minutes,status,repeat_days,created_at,done_at"
    }
}
