package app.kite.core.location

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
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** A named circle the parent saved for one child («Дом», «Школа», …). */
@Serializable
data class Place(
    val id: String,
    @SerialName("family_id") val familyId: String,
    @SerialName("child_member_id") val childMemberId: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    @SerialName("radius_m") val radiusM: Int = 150,
    @SerialName("notify_enter") val notifyEnter: Boolean = true,
    @SerialName("notify_exit") val notifyExit: Boolean = true,
) {
    companion object {
        const val MIN_RADIUS = 50
        const val MAX_RADIUS = 2000
        const val DEFAULT_RADIUS = 150
        const val MAX_NAME = 40

        /** Suggestion chips for the name field. */
        val NAME_SUGGESTIONS = listOf("Дом", "Школа", "Бабушка", "Секция", "Тренировка")
    }
}

/** The child device entered or left a place. */
@Serializable
data class PlaceEvent(
    val id: String,
    @SerialName("child_member_id") val childMemberId: String,
    @SerialName("place_id") val placeId: String,
    val kind: String,
    val at: String,
) {
    val isEnter: Boolean get() = kind == KIND_ENTER

    companion object {
        const val KIND_ENTER = "enter"
        const val KIND_EXIT = "exit"
    }
}

/**
 * `places` and `place_events` over PostgREST. Parents create/edit/delete places (RLS); the
 * child device reads its own places, evaluates enter/exit locally on every fix and inserts
 * events (RLS: own member). Saving a place pokes the child with a silent push so it refreshes
 * its cached list right away instead of at the next periodic pull.
 */
class PlacesRemote(
    private val httpClient: HttpClient,
    private val json: Json,
    private val sessionManager: SessionManager,
    private val baseUrl: String = SupabaseConfig.URL,
    private val apiKey: String = SupabaseConfig.PUBLISHABLE_KEY,
) {
    private val restUrl get() = "$baseUrl/rest/v1"

    /** Places of one child (parent and child both use this). */
    suspend fun forChild(childMemberId: String): Result<List<Place>> = runCatching {
        val response =
            httpClient.get("$restUrl/places") {
                authHeaders(requireSession())
                parameter("child_member_id", "eq.$childMemberId")
                parameter("order", "created_at.asc")
                parameter("select", PLACE_SELECT)
            }
        if (!response.status.isSuccess()) throw restError(response)
        json.decodeFromString<List<Place>>(response.bodyAsText())
    }.mapNetworkError()

    /** Parent: create a place; the child device is poked to refresh. */
    suspend fun create(
        familyId: String,
        childMemberId: String,
        name: String,
        latitude: Double,
        longitude: Double,
        radiusM: Int,
        notifyEnter: Boolean,
        notifyExit: Boolean,
    ): Result<Unit> = runCatching {
        val body =
            buildJsonObject {
                put("family_id", familyId)
                put("child_member_id", childMemberId)
                put("created_by", sessionUserId())
                put("name", name.trim().take(Place.MAX_NAME))
                put("latitude", latitude)
                put("longitude", longitude)
                put("radius_m", radiusM.coerceIn(Place.MIN_RADIUS, Place.MAX_RADIUS))
                put("notify_enter", notifyEnter)
                put("notify_exit", notifyExit)
            }
        val response =
            httpClient.post("$restUrl/places") {
                authHeaders(requireSession())
                header("Prefer", "return=minimal")
                setBody(body)
            }
        if (!response.status.isSuccess()) throw restError(response)
        pokeChild(childMemberId)
    }.mapNetworkError()

    /** Parent: edit name / radius / notification switches. */
    suspend fun update(place: Place): Result<Unit> = runCatching {
        val body =
            buildJsonObject {
                put("name", place.name.trim().take(Place.MAX_NAME))
                put("latitude", place.latitude)
                put("longitude", place.longitude)
                put("radius_m", place.radiusM.coerceIn(Place.MIN_RADIUS, Place.MAX_RADIUS))
                put("notify_enter", place.notifyEnter)
                put("notify_exit", place.notifyExit)
            }
        val response =
            httpClient.patch("$restUrl/places") {
                authHeaders(requireSession())
                parameter("id", "eq.${place.id}")
                header("Prefer", "return=minimal")
                setBody(body)
            }
        if (!response.status.isSuccess()) throw restError(response)
        pokeChild(place.childMemberId)
    }.mapNetworkError()

    suspend fun delete(place: Place): Result<Unit> = runCatching {
        val response =
            httpClient.delete("$restUrl/places") {
                authHeaders(requireSession())
                parameter("id", "eq.${place.id}")
                header("Prefer", "return=minimal")
            }
        if (!response.status.isSuccess()) throw restError(response)
        pokeChild(place.childMemberId)
    }.mapNetworkError()

    /** Child: report an enter/exit. */
    suspend fun reportEvent(familyId: String, childMemberId: String, placeId: String, kind: String): Result<Unit> = runCatching {
        val body =
            buildJsonObject {
                put("family_id", familyId)
                put("child_member_id", childMemberId)
                put("place_id", placeId)
                put("kind", kind)
            }
        val response =
            httpClient.post("$restUrl/place_events") {
                authHeaders(requireSession())
                header("Prefer", "return=minimal")
                setBody(body)
            }
        if (!response.status.isSuccess()) throw restError(response)
    }.mapNetworkError()

    /** Parent: recent events of one child, newest first. */
    suspend fun events(childMemberId: String, limit: Int = 30): Result<List<PlaceEvent>> = runCatching {
        val response =
            httpClient.get("$restUrl/place_events") {
                authHeaders(requireSession())
                parameter("child_member_id", "eq.$childMemberId")
                parameter("order", "at.desc")
                parameter("limit", limit.toString())
                parameter("select", "id,child_member_id,place_id,kind,at")
            }
        if (!response.status.isSuccess()) throw restError(response)
        json.decodeFromString<List<PlaceEvent>>(response.bodyAsText())
    }.mapNetworkError()

    /**
     * Notifies a family member's devices through the send-push Edge Function (FCM data
     * message). With no title it is a silent wake-up carrying only [data]; with a title the
     * receiving app shows it on [channel]. Best-effort: failures are swallowed.
     */
    suspend fun push(
        targetUserId: String,
        data: Map<String, String>,
        title: String? = null,
        body: String? = null,
        channel: String? = null,
    ) {
        runCatching {
            val payload =
                buildJsonObject {
                    put("target_user_id", targetUserId)
                    title?.let { put("title", it) }
                    body?.let { put("body", it) }
                    channel?.let { put("channel", it) }
                    put("data", JsonObject(data.mapValues { kotlinx.serialization.json.JsonPrimitive(it.value) }))
                }
            httpClient.post("$baseUrl/functions/v1/send-push") {
                authHeaders(requireSession())
                setBody(payload)
            }
        }
    }

    /** Silent push to the child device: «places changed, refresh». */
    private suspend fun pokeChild(childMemberId: String) {
        runCatching {
            httpClient.post("$baseUrl/functions/v1/send-push") {
                authHeaders(requireSession())
                setBody("""{"member_id":"$childMemberId","data":{"action":"$ACTION_PLACES"}}""")
            }
        }
    }

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

    companion object {
        /** FCM data `action` telling the child device to re-fetch its places. */
        const val ACTION_PLACES = "places"
        private const val PLACE_SELECT = "id,family_id,child_member_id,name,latitude,longitude,radius_m,notify_enter,notify_exit"
    }
}
