package com.example.network

import android.util.Log
import com.example.model.ConnectionStatus
import com.example.model.ControllerType
import com.example.model.Player
import io.socket.client.IO
import io.socket.client.Socket as SocketIOSocket
import java.net.URI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

data class RoomResponse(
    val roomCode: String,
    val player: Player,
    val sessionToken: String,
    val players: List<Player>,
    val targetScore: Int,
    val isFull: Boolean
)

class MultiplayerClient {

    private val tag = "MultiplayerClient"
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    // Production Socket.IO server
    private val productionServerUrl = "https://mobile-card-game.onrender.com"
    private val configuredHost = com.example.BuildConfig.SERVER_URL.trim().ifEmpty { productionServerUrl }

    private var activeBaseUrl: String? = null

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .writeTimeout(8, TimeUnit.SECONDS)
        .pingInterval(10, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private var socket: SocketIOSocket? = null
    private val isWsConnected = AtomicBoolean(false)
    private var activeRoomCode: String? = null
    private var activeSessionToken: String? = null
    private var eventListener: ((event: String, data: JSONObject) -> Unit)? = null

    private val _pingMs = MutableStateFlow<Int?>(null)
    val pingMs: StateFlow<Int?> = _pingMs.asStateFlow()

    private var pingJob: Job? = null
    private var lastPingSentTime: Long = 0L

    /**
     * Resolves the working multiplayer backend URL by testing reachability
     */
    private suspend fun getBaseUrl(): String = withContext(Dispatchers.IO) {
        activeBaseUrl?.let { return@withContext it }

        val serverUrl = if (configuredHost.isNotEmpty() &&
            !configuredHost.contains("localhost") &&
            !configuredHost.contains("127.0.0.1") &&
            !configuredHost.contains("10.0.2.2")
        ) {
            configuredHost
        } else {
            productionServerUrl
        }
        val httpUrl = serverUrl.replace("wss://", "https://").replace("ws://", "http://").removeSuffix("/")

        try {
            val req = Request.Builder()
                .url("${httpUrl.removeSuffix("/")}/health")
                .get()
                .build()
            val resp = httpClient.newCall(req).execute()
            if (resp.isSuccessful) {
                val body = resp.body?.string() ?: ""
                if (body.contains("\"status\"") && body.contains("\"ok\"")) {
                    Log.i(tag, "Connected to backend at $httpUrl")
                    activeBaseUrl = httpUrl
                    return@withContext httpUrl
                }
            }
        } catch (e: Exception) {
            Log.d(tag, "Host $httpUrl not reachable: ${e.message}")
        }

        activeBaseUrl = httpUrl
        httpUrl
    }

    /**
     * Creates an authoritative multiplayer room on the central server
     */
    suspend fun createRoom(
        playerName: String,
        uid: String,
        targetScore: Int = 300
    ): Result<RoomResponse> = withContext(Dispatchers.IO) {
        try {
            val base = getBaseUrl()
            val payload = JSONObject().apply {
                put("playerName", playerName.ifBlank { "Player 1 (Host)" })
                put("uid", uid)
                put("targetScore", targetScore)
            }

            val request = Request.Builder()
                .url("$base/api/rooms/create")
                .post(payload.toString().toRequestBody(jsonMediaType))
                .build()

            val response = httpClient.newCall(request).execute()
            val bodyString = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                val errorMsg = try {
                    JSONObject(bodyString).optString("error", "Server returned ${response.code}")
                } catch (_: Exception) {
                    "Failed to create room (HTTP ${response.code})"
                }
                return@withContext Result.failure(Exception(errorMsg))
            }

            val json = JSONObject(bodyString)
            if (!json.optBoolean("success", false)) {
                return@withContext Result.failure(Exception(json.optString("error", "Failed to create room")))
            }

            val roomCode = json.getString("roomCode")
            val sessionToken = json.getString("sessionToken")
            val roomStateJson = json.getJSONObject("roomState")
            val playerJson = json.getJSONObject("player")

            val player = parsePlayer(playerJson)
            val players = parsePlayers(roomStateJson.optJSONArray("players"))
            val target = roomStateJson.optInt("targetScore", targetScore)
            val isFull = roomStateJson.optBoolean("isFull", false)

            activeRoomCode = roomCode
            activeSessionToken = sessionToken

            Result.success(
                RoomResponse(
                    roomCode = roomCode,
                    player = player,
                    sessionToken = sessionToken,
                    players = players,
                    targetScore = target,
                    isFull = isFull
                )
            )
        } catch (e: Exception) {
            Log.e(tag, "createRoom error: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Joins an existing authoritative room by 4-digit numeric code
     */
    suspend fun joinRoom(
        roomCode: String,
        playerName: String,
        uid: String
    ): Result<RoomResponse> = withContext(Dispatchers.IO) {
        try {
            val cleanCode = roomCode.trim()
            if (cleanCode.length != 4 || !cleanCode.all { it.isDigit() }) {
                return@withContext Result.failure(Exception("Room code must be exactly 4 digits."))
            }

            val base = getBaseUrl()
            val payload = JSONObject().apply {
                put("roomCode", cleanCode)
                put("playerName", playerName.ifBlank { "Player" })
                put("uid", uid)
            }

            val request = Request.Builder()
                .url("$base/api/rooms/join")
                .post(payload.toString().toRequestBody(jsonMediaType))
                .build()

            val response = httpClient.newCall(request).execute()
            val bodyString = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                val errorMsg = try {
                    JSONObject(bodyString).optString("error", "Room join failed")
                } catch (_: Exception) {
                    "Room join failed (HTTP ${response.code})"
                }
                return@withContext Result.failure(Exception(errorMsg))
            }

            val json = JSONObject(bodyString)
            if (!json.optBoolean("success", false)) {
                return@withContext Result.failure(Exception(json.optString("error", "Unable to join room.")))
            }

            val sessionToken = json.getString("sessionToken")
            val roomStateJson = json.getJSONObject("roomState")
            val playerJson = json.getJSONObject("player")

            val player = parsePlayer(playerJson)
            val players = parsePlayers(roomStateJson.optJSONArray("players"))
            val target = roomStateJson.optInt("targetScore", 300)
            val isFull = roomStateJson.optBoolean("isFull", false)

            activeRoomCode = cleanCode
            activeSessionToken = sessionToken

            Result.success(
                RoomResponse(
                    roomCode = cleanCode,
                    player = player,
                    sessionToken = sessionToken,
                    players = players,
                    targetScore = target,
                    isFull = isFull
                )
            )
        } catch (e: Exception) {
            Log.e(tag, "joinRoom error: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Adds an AI Bot to the next available seat
     */
    suspend fun addBot(roomCode: String): Result<List<Player>> = withContext(Dispatchers.IO) {
        try {
            val base = getBaseUrl()
            val payload = JSONObject().apply {
                put("roomCode", roomCode)
            }

            val request = Request.Builder()
                .url("$base/api/rooms/add-bot")
                .post(payload.toString().toRequestBody(jsonMediaType))
                .build()

            val response = httpClient.newCall(request).execute()
            val bodyString = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Failed to add bot"))
            }

            val json = JSONObject(bodyString)
            val roomStateJson = json.getJSONObject("roomState")
            val players = parsePlayers(roomStateJson.optJSONArray("players"))
            Result.success(players)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetches current authoritative room state
     */
    suspend fun fetchRoomState(roomCode: String): Result<List<Player>> = withContext(Dispatchers.IO) {
        try {
            val base = getBaseUrl()
            val request = Request.Builder()
                .url("$base/api/rooms/$roomCode")
                .get()
                .build()

            val response = httpClient.newCall(request).execute()
            val bodyString = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Room not found"))
            }

            val json = JSONObject(bodyString)
            val roomStateJson = json.getJSONObject("roomState")
            val players = parsePlayers(roomStateJson.optJSONArray("players"))
            Result.success(players)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Connects real WebSocket for bidirectional real-time room updates
     */
    fun connectWebSocket(
        roomCode: String,
        sessionToken: String,
        onEvent: (event: String, data: JSONObject) -> Unit
    ) {
        this.activeRoomCode = roomCode
        this.activeSessionToken = sessionToken
        this.eventListener = onEvent

        disconnectWebSocket()

        val serverHttpUrl = (activeBaseUrl ?: productionServerUrl)
            .replace("wss://", "https://")
            .replace("ws://", "http://")
            .removeSuffix("/")

        Log.i(tag, "Connecting Socket.IO client to $serverHttpUrl with path /socket.io for room $roomCode")

        val opts = IO.Options().apply {
            transports = arrayOf("websocket")
            path = "/socket.io"
            reconnection = true
            reconnectionAttempts = 5
            reconnectionDelay = 1500L
        }

        try {
            val s = IO.socket(URI.create(serverHttpUrl), opts)
            socket = s

            s.on(SocketIOSocket.EVENT_CONNECT) {
                isWsConnected.set(true)
                Log.i(tag, "[Socket.IO connect] Connected successfully to $serverHttpUrl for room $roomCode (socket ID: ${s.id()})")

                // Reconnect/Associate session with room
                val joinMsg = JSONObject().apply {
                    put("roomCode", roomCode)
                    put("sessionToken", sessionToken)
                }
                s.emit("reconnect_session", joinMsg)
            }

            s.on(SocketIOSocket.EVENT_DISCONNECT) { args ->
                isWsConnected.set(false)
                _pingMs.value = null
                Log.i(tag, "[Socket.IO disconnect] Disconnected: ${args.firstOrNull()}")
            }

            s.on(SocketIOSocket.EVENT_CONNECT_ERROR) { args ->
                isWsConnected.set(false)
                _pingMs.value = null
                val err = args.firstOrNull()
                val errMsg = when (err) {
                    is Throwable -> {
                        val cause = err.cause?.message?.let { " (Cause: $it)" } ?: ""
                        "${err.javaClass.simpleName}: ${err.message ?: "Connection error"}$cause"
                    }
                    else -> err?.toString() ?: "Unknown error"
                }
                Log.e(tag, "[Socket.IO connect_error] Connection to $serverHttpUrl failed: $errMsg", err as? Throwable)
            }

            val events = listOf(
                "room_state_updated",
                "player_joined",
                "player_reconnected",
                "session_restored",
                "player_disconnected",
                "player_timeout_bot_assigned",
                "error",
                "pong"
            )
            for (ev in events) {
                s.on(ev) { args ->
                    val data = args.firstOrNull() as? JSONObject ?: JSONObject()
                    if (ev == "pong") {
                        val clientTime = data.optLong("clientTime", 0L).takeIf { it > 0 } ?: lastPingSentTime
                        if (clientTime > 0) {
                            val latency = (System.currentTimeMillis() - clientTime).toInt().coerceAtLeast(1)
                            _pingMs.value = latency
                        }
                    }
                    Log.d(tag, "Socket.IO event received: $ev")
                    eventListener?.invoke(ev, data)
                }
            }

            s.connect()
        } catch (e: Exception) {
            Log.e(tag, "Error initializing Socket.IO client: ${e.message}", e)
        }
    }

    fun startPingMeasurement(scope: CoroutineScope) {
        stopPingMeasurement()
        pingJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                measurePing()
                delay(2500)
            }
        }
    }

    fun stopPingMeasurement() {
        pingJob?.cancel()
        pingJob = null
    }

    suspend fun measurePing() {
        val s = socket
        if (s != null && s.connected()) {
            val now = System.currentTimeMillis()
            lastPingSentTime = now
            val pingJson = JSONObject().apply {
                put("clientTime", now)
            }
            s.emit("ping", pingJson)
        } else {
            try {
                val base = getBaseUrl()
                val start = System.currentTimeMillis()
                val req = Request.Builder().url("$base/health").get().build()
                val resp = httpClient.newCall(req).execute()
                if (resp.isSuccessful) {
                    val latency = (System.currentTimeMillis() - start).toInt().coerceAtLeast(1)
                    _pingMs.value = latency
                } else {
                    _pingMs.value = null
                }
            } catch (_: Exception) {
                _pingMs.value = null
            }
        }
    }

    fun disconnectWebSocket() {
        stopPingMeasurement()
        try {
            socket?.disconnect()
            socket?.off()
            socket = null
            isWsConnected.set(false)
            _pingMs.value = null
        } catch (_: Exception) {}
    }

    // JSON Parsers
    fun parsePlayers(array: JSONArray?): List<Player> {
        if (array == null) return emptyList()
        val list = mutableListOf<Player>()
        for (i in 0 until array.length()) {
            if (array.isNull(i)) continue
            val obj = array.getJSONObject(i)
            list.add(parsePlayer(obj))
        }
        return list
    }

    fun parsePlayer(obj: JSONObject): Player {
        val id = obj.optInt("id", 1)
        val seatIndex = obj.optInt("seatIndex", 0)
        val name = obj.optString("name", "Player $id")
        val isHost = obj.optBoolean("isHost", false)
        val controllerStr = obj.optString("controllerType", "HUMAN")
        val controllerType = if (controllerStr.equals("BOT", ignoreCase = true)) ControllerType.BOT else ControllerType.HUMAN

        val statusStr = obj.optString("connectionStatus", "CONNECTED")
        val connectionStatus = when (statusStr.uppercase()) {
            "DISCONNECTED" -> ConnectionStatus.DISCONNECTED
            "RECONNECTING" -> ConnectionStatus.RECONNECTING
            "BOT_ACTIVE" -> ConnectionStatus.BOT_ACTIVE
            else -> ConnectionStatus.CONNECTED
        }

        val totalScore = obj.optInt("totalScore", 0)

        return Player(
            id = id,
            seatIndex = seatIndex,
            name = name,
            controllerType = controllerType,
            connectionStatus = connectionStatus,
            isHost = isHost,
            totalScore = totalScore
        )
    }
}
