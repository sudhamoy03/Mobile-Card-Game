package com.example.network

import android.util.Log
import com.example.BuildConfig
import com.example.model.Player
import io.socket.client.IO
import io.socket.client.Manager
import io.socket.client.Socket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URI
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

enum class ServerConnectionState(val technicalLabel: String) {
    CONNECTING("CONNECTING"),
    CONNECTED("CONNECTED"),
    DISCONNECTED("DISCONNECTED"),
    TIMEOUT("TIMEOUT"),
    SERVER_ERROR("SERVER ERROR")
}

data class SocketRoomResponse(
    val success: Boolean,
    val roomCode: String?,
    val player: Player?,
    val sessionToken: String?,
    val targetScore: Int = 300,
    val error: String? = null
)

class SocketIOManager {

    companion object {
        private const val TAG = "MultiplayerSocket"
        private const val CONNECTION_TIMEOUT_MS = 10_000L // Strict 10-second timeout
        private const val SOCKET_PATH = "/socket.io"
        const val PRODUCTION_SERVER_URL = "https://mobile-card-game.onrender.com"
    }

    private var socket: Socket? = null
    private val isConnecting = AtomicBoolean(false)
    private var timeoutJob: Job? = null

    private val _connectionState = MutableStateFlow(ServerConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ServerConnectionState> = _connectionState.asStateFlow()

    private val _lastErrorMessage = MutableStateFlow<String?>(null)
    val lastErrorMessage: StateFlow<String?> = _lastErrorMessage.asStateFlow()

    private val _resolvedServerUrl = MutableStateFlow<String>(resolveConfiguredServerUrl())
    val resolvedServerUrl: StateFlow<String> = _resolvedServerUrl.asStateFlow()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    fun resolveConfiguredServerUrl(): String {
        val configured = BuildConfig.SERVER_URL.trim()
        if (configured.isNotEmpty() &&
            !configured.contains("localhost") &&
            !configured.contains("127.0.0.1") &&
            !configured.contains("10.0.2.2")
        ) {
            return configured.replace("wss://", "https://").replace("ws://", "http://").removeSuffix("/")
        }
        return PRODUCTION_SERVER_URL
    }

    suspend fun checkHealth(url: String): Boolean = withContext(Dispatchers.IO) {
        val httpUrl = url
            .replace("wss://", "https://")
            .replace("ws://", "http://")
        val healthUrl = "${httpUrl.removeSuffix("/")}/health"
        Log.i(TAG, "[STAGE 3: HEALTH_CHECK] Testing GET $healthUrl")
        try {
            val req = Request.Builder().url(healthUrl).get().build()
            val resp = okHttpClient.newCall(req).execute()
            val body = resp.body?.string().orEmpty()
            val ok = resp.isSuccessful && body.contains("\"status\"") && body.contains("\"ok\"")
            Log.i(TAG, "[STAGE 3: HEALTH_CHECK] Result code=${resp.code}, isOk=$ok, body=$body")
            ok
        } catch (e: Exception) {
            Log.w(TAG, "[STAGE 3: HEALTH_CHECK] Health check failed for $healthUrl: ${e.message}")
            false
        }
    }

    fun connect(scope: CoroutineScope, onConnected: (() -> Unit)? = null) {
        if (socket?.connected() == true) {
            Log.i(TAG, "[STAGE: ALREADY_CONNECTED] Socket already connected. ID: ${socket?.id()}")
            _connectionState.value = ServerConnectionState.CONNECTED
            onConnected?.invoke()
            return
        }

        if (isConnecting.getAndSet(true)) {
            Log.d(TAG, "Connection attempt already in progress.")
            return
        }

        _connectionState.value = ServerConnectionState.CONNECTING
        _lastErrorMessage.value = null

        val serverUrl = resolveConfiguredServerUrl()
        _resolvedServerUrl.value = serverUrl
        Log.i(TAG, "[STAGE 1: RESOLVE_URL] Configured SERVER_URL: $serverUrl")

        timeoutJob?.cancel()
        timeoutJob = scope.launch {
            delay(CONNECTION_TIMEOUT_MS)
            if (_connectionState.value == ServerConnectionState.CONNECTING) {
                Log.e(TAG, "[STAGE: TIMEOUT] Connection timed out after 10 seconds.")
                isConnecting.set(false)
                _connectionState.value = ServerConnectionState.TIMEOUT
                _lastErrorMessage.value = "Connection timed out after 10 seconds."
                disconnect()
            }
        }

        scope.launch(Dispatchers.IO) {
            try {
                val isHealthy = checkHealth(serverUrl)
                if (!isHealthy) {
                    Log.w(TAG, "[STAGE 3: HEALTH_CHECK] Pre-health check was unsuccessful. Proceeding with Socket.IO handshake.")
                }

                Log.i(TAG, "[STAGE 2: INIT_CLIENT] Initializing Socket.IO client (transport: websocket, path: $SOCKET_PATH)")
                val opts = IO.Options().apply {
                    transports = arrayOf("websocket")
                    timeout = CONNECTION_TIMEOUT_MS
                    reconnection = true
                    reconnectionAttempts = 5
                    reconnectionDelay = 1500L
                    path = SOCKET_PATH
                }

                val normalizedUrl = serverUrl.replace("wss://", "https://").replace("ws://", "http://").removeSuffix("/")
                val socketInstance = IO.socket(URI.create(normalizedUrl), opts)
                socket = socketInstance

                registerEventHandlers(socketInstance, normalizedUrl, onConnected)

                Log.i(TAG, "[STAGE 4: HANDSHAKE] Connecting to $normalizedUrl via Socket.IO...")
                socketInstance.connect()

            } catch (e: Exception) {
                Log.e(TAG, "[STAGE: INIT_ERROR] Failed to initialize socket: ${e.message}", e)
                isConnecting.set(false)
                timeoutJob?.cancel()
                _connectionState.value = ServerConnectionState.SERVER_ERROR
                _lastErrorMessage.value = e.message ?: "Failed to initialize connection"
            }
        }
    }

    private fun registerEventHandlers(s: Socket, serverUrl: String, onConnected: (() -> Unit)?) {
        s.on(Socket.EVENT_CONNECT) {
            Log.i(TAG, "[Socket.IO connect] Handshake succeeded to $serverUrl! Socket ID: ${s.id()} | Connected: ${s.connected()}")
            isConnecting.set(false)
            timeoutJob?.cancel()
            _connectionState.value = ServerConnectionState.CONNECTED
            _lastErrorMessage.value = null
            onConnected?.invoke()
        }

        s.on(Socket.EVENT_CONNECT_ERROR) { args ->
            val errObj = args.firstOrNull()
            val errMsg = when (errObj) {
                is Throwable -> {
                    val cause = errObj.cause?.message?.let { " (Cause: $it)" } ?: ""
                    "${errObj.javaClass.simpleName}: ${errObj.message ?: "Connection error"}$cause"
                }
                else -> errObj?.toString() ?: "Unknown connection error"
            }
            Log.e(TAG, "[Socket.IO connect_error] Connection to $serverUrl failed: $errMsg", errObj as? Throwable)
            isConnecting.set(false)
            timeoutJob?.cancel()
            _connectionState.value = ServerConnectionState.SERVER_ERROR
            _lastErrorMessage.value = errMsg
        }

        s.on(Socket.EVENT_DISCONNECT) { args ->
            val reason = args.firstOrNull()?.toString() ?: "Unknown reason"
            Log.w(TAG, "[STAGE: DISCONNECT] Socket disconnected: $reason")
            isConnecting.set(false)
            _connectionState.value = ServerConnectionState.DISCONNECTED
        }

        s.io().on(Manager.EVENT_RECONNECT_ATTEMPT) { args ->
            val attempt = args.firstOrNull()?.toString() ?: "1"
            Log.i(TAG, "[STAGE: RECONNECT_ATTEMPT] Reconnect attempt #$attempt...")
            _connectionState.value = ServerConnectionState.CONNECTING
        }

        s.io().on(Manager.EVENT_RECONNECT) { args ->
            val attempt = args.firstOrNull()?.toString() ?: ""
            Log.i(TAG, "[STAGE: RECONNECT] Reconnected successfully after $attempt attempts!")
            _connectionState.value = ServerConnectionState.CONNECTED
        }

        s.io().on(Manager.EVENT_RECONNECT_ERROR) { args ->
            val err = args.firstOrNull()?.toString() ?: "Reconnect failed"
            Log.e(TAG, "[STAGE: RECONNECT_ERROR] Reconnect error: $err")
        }
    }

    fun isConnected(): Boolean = socket?.connected() == true

    fun createRoom(
        playerName: String,
        uid: String,
        targetScore: Int = 300,
        callback: (SocketRoomResponse) -> Unit
    ) {
        val s = socket
        if (s == null || !s.connected()) {
            Log.e(TAG, "[STAGE 6: ROOM_OPERATION] Cannot create room: Socket is not connected!")
            callback(
                SocketRoomResponse(
                    success = false,
                    roomCode = null,
                    player = null,
                    sessionToken = null,
                    error = "Socket not connected. Please connect first."
                )
            )
            return
        }

        Log.i(TAG, "[STAGE 6: ROOM_OPERATION] Emitting 'create_room' (playerName: $playerName, uid: $uid)...")
        val payload = JSONObject().apply {
            put("playerName", playerName)
            put("uid", uid)
            put("targetScore", targetScore)
        }

        s.emit("create_room", payload, io.socket.client.Ack { args ->
            val resp = args.firstOrNull() as? JSONObject
            if (resp != null && resp.optBoolean("success", false)) {
                val data = resp.optJSONObject("data")
                val roomCode = data?.optString("roomCode")
                val sessionToken = data?.optString("sessionToken")
                Log.i(TAG, "[STAGE 6: ROOM_OPERATION] Room created successfully! 4-digit code: $roomCode")
                callback(
                    SocketRoomResponse(
                        success = true,
                        roomCode = roomCode,
                        player = null,
                        sessionToken = sessionToken,
                        targetScore = targetScore
                    )
                )
            } else {
                val errObj = resp?.optJSONObject("error")
                val errMsg = errObj?.optString("message") ?: "Failed to create room on server"
                Log.e(TAG, "[STAGE 6: ROOM_OPERATION] create_room failed: $errMsg")
                callback(
                    SocketRoomResponse(
                        success = false,
                        roomCode = null,
                        player = null,
                        sessionToken = null,
                        error = errMsg
                    )
                )
            }
        })
    }

    fun joinRoom(
        roomCode: String,
        playerName: String,
        uid: String,
        callback: (SocketRoomResponse) -> Unit
    ) {
        val s = socket
        if (s == null || !s.connected()) {
            Log.e(TAG, "[STAGE 6: ROOM_OPERATION] Cannot join room: Socket is not connected!")
            callback(
                SocketRoomResponse(
                    success = false,
                    roomCode = null,
                    player = null,
                    sessionToken = null,
                    error = "Socket not connected. Please connect first."
                )
            )
            return
        }

        Log.i(TAG, "[STAGE 6: ROOM_OPERATION] Emitting 'join_room' (roomCode: $roomCode, playerName: $playerName)...")
        val payload = JSONObject().apply {
            put("roomCode", roomCode)
            put("playerName", playerName)
            put("uid", uid)
        }

        s.emit("join_room", payload, io.socket.client.Ack { args ->
            val resp = args.firstOrNull() as? JSONObject
            if (resp != null && resp.optBoolean("success", false)) {
                val data = resp.optJSONObject("data")
                val code = data?.optString("roomCode") ?: roomCode
                val sessionToken = data?.optString("sessionToken")
                Log.i(TAG, "[STAGE 6: ROOM_OPERATION] Joined room $code successfully!")
                callback(
                    SocketRoomResponse(
                        success = true,
                        roomCode = code,
                        player = null,
                        sessionToken = sessionToken
                    )
                )
            } else {
                val errObj = resp?.optJSONObject("error")
                val errMsg = errObj?.optString("message") ?: "Failed to join room"
                Log.e(TAG, "[STAGE 6: ROOM_OPERATION] join_room failed: $errMsg")
                callback(
                    SocketRoomResponse(
                        success = false,
                        roomCode = null,
                        player = null,
                        sessionToken = null,
                        error = errMsg
                    )
                )
            }
        })
    }

    fun disconnect() {
        timeoutJob?.cancel()
        isConnecting.set(false)
        try {
            socket?.disconnect()
            socket?.off()
            socket = null
        } catch (e: Exception) {
            Log.e(TAG, "Error disconnecting socket", e)
        }
        _connectionState.value = ServerConnectionState.DISCONNECTED
    }
}
