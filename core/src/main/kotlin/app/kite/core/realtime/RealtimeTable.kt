package app.kite.core.realtime

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
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

class RealtimeTable(
    private val httpClient: HttpClient,
    private val json: Json,
    private val sessionManager: SessionManager,
    private val baseUrl: String = SupabaseConfig.URL,
    private val apiKey: String = SupabaseConfig.PUBLISHABLE_KEY,
) {
    fun subscribe(
        scope: CoroutineScope,
        table: String,
        filter: String? = null,
        events: List<String> = listOf(EVENT_INSERT),
        onChange: (RealtimeChange) -> Unit,
    ): Job = scope.launch {
        var backoffMs = INITIAL_BACKOFF_MS
        while (isActive) {
            val startedAt = System.currentTimeMillis()
            runCatching { connect(table, filter, events, onChange) }
            if (!isActive) return@launch
            val lived = System.currentTimeMillis() - startedAt
            backoffMs = if (lived >= HEALTHY_CONNECTION_MS) INITIAL_BACKOFF_MS else (backoffMs * 2).coerceAtMost(MAX_BACKOFF_MS)
            delay(backoffMs)
        }
    }

    private suspend fun connect(table: String, filter: String?, events: List<String>, onChange: (RealtimeChange) -> Unit) {
        val token = sessionManager.validAccessToken() ?: return
        val socketUrl = baseUrl.replaceFirst("https://", "wss://").replaceFirst("http://", "ws://") +
            "/realtime/v1/websocket?apikey=$apiKey&vsn=1.0.0"

        httpClient.webSocket(socketUrl) {
            send(Frame.Text(joinMessage(table, filter, events, token).toString()))
            val heartbeat = launch { heartbeatLoop { send(Frame.Text(it)) } }
            try {
                for (frame in incoming) {
                    val text = (frame as? Frame.Text)?.readText() ?: continue
                    parseChange(text)?.let(onChange)
                }
            } finally {
                heartbeat.cancel()
            }
        }
    }

    private suspend fun heartbeatLoop(send: suspend (String) -> Unit) {
        var ref = 2
        while (true) {
            delay(HEARTBEAT_MS)
            val message =
                buildJsonObject {
                    put("topic", "phoenix")
                    put("event", "heartbeat")
                    putJsonObject("payload") {}
                    put("ref", (ref++).toString())
                }
            send(message.toString())
        }
    }

    private fun joinMessage(table: String, filter: String?, events: List<String>, accessToken: String): JsonObject = buildJsonObject {
        put("topic", "realtime:kite-$table")
        put("event", "phx_join")
        put("ref", "1")
        put("join_ref", "1")
        putJsonObject("payload") {
            put("access_token", accessToken)
            putJsonObject("config") {
                putJsonObject("broadcast") { put("self", false) }
                putJsonObject("presence") { put("key", "") }
                putJsonArray("postgres_changes") {
                    events.forEach { event ->
                        add(
                            buildJsonObject {
                                put("event", event)
                                put("schema", "public")
                                put("table", table)
                                if (filter != null) put("filter", filter)
                            },
                        )
                    }
                }
            }
        }
    }

    private fun parseChange(text: String): RealtimeChange? = runCatching {
        val root = json.parseToJsonElement(text).jsonObject
        if (root["event"]?.jsonPrimitive?.content != "postgres_changes") return null
        val data = root["payload"]?.jsonObject?.get("data")?.jsonObject ?: return null
        val record = data["record"]?.jsonObject ?: return null
        RealtimeChange(event = data["type"]?.jsonPrimitive?.content ?: EVENT_INSERT, record = record)
    }.getOrNull()

    companion object {
        const val EVENT_INSERT = "INSERT"
        const val EVENT_UPDATE = "UPDATE"

        private const val HEALTHY_CONNECTION_MS = 20_000L
        private const val HEARTBEAT_MS = 25_000L
        private const val INITIAL_BACKOFF_MS = 3_000L
        private const val MAX_BACKOFF_MS = 60_000L
    }
}

data class RealtimeChange(val event: String, val record: JsonObject) {
    fun string(field: String): String? = runCatching { record[field]?.jsonPrimitive?.content }.getOrNull()
}
