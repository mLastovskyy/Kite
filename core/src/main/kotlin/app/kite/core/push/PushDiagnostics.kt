package app.kite.core.push

import app.kite.core.auth.AuthState
import app.kite.core.auth.SessionManager
import app.kite.core.config.SupabaseConfig
import app.kite.core.notifications.Channels
import app.kite.core.platform.PlatformServices
import app.kite.core.platform.PlatformVariant
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess

class PushDiagnostics(
    private val httpClient: HttpClient,
    private val sessionManager: SessionManager,
    private val platformServices: PlatformServices,
    private val pushTokenRemote: PushTokenRemote,
    private val baseUrl: String = SupabaseConfig.URL,
    private val apiKey: String = SupabaseConfig.PUBLISHABLE_KEY,
) {
    data class Report(
        val tokenObtained: Boolean,
        val registered: Boolean,
        val delivered: Int?,
        val error: String? = null,
        val variant: String = "",
    ) {
        val ok: Boolean get() = tokenObtained && registered && (delivered ?: 0) > 0
    }

    val variant: String get() = platformServices.variant.name.lowercase()

    suspend fun run(): Report {
        val token = platformServices.pushToken()
            ?: return Report(
                tokenObtained = false,
                registered = false,
                delivered = null,
                error = tokenHint(),
                variant = variant,
            )
        val platform = if (platformServices.variant == PlatformVariant.HMS) PushTokenRemote.PLATFORM_HMS else PushTokenRemote.PLATFORM_FCM
        val registration = pushTokenRemote.register(platform, token)
        if (registration.isFailure) {
            return Report(
                tokenObtained = true,
                registered = false,
                delivered = null,
                error = registration.exceptionOrNull()?.message,
            )
        }
        val accessToken = sessionManager.validAccessToken()
            ?: return Report(tokenObtained = true, registered = true, delivered = null, error = "Нужно войти заново")
        val userId = (sessionManager.authState.value as? AuthState.SignedIn)?.session?.userId
            ?: return Report(tokenObtained = true, registered = true, delivered = null, error = "Нет активной сессии")

        return runCatching {
            val response =
                httpClient.post("$baseUrl/functions/v1/send-push") {
                    header("apikey", apiKey)
                    header("Authorization", "Bearer $accessToken")
                    contentType(ContentType.Application.Json)
                    setBody(
                        """{"target_user_id":"$userId","title":"Проверка уведомлений",""" +
                            """"body":"Если вы это видите, уведомления работают.","channel":"${Channels.STATUS}",""" +
                            """"collapse":"selftest"}""",
                    )
                }
            val body = response.bodyAsText()
            if (!response.status.isSuccess()) {
                Report(tokenObtained = true, registered = true, delivered = null, error = "Сервер: ${response.status.value}")
            } else {
                Report(tokenObtained = true, registered = true, delivered = sentCount(body))
            }
        }.getOrElse { Report(tokenObtained = true, registered = true, delivered = null, error = "Нет соединения с сервером") }
    }

    private fun tokenHint(): String = when (platformServices.variant) {
        PlatformVariant.HMS -> "Это сборка для Huawei без Google-сервисов: push пока не поддержан. Установите обычную сборку Kite."
        PlatformVariant.GMS -> "Google-сервисы не выдали токен. Проверьте, что Google Play есть на телефоне и обновлён."
        else -> "На телефоне нет ни Google, ни Huawei push — уведомления придут при открытии приложения."
    }

    private fun sentCount(body: String): Int = Regex("\"sent\"\\s*:\\s*(\\d+)").find(body)?.groupValues?.get(1)?.toIntOrNull() ?: 0
}
