package app.kite.core.apps

import app.kite.core.auth.AuthException
import app.kite.core.auth.SessionManager
import app.kite.core.config.SupabaseConfig
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess

/**
 * App icons in Supabase Storage (bucket `app-icons`, public read): the child uploads a 64 px
 * PNG per launchable app once; the parent renders it by URL. The URL is deterministic, so no
 * table is needed — a missing object simply falls back to the letter avatar.
 */
class AppIconsRemote(
    private val httpClient: HttpClient,
    private val sessionManager: SessionManager,
    private val baseUrl: String = SupabaseConfig.URL,
    private val apiKey: String = SupabaseConfig.PUBLISHABLE_KEY,
) {
    /** Child: upload (or replace) one icon. */
    suspend fun upload(memberId: String, packageName: String, png: ByteArray): Result<Unit> = runCatching {
        val token = sessionManager.validAccessToken() ?: throw AuthException("Нужно войти заново")
        val response =
            httpClient.post("$baseUrl/storage/v1/object/app-icons/${path(memberId, packageName)}") {
                header("apikey", apiKey)
                header("Authorization", "Bearer $token")
                header("x-upsert", "true")
                contentType(ContentType.Image.PNG)
                setBody(png)
            }
        if (!response.status.isSuccess()) throw AuthException("Ошибка сервера (${response.status.value})")
    }.recoverCatching { throwable ->
        throw if (throwable is AuthException) throwable else AuthException("Нет соединения с сервером")
    }

    /** Parent: public URL of the icon the child may have uploaded. */
    fun iconUrl(memberId: String, packageName: String): String = publicUrl(memberId, packageName, baseUrl)

    companion object {
        const val ICON_PX = 64

        private fun path(memberId: String, packageName: String): String = "$memberId/$packageName.png"

        /** Deterministic public URL — usable without an instance (UI code has no DI handle). */
        fun publicUrl(memberId: String, packageName: String, baseUrl: String = SupabaseConfig.URL): String =
            "$baseUrl/storage/v1/object/public/app-icons/${path(memberId, packageName)}"
    }
}
