package app.kite.core.family

import app.kite.core.auth.AuthException
import app.kite.core.auth.SessionManager
import app.kite.core.config.SupabaseConfig
import io.ktor.client.HttpClient
import io.ktor.client.call.body
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
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Family/pairing calls against Supabase PostgREST + RPC, authorized with the current
 * access token. Server-side RLS is the real guard; this client just shapes requests.
 *
 * Creating an invite is a two-step client flow: generate token + code locally, POST the
 * row (storing only the token's SHA-256 hash), and hand the parent the deep link + code.
 */
class FamilyRepository(
    private val httpClient: HttpClient,
    private val json: Json,
    private val sessionManager: SessionManager,
    private val baseUrl: String = SupabaseConfig.URL,
    private val apiKey: String = SupabaseConfig.PUBLISHABLE_KEY,
) {
    private val restUrl get() = "$baseUrl/rest/v1"
    private val rpcUrl get() = "$baseUrl/rest/v1/rpc"

    /** Creates a family with its owner via the create_family RPC; returns the family id. */
    suspend fun createFamily(familyName: String?, displayName: String, avatarKind: String): Result<String> = rpc(
        "create_family",
        JsonObject(
            mapOf(
                "p_family_name" to JsonPrimitive(familyName),
                "p_display_name" to JsonPrimitive(displayName),
                "p_avatar_kind" to JsonPrimitive(avatarKind),
            ),
        ),
    ).mapCatching { text -> text.trim().trim('"') }

    /**
     * Joins an existing family from a scanned token or a typed 6-digit code. A child
     * device passes [totpSecretBase64] — the offline-approval shared secret it generated;
     * parents of the family read it back via [memberSecret].
     */
    suspend fun redeemPairing(
        token: String?,
        code: String?,
        displayName: String,
        avatarKind: String,
        totpSecretBase64: String? = null,
    ): Result<String> = rpc(
        "redeem_pairing",
        JsonObject(
            mapOf(
                "p_token" to JsonPrimitive(token),
                "p_code" to JsonPrimitive(code),
                "p_display_name" to JsonPrimitive(displayName),
                "p_avatar_kind" to JsonPrimitive(avatarKind),
                "p_totp_secret" to JsonPrimitive(totpSecretBase64),
            ),
        ),
    ).mapCatching { text -> text.trim().trim('"') }

    /** Who is inviting — for the child's consent screen, before anything is redeemed. */
    suspend fun pairingPreview(token: String?, code: String?): Result<PairingPreview> = rpc(
        "pairing_preview",
        JsonObject(
            mapOf(
                "p_token" to JsonPrimitive(token),
                "p_code" to JsonPrimitive(code),
            ),
        ),
    ).mapCatching { text ->
        json.decodeFromString<List<PairingPreview>>(text).firstOrNull()
            ?: throw AuthException("Код не найден")
    }

    /** Parent side: the TOTP secret a child deposited at pairing (RLS: parents only). */
    suspend fun memberSecret(memberId: String): Result<String> = runCatching {
        val response =
            httpClient.get("$restUrl/member_secrets") {
                authHeaders(requireSession())
                parameter("member_id", "eq.$memberId")
                parameter("select", "totp_secret")
            }
        if (!response.status.isSuccess()) throw restError(response)
        json.decodeFromString<List<MemberSecretRow>>(response.bodyAsText()).firstOrNull()?.totpSecret
            ?: throw AuthException("Секрет ещё не создан — привяжите устройство ребёнка заново")
    }.mapNetworkError()

    /** Parent creates a pairing invite (child or second parent). TTL 15 minutes. */
    suspend fun createInvite(familyId: String, kind: PairingKind, ttlMinutes: Long = 15): Result<PairingInvite> = runCatching {
        val token = PairingTokens.newToken()
        val code = PairingTokens.newCode()
        val expiresAtSeconds = System.currentTimeMillis() / 1000 + ttlMinutes * 60
        val userId = requireSession().let { sessionUserId() }
        val body =
            JsonObject(
                mapOf(
                    "family_id" to JsonPrimitive(familyId),
                    "kind" to JsonPrimitive(kind.serial),
                    "token_hash" to JsonPrimitive(PairingTokens.sha256Hex(token)),
                    "code" to JsonPrimitive(code),
                    "created_by" to JsonPrimitive(userId),
                    "expires_at" to JsonPrimitive(isoFromEpochSeconds(expiresAtSeconds)),
                ),
            )
        val response =
            httpClient.post("$restUrl/pair_requests") {
                authHeaders(requireSession())
                header("Prefer", "return=minimal")
                setBody(body)
            }
        if (!response.status.isSuccess()) throw restError(response)
        PairingInvite(
            deepLink = PairingTokens.deepLink(token),
            code = code,
            expiresAt = expiresAtSeconds,
            kind = kind,
        )
    }.mapNetworkError()

    /**
     * Edits the signed-in user's own member row(s): name, preset avatar, custom photo URL.
     * Null leaves a field untouched; [clearAvatarUrl] drops the photo so the preset shows.
     * RLS `members_update_self` scopes the PATCH to rows where user_id = auth.uid().
     */
    suspend fun updateMyProfile(
        displayName: String? = null,
        avatarKind: String? = null,
        avatarUrl: String? = null,
        clearAvatarUrl: Boolean = false,
    ): Result<Unit> = runCatching {
        val fields = buildMap<String, JsonElement> {
            displayName?.let { put("display_name", JsonPrimitive(it)) }
            avatarKind?.let { put("avatar_kind", JsonPrimitive(it)) }
            if (clearAvatarUrl) put("avatar_url", JsonNull) else avatarUrl?.let { put("avatar_url", JsonPrimitive(it)) }
        }
        if (fields.isEmpty()) return@runCatching
        val response =
            httpClient.patch("$restUrl/family_members") {
                authHeaders(requireSession())
                header("Prefer", "return=minimal")
                parameter("user_id", "eq.${sessionUserId()}")
                setBody(JsonObject(fields))
            }
        if (!response.status.isSuccess()) throw restError(response)
    }.mapNetworkError()

    /** Members of a family, parents first. */
    suspend fun members(familyId: String): Result<List<FamilyMember>> = runCatching {
        val response =
            httpClient.get("$restUrl/family_members") {
                authHeaders(requireSession())
                parameter("family_id", "eq.$familyId")
                parameter("order", "role.asc,created_at.asc")
            }
        if (!response.status.isSuccess()) throw restError(response)
        json.decodeFromString<List<FamilyMember>>(response.bodyAsText())
    }.mapNetworkError()

    /** The families the signed-in user belongs to (via their member rows). */
    suspend fun myFamilies(): Result<List<Family>> = runCatching {
        val response =
            httpClient.get("$restUrl/families") {
                authHeaders(requireSession())
                parameter("select", "id,name,owner_user_id")
            }
        if (!response.status.isSuccess()) throw restError(response)
        json.decodeFromString<List<Family>>(response.bodyAsText())
    }.mapNetworkError()

    // ── internals ────────────────────────────────────────────────────────────
    private suspend fun rpc(name: String, body: JsonObject): Result<String> = runCatching {
        val response =
            httpClient.post("$rpcUrl/$name") {
                authHeaders(requireSession())
                setBody(body)
            }
        if (!response.status.isSuccess()) throw restError(response)
        response.bodyAsText()
    }.mapNetworkError()

    private suspend fun requireSession(): String = sessionManager.validAccessToken() ?: throw AuthException("Нужно войти заново")

    private fun sessionUserId(): String = (sessionManager.authState.value as? app.kite.core.auth.AuthState.SignedIn)?.session?.userId
        ?: throw AuthException("Нет активной сессии")

    private fun io.ktor.client.request.HttpRequestBuilder.authHeaders(accessToken: String) {
        header("apikey", apiKey)
        header("Authorization", "Bearer $accessToken")
        contentType(ContentType.Application.Json)
    }

    private suspend fun restError(response: HttpResponse): Exception {
        val text = runCatching { response.bodyAsText() }.getOrNull().orEmpty()
        val message =
            when {
                text.contains("already used") -> "Код уже использован"
                text.contains("expired") -> "Код истёк, попросите новый"
                text.contains("invalid pairing") -> "Код не найден"
                text.contains("too many attempts") -> "Слишком много попыток"
                else -> "Ошибка сервера (${response.status.value})"
            }
        return AuthException(message)
    }

    private fun <T> Result<T>.mapNetworkError(): Result<T> = recoverCatching { throwable ->
        throw if (throwable is AuthException) throwable else AuthException("Нет соединения с сервером")
    }

    private fun isoFromEpochSeconds(epochSeconds: Long): String {
        // Minimal ISO-8601 UTC without pulling kotlinx-datetime yet (reserved for M3+/M4).
        val instant = java.time.Instant.ofEpochSecond(epochSeconds)
        return java.time.format.DateTimeFormatter.ISO_INSTANT.format(instant)
    }
}

@Serializable
private data class MemberSecretRow(@SerialName("totp_secret") val totpSecret: String)
