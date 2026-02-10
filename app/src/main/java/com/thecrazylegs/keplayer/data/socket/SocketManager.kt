package com.thecrazylegs.keplayer.data.socket

import android.util.Log
import io.socket.client.IO
import io.socket.client.Socket
import io.socket.engineio.client.transports.WebSocket
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.json.JSONObject
import java.net.URI

data class SocketEvent(
    val type: String,
    val data: String,
    val timestamp: Long = System.currentTimeMillis(),
    val count: Int = 1  // For grouping identical consecutive events (Chrome-style)
) {
    /**
     * Get the action type from JSON data (for ACTION events)
     */
    fun getActionType(): String? {
        if (type != "ACTION") return null
        return try {
            org.json.JSONObject(data).optString("type", null)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Check if this event should be grouped with another (same type/action)
     */
    fun isSameAs(other: SocketEvent): Boolean {
        if (type != other.type) return false
        if (type == "ACTION") {
            return getActionType() == other.getActionType()
        }
        return true
    }
}

class SocketManager {

    private var socket: Socket? = null

    private val _events = MutableSharedFlow<SocketEvent>(replay = 50)
    val events: SharedFlow<SocketEvent> = _events.asSharedFlow()

    private val _connectionState = MutableSharedFlow<String>(replay = 1)
    val connectionState: SharedFlow<String> = _connectionState.asSharedFlow()

    fun connect(serverUrl: String, token: String?) {
        try {
            val uri = normalizeUrl(serverUrl)

            val options = IO.Options().apply {
                transports = arrayOf(WebSocket.NAME)
                reconnection = true
                reconnectionAttempts = 5
                reconnectionDelay = 1000
                timeout = 10000
                // Send token as cookie in headers (like browser does)
                if (token != null) {
                    extraHeaders = mapOf("Cookie" to listOf("keToken=$token"))
                }
            }

            socket = IO.socket(uri, options)

            setupEventListeners()

            emitEvent("CONNECTING", "Connecting to $uri...")
            socket?.connect()

        } catch (e: Exception) {
            Log.e("SocketManager", "Connection error", e)
            emitEvent("ERROR", "Connection error: ${e.message}")
        }
    }

    private fun setupEventListeners() {
        socket?.apply {
            // Connection events
            on(Socket.EVENT_CONNECT) {
                Log.d("SocketManager", "Connected!")
                emitEvent("CONNECT", "Connected to server")
                emitConnectionState("connected")
            }

            on(Socket.EVENT_DISCONNECT) { args ->
                val reason = args.firstOrNull()?.toString() ?: "unknown"
                Log.d("SocketManager", "Disconnected: $reason")
                emitEvent("DISCONNECT", "Disconnected: $reason")
                emitConnectionState("disconnected")
            }

            on(Socket.EVENT_CONNECT_ERROR) { args ->
                val error = args.firstOrNull()?.toString() ?: "unknown error"
                Log.e("SocketManager", "Connection error: $error")
                emitEvent("CONNECT_ERROR", error)
                emitConnectionState("error")
            }

            // KE specific events - "action" is the main event type
            on("action") { args ->
                args.forEach { arg ->
                    val data = formatData(arg)
                    Log.d("SocketManager", "Action received: $data")
                    emitEvent("ACTION", data)
                }
            }

            // Other events
            on("message") { args ->
                val data = args.map { formatData(it) }.joinToString("\n")
                emitEvent("MESSAGE", data)
            }

            on("error") { args ->
                val data = args.map { formatData(it) }.joinToString("\n")
                emitEvent("SERVER_ERROR", data)
            }
        }
    }

    private fun formatData(arg: Any?): String {
        return when (arg) {
            is JSONObject -> try { arg.toString(2) } catch (e: Exception) { arg.toString() }
            null -> "null"
            else -> arg.toString()
        }
    }

    private fun emitEvent(type: String, data: String) {
        _events.tryEmit(SocketEvent(type, data))
    }

    private fun emitConnectionState(state: String) {
        _connectionState.tryEmit(state)
    }

    fun disconnect() {
        socket?.disconnect()
        socket?.off()
        socket = null
        emitEvent("DISCONNECTED", "Manually disconnected")
        emitConnectionState("disconnected")
    }

    /**
     * Emit an action to the server
     */
    fun emit(type: String, payload: JSONObject) {
        try {
            val action = JSONObject().apply {
                put("type", type)
                put("payload", payload)
            }
            // Debug: log full payload for PLAYER_EMIT_STATUS
            if (type.contains("PLAYER_EMIT_STATUS") && payload.has("historyJSON")) {
                Log.d("SocketManager", "EMIT FULL: ${action.toString(2)}")
            }
            socket?.emit("action", action)
            Log.d("SocketManager", "Emitted: $type")
        } catch (e: Exception) {
            Log.e("SocketManager", "Error emitting $type", e)
        }
    }

    /**
     * Emit an action without payload
     */
    fun emit(type: String) {
        try {
            val action = JSONObject().apply {
                put("type", type)
            }
            socket?.emit("action", action)
            Log.d("SocketManager", "Emitted: $type")
        } catch (e: Exception) {
            Log.e("SocketManager", "Error emitting $type", e)
        }
    }

    fun isConnected(): Boolean = socket?.connected() == true

    private fun normalizeUrl(url: String): URI {
        var normalized = url.trim()
        if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
            normalized = "http://$normalized"
        }
        return URI.create(normalized)
    }
}
