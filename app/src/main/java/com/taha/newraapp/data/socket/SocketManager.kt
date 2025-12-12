package com.taha.newraapp.data.socket

import android.util.Log
import com.taha.newraapp.data.local.TokenManager
import io.socket.client.IO
import io.socket.client.Socket
import io.socket.emitter.Emitter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import org.json.JSONObject

/**
 * Connection status for the Socket.IO client
 */
enum class SocketStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
}

/**
 * Manages Socket.IO connection for real-time messaging.
 * Handles connection lifecycle and event dispatching.
 */
class SocketManager(
    private val tokenManager: TokenManager
) {
    companion object {
        private const val TAG = "SocketManager"
        private const val BASE_URL = "https://micladevops.com"
        private const val SOCKET_PATH = "/api/v2/socket.io"
    }

    private var socket: Socket? = null
    
    private val _connectionStatus = MutableStateFlow(SocketStatus.DISCONNECTED)
    val connectionStatus: StateFlow<SocketStatus> = _connectionStatus.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    // Event handlers map - extensible for future events
    private val eventHandlers = mutableMapOf<String, MutableList<(Any) -> Unit>>()

    /**
     * Connect to the Socket.IO server using the stored access token.
     */
    suspend fun connect() {
        if (socket?.connected() == true) {
            Log.d(TAG, "Already connected, skipping connection")
            return
        }

        val token = tokenManager.accessToken.first()
        if (token.isNullOrBlank()) {
            Log.e(TAG, "Cannot connect: No access token available")
            _lastError.value = "No access token available"
            _connectionStatus.value = SocketStatus.ERROR
            return
        }

        try {
            _connectionStatus.value = SocketStatus.CONNECTING
            Log.d(TAG, "Connecting to Socket.IO server...")

            val options = IO.Options().apply {
                path = SOCKET_PATH
                auth = mapOf("token" to token)
                transports = arrayOf("websocket", "polling")
                reconnection = true
                reconnectionAttempts = 5
                reconnectionDelay = 1000
                reconnectionDelayMax = 5000
                timeout = 20000
            }

            socket = IO.socket(BASE_URL, options).apply {
                // Core connection events
                on(Socket.EVENT_CONNECT, onConnect)
                on(Socket.EVENT_DISCONNECT, onDisconnect)
                on(Socket.EVENT_CONNECT_ERROR, onConnectError)
                
                // Register application-specific events
                registerDefaultEvents()
                
                connect()
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to create socket connection", e)
            _lastError.value = e.message
            _connectionStatus.value = SocketStatus.ERROR
        }
    }

    /**
     * Disconnect from the Socket.IO server.
     */
    fun disconnect() {
        Log.d(TAG, "Disconnecting from Socket.IO server...")
        socket?.apply {
            off()
            disconnect()
        }
        socket = null
        _connectionStatus.value = SocketStatus.DISCONNECTED
    }

    /**
     * Check if currently connected to the server.
     */
    fun isConnected(): Boolean = socket?.connected() == true

    /**
     * Emit an event to the server.
     */
    fun emit(event: String, data: JSONObject) {
        if (socket?.connected() == true) {
            Log.d(TAG, "Emitting event: $event")
            socket?.emit(event, data)
        } else {
            Log.w(TAG, "Cannot emit event $event: Socket not connected")
        }
    }

    /**
     * Emit an event to the server with acknowledgment callback.
     */
    fun emit(event: String, data: JSONObject, ack: (Array<Any>) -> Unit) {
        if (socket?.connected() == true) {
            Log.d(TAG, "Emitting event with ack: $event")
            socket?.emit(event, arrayOf(data)) { args ->
                ack(args)
            }
        } else {
            Log.w(TAG, "Cannot emit event $event: Socket not connected")
        }
    }

    /**
     * Register a handler for a specific event.
     * Multiple handlers can be registered for the same event.
     */
    fun on(event: String, handler: (Any) -> Unit) {
        eventHandlers.getOrPut(event) { mutableListOf() }.add(handler)
        socket?.on(event) { args ->
            if (args.isNotEmpty()) {
                handler(args[0])
            }
        }
    }

    /**
     * Remove all handlers for a specific event.
     */
    fun off(event: String) {
        eventHandlers.remove(event)
        socket?.off(event)
    }

    // ===========================================
    // Core Event Handlers
    // ===========================================

    private val onConnect = Emitter.Listener {
        Log.i(TAG, "✓ Connected to Socket.IO server")
        _connectionStatus.value = SocketStatus.CONNECTED
        _lastError.value = null
    }

    private val onDisconnect = Emitter.Listener { args ->
        val reason = if (args.isNotEmpty()) args[0].toString() else "Unknown"
        Log.w(TAG, "✗ Disconnected from Socket.IO server: $reason")
        _connectionStatus.value = SocketStatus.DISCONNECTED
    }

    private val onConnectError = Emitter.Listener { args ->
        val error = if (args.isNotEmpty()) args[0].toString() else "Unknown error"
        Log.e(TAG, "Connection error: $error")
        _lastError.value = error
        _connectionStatus.value = SocketStatus.ERROR
    }

    // ===========================================
    // Application Event Registration
    // ===========================================

    /**
     * Register default application events.
     * Add new event handlers here as the application grows.
     */
    private fun Socket.registerDefaultEvents() {
        // Chat events
        on(SocketEvents.MESSAGE_RECEIVED) { args ->
            Log.d(TAG, "Received message event")
            dispatchEvent(SocketEvents.MESSAGE_RECEIVED, args)
        }
        
        on(SocketEvents.MESSAGE_SENT) { args ->
            Log.d(TAG, "Message sent confirmation")
            dispatchEvent(SocketEvents.MESSAGE_SENT, args)
        }
        
        on(SocketEvents.MESSAGE_READ) { args ->
            Log.d(TAG, "Message read event")
            dispatchEvent(SocketEvents.MESSAGE_READ, args)
        }
        
        on(SocketEvents.USER_TYPING) { args ->
            Log.d(TAG, "User typing event")
            dispatchEvent(SocketEvents.USER_TYPING, args)
        }
        
        // Presence updates are now handled via targeted subscription
        // (request:presence:subscribe / request:presence:unsubscribe)
        on(SocketEvents.PRESENCE_UPDATE) { args ->
            Log.d(TAG, "Presence update event")
            dispatchEvent(SocketEvents.PRESENCE_UPDATE, args)
        }
    }

    private fun dispatchEvent(event: String, args: Array<Any>) {
        eventHandlers[event]?.forEach { handler ->
            if (args.isNotEmpty()) {
                handler(args[0])
            }
        }
    }
}

/**
 * Socket event names - centralized constants for type safety.
 */
object SocketEvents {
    // Message events
    const val MESSAGE_RECEIVED = "message:received"
    const val MESSAGE_SENT = "message:sent"
    const val MESSAGE_READ = "message:read"
    const val MESSAGE_DELIVERED = "message:delivered"
    
    // User presence events
    const val USER_TYPING = "user:typing"
    const val REQUEST_HEARTBEAT = "request:heartbeat"
    const val PRESENCE_UPDATE = "action:presence:update"
    const val REQUEST_PRESENCE_SUBSCRIBE = "request:presence:subscribe"
    const val REQUEST_PRESENCE_UNSUBSCRIBE = "request:presence:unsubscribe"
    
    // Room events
    const val ROOM_JOIN = "room:join"
    const val ROOM_LEAVE = "room:leave"
    
    // Notification events
    const val NOTIFICATION = "notification"
}
