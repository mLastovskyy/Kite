package app.kite.core.commands

import app.kite.core.auth.SessionManager
import app.kite.core.config.SupabaseConfig
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Supabase Realtime subscription to device_commands INSERTs over a raw Phoenix-protocol
 * WebSocket — deliberately no Supabase SDK (CLAUDE.md). Reconnects with backoff forever;
 * a dropped socket (or an expired access token, which makes the server close the channel)
 * simply reconnects with a fresh JWT. RLS filters rows server-side; the member_id filter
 * narrows the stream. Failures are harmless: polling stays the fallback.
 *
 * NEEDS_DEVICE_TEST: verify join/heartbeat payloads against the live Realtime service.
 */
class RealtimeCommands(
    private val httpClient: HttpClient,
    private val json: Json,
    private val sessionManager: SessionManager,
    private val baseUrl: String = SupabaseConfig.URL,
    private val apiKey: String = SupabaseConfig.PUBLISHABLE_KEY,
) {
    /** Starts listening; returns the job so the owner can cancel it. */
    fun listen(memberId: String, scope: CoroutineScope, onCommand: (DeviceCommand) -> Unit): Job = scope.launch {
        var backoffMs = INITIAL_BACKOFF_MS
        while (isActive) {
            val startedAt = System.currentTimeMillis()
            runCatching {
                connectOnce(memberId, onCommand)
            }
            if (!isActive) return@launch
            // A socket that stayed up was healthy: start over from the short delay, or a few
            // network hiccups would leave the child a minute behind every parent action.
            val lived = System.currentTimeMillis() - startedAt
            backoffMs = if (lived >= HEALTHY_CONNECTION_MS) INITIAL_BACKOFF_MS else (backoffMs * 2).coerceAtMost(MAX_BACKOFF_MS)
            delay(backoffMs)
        }
    }

    private suspend fun connectOnce(memberId: String, onCommand: (DeviceCommand) -> Unit) {
        val token = sessionManager.validAccessToken() ?: return
        val wsUrl = baseUrl.replaceFirst("https://", "wss://").replaceFirst("http://", "ws://") +
            "/realtime/v1/websocket?apikey=$apiKey&vsn=1.0.0"

        httpClient.webSocket(wsUrl) {
            send(Frame.Text(joinMessage(memberId, token).toString()))
            var heartbeatRef = 2
            val heartbeat =
                launch {
                    while (isActive) {
                        delay(HEARTBEAT_MS)
                        val msg =
                            buildJsonObject {
                                put("topic", "phoenix")
                                put("event", "heartbeat")
                                putJsonObject("payload") {}
                                put("ref", (heartbeatRef++).toString())
                            }
                        send(Frame.Text(msg.toString()))
                    }
                }
            try {
                for (frame in incoming) {
                    val text = (frame as? Frame.Text)?.readText() ?: continue
                    parseInsert(text)?.let(onCommand)
                }
            } finally {
                heartbeat.cancel()
            }
        }
    }

    private fun joinMessage(memberId: String, accessToken: String): JsonObject = buildJsonObject {
        put("topic", TOPIC)
        put("event", "phx_join")
        put("ref", "1")
        put("join_ref", "1")
        putJsonObject("payload") {
            put("access_token", accessToken)
            putJsonObject("config") {
                putJsonObject("broadcast") { put("self", false) }
                putJsonObject("presence") { put("key", "") }
                putJsonArray("postgres_changes") {
                    add(
                        buildJsonObject {
                            put("event", "INSERT")
                            put("schema", "public")
                            put("table", "device_commands")
                            put("filter", "member_id=eq.$memberId")
                        },
                    )
                }
            }
        }
    }

    /** Extracts a [DeviceCommand] from a postgres_changes INSERT frame; null otherwise. */
    private fun parseInsert(text: String): DeviceCommand? = runCatching {
        val root = json.parseToJsonElement(text).jsonObject
        if (root["event"]?.jsonPrimitive?.content != "postgres_changes") return null
        val record =
            root["payload"]?.jsonObject
                ?.get("data")?.jsonObject
                ?.get("record")?.jsonObject ?: return null
        DeviceCommand(
            id = record["id"]?.jsonPrimitive?.content ?: return null,
            memberId = record["member_id"]?.jsonPrimitive?.content ?: return null,
            command = record["command"]?.jsonPrimitive?.content ?: return null,
            // The payload carries the minutes of a grant_time. Without it the instant path
            // granted zero and acknowledged the command, so the polling fallback never saw it
            // again and «дал ещё 15 минут» quietly did nothing.
            payload = payloadOf(record),
            createdBy = record["created_by"]?.jsonPrimitive?.contentOrNull,
        )
    }.getOrNull()

    /** Realtime sends jsonb either as an object or, for some clients, as a JSON string. */
    private fun payloadOf(record: JsonObject): JsonObject {
        val raw = record["payload"] ?: return JsonObject(emptyMap())
        (raw as? JsonObject)?.let { return it }
        val text = (raw as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return JsonObject(emptyMap())
        return runCatching { json.parseToJsonElement(text).jsonObject }.getOrDefault(JsonObject(emptyMap()))
    }

    private companion object {
        const val TOPIC = "realtime:device-commands"
        const val HEARTBEAT_MS = 25_000L
        const val HEALTHY_CONNECTION_MS = 20_000L
        const val INITIAL_BACKOFF_MS = 5_000L
        const val MAX_BACKOFF_MS = 60_000L
    }
}
