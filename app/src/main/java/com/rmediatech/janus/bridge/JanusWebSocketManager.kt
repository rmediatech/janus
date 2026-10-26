package com.rmediatech.janus.bridge

import com.rmediatech.janus.clipboard.JanusClipboardBridge
import com.rmediatech.janus.devtools.JanusLogcatStreamer
import com.rmediatech.janus.logging.SovereignLogger
import fi.iki.elonen.NanoHTTPD.IHTTPSession
import fi.iki.elonen.NanoWSD.WebSocket
import fi.iki.elonen.NanoWSD.WebSocketFrame
import org.json.JSONObject
import java.io.IOException
import java.io.OutputStream
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Sovereign WebSocket Session & Temporal Liveness Manager (`SOVEREIGN-LIFECYCLE-VIABILITY-01`):
 * One Job: Manages active WebSocket connections, enforces Zero-Ghost-Socket invalidation across
 * sleep/wake boundaries, and dispatches bi-directional clipboard & logcat frames.
 */
class JanusWebSocketManager(
    private val onClientCountChanged: (Int) -> Unit,
    private val onActivityEvent: (String) -> Unit
) {
    private val connectedWebSockets = CopyOnWriteArrayList<JanusWebSocket>()

    val activeClientCount: Int
        get() = connectedWebSockets.size

    fun createWebSocket(handshake: IHTTPSession): WebSocket {
        return JanusWebSocket(handshake)
    }

    /**
     * Broadcasts a JSON message to all open WebSockets.
     * Atomically evicts any dead or broken sockets encountered during transmission.
     */
    fun broadcast(message: String) {
        val evicted = mutableListOf<JanusWebSocket>()
        SovereignLogger.d(SovereignLogger.Category.WEBSOCKET, "WS BROADCAST (${connectedWebSockets.size} clients): ${if (message.length > 60) message.take(60) + "..." else message}")
        for (ws in connectedWebSockets) {
            if (!ws.isOpen) {
                evicted.add(ws)
                continue
            }
            try {
                ws.send(message)
                ws.flushSocket()
            } catch (e: Exception) {
                SovereignLogger.w(
                    SovereignLogger.Category.WEBSOCKET,
                    "Broadcast write failed on stale socket; evicting ghost connection",
                    e
                )
                evicted.add(ws)
            }
        }
        if (evicted.isNotEmpty()) {
            evicted.forEach { evictSocket(it, "BROADCAST_IO_FAILURE") }
        }
    }

    /**
     * Actively probes all tracked WebSockets across temporal sleep/wake boundaries
     * (`SOVEREIGN-LIFECYCLE-VIABILITY-01`). Evicts closed or half-open ghost sockets.
     */
    fun pruneAndProbeConnections(): Int {
        val snapshot = connectedWebSockets.toList()
        if (snapshot.isEmpty()) return 0

        var evictedCount = 0
        for (ws in snapshot) {
            if (!ws.isOpen) {
                evictSocket(ws, "SOCKET_NOT_OPEN")
                evictedCount++
                continue
            }
            try {
                ws.ping(ByteArray(0))
                if (!ws.flushSocket()) {
                    evictSocket(ws, "FLUSH_FAILED")
                    evictedCount++
                }
            } catch (e: Exception) {
                SovereignLogger.w(
                    SovereignLogger.Category.WEBSOCKET,
                    "Temporal probe failed on half-open WebSocket; evicting ghost socket",
                    e
                )
                evictSocket(ws, "PING_PROBE_FAILED")
                evictedCount++
            }
        }

        if (evictedCount > 0) {
            SovereignLogger.i(
                SovereignLogger.Category.LIFECYCLE,
                "Pruned $evictedCount ghost WebSocket(s); remaining active=${connectedWebSockets.size}"
            )
        }
        return connectedWebSockets.size
    }

    fun closeAll() {
        val snapshot = connectedWebSockets.toList()
        connectedWebSockets.clear()
        for (ws in snapshot) {
            ws.cleanupSubscriptions()
            try {
                ws.close(WebSocketFrame.CloseCode.GoingAway, "Bridge stopping", false)
            } catch (_: Exception) {}
        }
    }

    private fun evictSocket(ws: JanusWebSocket, reason: String) {
        ws.cleanupSubscriptions()
        val removed = connectedWebSockets.remove(ws)
        try {
            ws.close(WebSocketFrame.CloseCode.AbnormalClosure, reason, false)
        } catch (_: Exception) {}
        if (removed) {
            SovereignLogger.i(
                SovereignLogger.Category.WEBSOCKET,
                "WebSocket evicted ($reason). Active clients: ${connectedWebSockets.size}"
            )
            onClientCountChanged(connectedWebSockets.size)
            onActivityEvent("CLIENT_DISCONNECTED")
        }
    }

    inner class JanusWebSocket(handshake: IHTTPSession) : WebSocket(handshake) {
        @Volatile
        var lastSeenAtMs: Long = System.currentTimeMillis()
            private set

        private val remoteIp: String = handshake.remoteIpAddress ?: "unknown"

        private val outField by lazy {
            try {
                val f = WebSocket::class.java.getDeclaredField("out")
                f.isAccessible = true
                f
            } catch (_: Exception) {
                null
            }
        }

        fun flushSocket(): Boolean {
            return try {
                (outField?.get(this) as? OutputStream)?.flush()
                true
            } catch (_: Exception) {
                false
            }
        }

        private var isSubscribedToLogcat = false
        private val logcatListener: (String) -> Unit = { line ->
            try {
                val msg = JSONObject().apply {
                    put("type", "logcat_line")
                    put("line", line)
                }.toString()
                send(msg)
                flushSocket()
            } catch (_: Exception) {
                evictSocket(this, "LOGCAT_STREAM_BROKEN")
            }
        }

        fun cleanupSubscriptions() {
            if (isSubscribedToLogcat) {
                isSubscribedToLogcat = false
                JanusLogcatStreamer.removeListener(logcatListener)
            }
        }

        override fun onOpen() {
            lastSeenAtMs = System.currentTimeMillis()
            connectedWebSockets.add(this)
            SovereignLogger.i(
                SovereignLogger.Category.WEBSOCKET,
                "WebSocket opened from $remoteIp (active=${connectedWebSockets.size})"
            )
            onClientCountChanged(connectedWebSockets.size)
            onActivityEvent("CLIENT_CONNECTED")

            val latest = JanusClipboardBridge.getLatest()
            if (latest != null) {
                try {
                    val initMsg = JSONObject().apply {
                        put("type", "clipboard_update")
                        put("item", latest.toJson())
                    }.toString()
                    send(initMsg)
                    flushSocket()
                } catch (_: Exception) {}
            }
        }

        override fun onClose(code: WebSocketFrame.CloseCode?, reason: String?, initiatedByRemote: Boolean) {
            cleanupSubscriptions()
            if (connectedWebSockets.remove(this)) {
                SovereignLogger.i(
                    SovereignLogger.Category.WEBSOCKET,
                    "WebSocket closed from $remoteIp (code=$code, active=${connectedWebSockets.size})"
                )
                onClientCountChanged(connectedWebSockets.size)
                onActivityEvent("CLIENT_DISCONNECTED")
            }
        }

        override fun onMessage(message: WebSocketFrame) {
            lastSeenAtMs = System.currentTimeMillis()
            try {
                val payload = message.textPayload ?: return
                if (payload.length > 131_072) return // Bound incoming WS frame against allocation bombs
                val json = JSONObject(payload)
                val action = json.optString("action")
                when (action) {
                    "start_logcat" -> {
                        SovereignLogger.i(SovereignLogger.Category.WEBSOCKET, "WS ACTION: start_logcat from $remoteIp")
                        if (!isSubscribedToLogcat) {
                            isSubscribedToLogcat = true
                            JanusLogcatStreamer.addListener(logcatListener)
                        }
                    }
                    "stop_logcat" -> {
                        SovereignLogger.i(SovereignLogger.Category.WEBSOCKET, "WS ACTION: stop_logcat from $remoteIp")
                        cleanupSubscriptions()
                    }
                    "clipboard_push" -> {
                        val text = json.optString("text")
                        if (text.isNotBlank()) {
                            SovereignLogger.i(SovereignLogger.Category.CLIPBOARD, "WS CLIPBOARD_PUSH (${text.length} chars) from $remoteIp")
                            JanusClipboardBridge.push(text, "desktop")
                        }
                    }
                    "ping" -> {
                        SovereignLogger.d(SovereignLogger.Category.WEBSOCKET, "WS PING from $remoteIp -> pong")
                        val pong = JSONObject().apply {
                            put("type", "pong")
                            put("timestamp", System.currentTimeMillis())
                        }.toString()
                        send(pong)
                        flushSocket()
                    }
                    else -> {
                        SovereignLogger.d(SovereignLogger.Category.WEBSOCKET, "WS MESSAGE action='$action' from $remoteIp")
                    }
                }
            } catch (_: Exception) {}
        }

        override fun onPong(pong: WebSocketFrame?) {
            lastSeenAtMs = System.currentTimeMillis()
        }

        override fun onException(exception: IOException?) {
            if (exception is java.net.SocketTimeoutException && isOpen) {
                // Ignore transient socket read timeout if socket is still active
                return
            }
            evictSocket(this, "IO_EXCEPTION: ${exception?.message ?: "broken pipe"}")
        }
    }
}
