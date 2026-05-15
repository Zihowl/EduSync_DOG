package dev.zihowl.dog.data.remote

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject

/**
 * Cliente de subscripciones GraphQL sobre WebSocket (`graphql-transport-ws`).
 * Escucha el evento `RealtimeEvents` del backend para que la app pueda
 * reaccionar al instante a cambios del servidor (p. ej. el catálogo de
 * docentes que altera el rol del usuario).
 */
class RealtimeClient(
    private val client: OkHttpClient = GraphQL.defaultClient
) {

    private var webSocket: WebSocket? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var stopped = false
    private var wsUrl: String? = null
    private var onScopes: ((List<String>) -> Unit)? = null

    fun start(baseUrl: String, onScopes: (List<String>) -> Unit) {
        val normalized = GraphQL.normalize(baseUrl) ?: return
        wsUrl = normalized
            .replaceFirst("https://", "wss://")
            .replaceFirst("http://", "ws://") + "/graphql/ws"
        this.onScopes = onScopes
        stopped = false
        connect()
    }

    fun stop() {
        stopped = true
        webSocket?.close(NORMAL_CLOSURE, null)
        webSocket = null
        scope.cancel()
    }

    private fun connect() {
        val url = wsUrl ?: return
        val request = Request.Builder()
            .url(url)
            .addHeader("Sec-WebSocket-Protocol", "graphql-transport-ws")
            .build()
        webSocket = client.newWebSocket(request, listener)
    }

    private fun scheduleReconnect() {
        if (stopped) return
        scope.launch {
            delay(RECONNECT_DELAY_MS)
            if (!stopped) connect()
        }
    }

    private val listener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            webSocket.send(JSONObject().put("type", "connection_init").toString())
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            val message = runCatching { JSONObject(text) }.getOrNull() ?: return
            when (message.optString("type")) {
                "connection_ack" -> {
                    val subscribe = JSONObject()
                        .put("id", SUBSCRIPTION_ID)
                        .put("type", "subscribe")
                        .put(
                            "payload",
                            JSONObject().put(
                                "query",
                                "subscription { RealtimeEvents { scopes } }"
                            )
                        )
                    webSocket.send(subscribe.toString())
                }
                "ping" -> webSocket.send(JSONObject().put("type", "pong").toString())
                "next" -> {
                    val scopes = message.optJSONObject("payload")
                        ?.optJSONObject("data")
                        ?.optJSONObject("RealtimeEvents")
                        ?.optJSONArray("scopes") ?: return
                    val list = (0 until scopes.length()).map { scopes.optString(it) }
                    if (list.isNotEmpty()) onScopes?.invoke(list)
                }
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            scheduleReconnect()
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            if (code != NORMAL_CLOSURE) scheduleReconnect()
        }
    }

    private companion object {
        const val SUBSCRIPTION_ID = "1"
        const val NORMAL_CLOSURE = 1000
        const val RECONNECT_DELAY_MS = 5_000L
    }
}
