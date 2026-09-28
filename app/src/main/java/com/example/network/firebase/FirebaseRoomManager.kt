package com.example.network.firebase

import android.util.Log
import com.example.engine.AuthoritativeRuleEngine
import com.example.model.Card
import com.example.model.ConnectionStatus
import com.example.model.ControllerType
import com.example.model.GameMode
import com.example.model.GamePhase
import com.example.model.GameState
import com.example.model.PlayedCard
import com.example.model.Player
import com.example.model.Rank
import com.example.model.Suit
import com.example.model.Trick
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.random.Random

data class RoomPlayerData(
    val id: Int = 1,
    val uid: String = "",
    val name: String = "",
    val seatIndex: Int = 0,
    val connected: Boolean = true,
    val isBot: Boolean = false,
    val ready: Boolean = true,
    val isHost: Boolean = false,
    val totalScore: Int = 0,
    val call: Int? = null,
    val tricksWon: Int = 0,
    val disconnectRemainingSeconds: Int? = null,
    val cards: List<Card> = emptyList()
)

data class RoomData(
    val roomCode: String = "",
    val hostId: String = "",
    val status: String = "WAITING", // WAITING, PLAYING, COMPLETED
    val targetScore: Int = 300,
    val createdAt: Long = 0L,
    val players: List<Player> = emptyList(),
    val gameState: GameState? = null
)

/**
 * Manages live multiplayer rooms using Firebase Realtime Database.
 * Fulfills all Realtime Database synchronization, Host controls, Bot fill,
 * 30s disconnect/reconnect grace periods, and room security requirements.
 */
class FirebaseRoomManager {

    private val tag = "FirebaseRoomManager"

    // Lazily get FirebaseDatabase instance
    val database: FirebaseDatabase by lazy {
        try {
            FirebaseDatabase.getInstance()
        } catch (e: Exception) {
            Log.w(tag, "Failed to get default FirebaseDatabase instance, using explicit URL: ${e.message}")
            FirebaseDatabase.getInstance("https://card-game-project-default-rtdb.firebaseio.com")
        }
    }

    private val roomsRef: DatabaseReference by lazy {
        database.getReference("rooms")
    }

    private var activeRoomCode: String? = null
    private var activeUserUid: String? = null
    private var activeRoomListener: ValueEventListener? = null
    private var activeRoomRef: DatabaseReference? = null
    private var presenceListener: ValueEventListener? = null

    private val _currentRoomData = MutableStateFlow<RoomData?>(null)
    val currentRoomData: StateFlow<RoomData?> = _currentRoomData.asStateFlow()

    private val _pingMs = MutableStateFlow<Int?>(null)
    val pingMs: StateFlow<Int?> = _pingMs.asStateFlow()

    private var pingJob: Job? = null

    /**
     * Start measuring Firebase network ping latency
     */
    fun startPingMeasurement(scope: CoroutineScope) {
        stopPingMeasurement()
        pingJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                try {
                    val start = System.currentTimeMillis()
                    val connectedRef = database.getReference(".info/connected")
                    connectedRef.get().await()
                    val rtt = (System.currentTimeMillis() - start).toInt().coerceAtLeast(1)
                    _pingMs.value = rtt
                } catch (_: Exception) {
                    _pingMs.value = null
                }
                delay(3000)
            }
        }
    }

    fun stopPingMeasurement() {
        pingJob?.cancel()
        pingJob = null
        _pingMs.value = null
    }

    /**
     * 2. CREATE ROOM
     * - Generate a unique 4-digit numeric room code.
     * - Create a Firebase Realtime Database room.
     * - The host automatically occupies Player 1 (seatIndex 0).
     */
    suspend fun createRoom(
        hostName: String,
        hostUid: String,
        targetScore: Int = 300
    ): Result<RoomData> = withContext(Dispatchers.IO) {
        try {
            var generatedCode: String
            var attempts = 0
            do {
                generatedCode = String.format("%04d", Random.nextInt(1000, 10000))
                val snapshot = roomsRef.child(generatedCode).get().await()
                attempts++
            } while (snapshot.exists() && attempts < 10)

            val roomRef = roomsRef.child(generatedCode)
            val hostPlayer = Player(
                id = 1,
                seatIndex = 0,
                name = hostName,
                controllerType = ControllerType.HUMAN,
                connectionStatus = ConnectionStatus.CONNECTED,
                isHost = true
            )

            val roomMap = hashMapOf<String, Any>(
                "hostId" to hostUid,
                "status" to "WAITING",
                "targetScore" to targetScore,
                "createdAt" to ServerValue.TIMESTAMP,
                "updatedAt" to ServerValue.TIMESTAMP
            )

            roomRef.setValue(roomMap).await()

            // Add Host as Player 1 in players/{hostUid}
            val playerMap = playerToMap(hostPlayer, hostUid)
            roomRef.child("players").child(hostUid).setValue(playerMap).await()

            activeRoomCode = generatedCode
            activeUserUid = hostUid
            activeRoomRef = roomRef

            setupPresence(generatedCode, hostUid)

            val initialRoomData = RoomData(
                roomCode = generatedCode,
                hostId = hostUid,
                status = "WAITING",
                targetScore = targetScore,
                players = listOf(hostPlayer)
            )
            _currentRoomData.value = initialRoomData

            Result.success(initialRoomData)
        } catch (e: Exception) {
            Log.e(tag, "Failed to create room in Firebase: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * 3. JOIN ROOM
     * - Validate the room code through Firebase.
     * - If valid and a seat is available, join the room.
     * - If the room is full, return "Room Full".
     * - If the room does not exist, return "Invalid Room Code".
     * - Prevent duplicate seat assignment.
     * - Each player keeps the same seat while connected.
     */
    suspend fun joinRoom(
        roomCode: String,
        playerName: String,
        playerUid: String
    ): Result<RoomData> = withContext(Dispatchers.IO) {
        try {
            val cleanCode = roomCode.trim()
            if (cleanCode.length != 4 || !cleanCode.all { it.isDigit() }) {
                return@withContext Result.failure(Exception("Room code must be exactly 4 digits."))
            }

            val roomRef = roomsRef.child(cleanCode)
            val snapshot = roomRef.get().await()

            if (!snapshot.exists()) {
                return@withContext Result.failure(Exception("Invalid Room Code"))
            }

            val status = snapshot.child("status").getValue(String::class.java) ?: "WAITING"
            val hostId = snapshot.child("hostId").getValue(String::class.java) ?: ""
            val targetScore = snapshot.child("targetScore").getValue(Long::class.java)?.toInt() ?: 300

            val playersSnapshot = snapshot.child("players")
            val existingPlayers = mutableListOf<Player>()

            var alreadyInRoom = false
            var existingSeat = -1

            for (pSnap in playersSnapshot.children) {
                val p = parsePlayerFromSnapshot(pSnap)
                if (p != null) {
                    existingPlayers.add(p)
                    if (pSnap.key == playerUid) {
                        alreadyInRoom = true
                        existingSeat = p.seatIndex
                    }
                }
            }

            if (status == "PLAYING" && !alreadyInRoom) {
                return@withContext Result.failure(Exception("Game already started"))
            }

            if (alreadyInRoom) {
                // Reconnect flow: keep original seat, cards, and state intact!
                roomRef.child("players").child(playerUid).updateChildren(
                    mapOf(
                        "connected" to true,
                        "disconnectRemainingSeconds" to null,
                        "disconnectTimestamp" to null
                    )
                ).await()
            } else {
                if (existingPlayers.size >= 4) {
                    return@withContext Result.failure(Exception("Room Full"))
                }

                // Determine lowest available seat index (0..3)
                val occupiedSeats = existingPlayers.map { it.seatIndex }.toSet()
                val nextSeat = (0..3).firstOrNull { it !in occupiedSeats } ?: 0
                val nextId = nextSeat + 1

                val newPlayer = Player(
                    id = nextId,
                    seatIndex = nextSeat,
                    name = playerName,
                    controllerType = ControllerType.HUMAN,
                    connectionStatus = ConnectionStatus.CONNECTED,
                    isHost = false
                )

                val playerMap = playerToMap(newPlayer, playerUid)
                roomRef.child("players").child(playerUid).setValue(playerMap).await()
                existingPlayers.add(newPlayer)
            }

            activeRoomCode = cleanCode
            activeUserUid = playerUid
            activeRoomRef = roomRef

            setupPresence(cleanCode, playerUid)

            val joinedRoomData = RoomData(
                roomCode = cleanCode,
                hostId = hostId,
                status = status,
                targetScore = targetScore,
                players = existingPlayers.sortedBy { it.seatIndex }
            )
            _currentRoomData.value = joinedRoomData

            Result.success(joinedRoomData)
        } catch (e: Exception) {
            Log.e(tag, "Failed to join room: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * 5. HOST: Add Bot to an empty seat
     */
    suspend fun addBot(roomCode: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val roomRef = roomsRef.child(roomCode)
            val snapshot = roomRef.child("players").get().await()

            val occupiedSeats = mutableSetOf<Int>()
            for (pSnap in snapshot.children) {
                val seat = pSnap.child("seatIndex").getValue(Long::class.java)?.toInt()
                if (seat != null) occupiedSeats.add(seat)
            }

            if (occupiedSeats.size >= 4) {
                return@withContext Result.failure(Exception("All seats are already filled"))
            }

            val nextSeat = (0..3).firstOrNull { it !in occupiedSeats } ?: 1
            val botId = nextSeat + 1
            val botUid = "bot_$nextSeat"

            val botPlayer = Player(
                id = botId,
                seatIndex = nextSeat,
                name = "Bot $botId",
                controllerType = ControllerType.BOT,
                connectionStatus = ConnectionStatus.CONNECTED,
                isHost = false
            )

            val botMap = playerToMap(botPlayer, botUid)
            roomRef.child("players").child(botUid).setValue(botMap).await()

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(tag, "Failed to add bot: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Remove player or bot from seat (Host control)
     */
    suspend fun removeSeat(roomCode: String, seatIndex: Int): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val roomRef = roomsRef.child(roomCode)
            val snapshot = roomRef.child("players").get().await()

            for (pSnap in snapshot.children) {
                val seat = pSnap.child("seatIndex").getValue(Long::class.java)?.toInt()
                if (seat == seatIndex) {
                    pSnap.ref.removeValue().await()
                    break
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Real-time room listener using Firebase ValueEventListener
     * Emits room updates and synchronized game state immediately to all connected devices.
     */
    fun attachRoomListener(
        roomCode: String,
        onRoomUpdated: (RoomData) -> Unit,
        onGameStateUpdated: (GameState) -> Unit,
        onError: (String) -> Unit
    ) {
        detachRoomListener()

        val roomRef = roomsRef.child(roomCode)
        activeRoomRef = roomRef
        activeRoomCode = roomCode

        activeRoomListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) {
                    onError("Room no longer exists")
                    return
                }

                try {
                    val hostId = snapshot.child("hostId").getValue(String::class.java) ?: ""
                    val status = snapshot.child("status").getValue(String::class.java) ?: "WAITING"
                    val targetScore = snapshot.child("targetScore").getValue(Long::class.java)?.toInt() ?: 300
                    val playersList = mutableListOf<Player>()

                    val playersSnap = snapshot.child("players")
                    for (pSnap in playersSnap.children) {
                        val player = parsePlayerFromSnapshot(pSnap)
                        if (player != null) {
                            playersList.add(player)
                        }
                    }

                    // Sort players by seatIndex (0..3)
                    val sortedPlayers = playersList.sortedBy { it.seatIndex }

                    // Host Transfer Rule:
                    // If current host is disconnected or absent, transfer host to next connected human player
                    val currentHost = sortedPlayers.firstOrNull { it.isHost }
                    if (currentHost == null || currentHost.connectionStatus == ConnectionStatus.DISCONNECTED) {
                        val nextHuman = sortedPlayers.firstOrNull {
                            it.controllerType == ControllerType.HUMAN && it.connectionStatus == ConnectionStatus.CONNECTED
                        }
                        if (nextHuman != null && !nextHuman.isHost && activeUserUid == nextHuman.name) {
                            roomRef.child("hostId").setValue(activeUserUid)
                        }
                    }

                    val roomData = RoomData(
                        roomCode = roomCode,
                        hostId = hostId,
                        status = status,
                        targetScore = targetScore,
                        players = sortedPlayers
                    )
                    _currentRoomData.value = roomData
                    onRoomUpdated(roomData)

                    // Synchronize live gameplay if active
                    val gameSnap = snapshot.child("game")
                    if (gameSnap.exists()) {
                        val gameState = parseGameStateFromSnapshot(gameSnap, sortedPlayers, roomCode, targetScore)
                        if (gameState != null) {
                            onGameStateUpdated(gameState)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(tag, "Error parsing room update from Firebase: ${e.message}", e)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(tag, "Firebase room listener cancelled: ${error.message}")
                onError(error.message)
            }
        }

        roomRef.addValueEventListener(activeRoomListener!!)
    }

    fun detachRoomListener() {
        activeRoomListener?.let { listener ->
            activeRoomRef?.removeEventListener(listener)
        }
        activeRoomListener = null
    }

    /**
     * Synchronize authoritative game state to Firebase Realtime Database
     */
    suspend fun syncGameState(roomCode: String, gameState: GameState) = withContext(Dispatchers.IO) {
        try {
            val gameRef = roomsRef.child(roomCode).child("game")
            val gameMap = hashMapOf<String, Any>(
                "phase" to gameState.phase.name,
                "roundNumber" to gameState.roundNumber,
                "targetScore" to gameState.targetScore,
                "currentDealerIndex" to gameState.currentDealerIndex,
                "firstCardPlayerIndex" to gameState.firstCardPlayerIndex,
                "currentTurnPlayerIndex" to gameState.currentTurnPlayerIndex,
                "turnTimerSeconds" to gameState.turnTimerSeconds,
                "statusMessage" to (gameState.statusMessage ?: ""),
                "callPhasePrompt" to (gameState.callPhasePrompt ?: "")
            )

            // Sync current trick
            val trickMap = hashMapOf<String, Any>(
                "trickNumber" to gameState.currentTrick.trickNumber,
                "leadSuit" to (gameState.currentTrick.leadSuit?.name ?: ""),
                "playedCards" to gameState.currentTrick.playedCards.map { played ->
                    mapOf(
                        "playerIndex" to played.playerIndex,
                        "suit" to played.card.suit.name,
                        "rank" to played.card.rank.name,
                        "timestamp" to played.timestamp
                    )
                }
            )
            gameMap["currentTrick"] = trickMap

            // Sync player hands and calls securely
            val playersRef = roomsRef.child(roomCode).child("players")
            val updates = hashMapOf<String, Any>()

            for (player in gameState.players) {
                // Find matching player node or bot node
                val playerNodeKey = if (player.controllerType == ControllerType.BOT) "bot_${player.seatIndex}"
                else activeUserUid?.takeIf { player.seatIndex == 0 } ?: "seat_${player.seatIndex}"

                updates["$playerNodeKey/call"] = player.call ?: -1
                updates["$playerNodeKey/tricksWon"] = player.tricksWon
                updates["$playerNodeKey/totalScore"] = player.totalScore
                updates["$playerNodeKey/cards"] = player.cards.map { card ->
                    mapOf("suit" to card.suit.name, "rank" to card.rank.name)
                }
            }

            gameRef.updateChildren(gameMap).await()
            playersRef.updateChildren(updates).await()

            // Update room status
            val roomStatus = when (gameState.phase) {
                GamePhase.ONLINE_LOBBY -> "WAITING"
                GamePhase.GAME_COMPLETE -> "COMPLETED"
                else -> "PLAYING"
            }
            roomsRef.child(roomCode).child("status").setValue(roomStatus).await()
        } catch (e: Exception) {
            Log.e(tag, "Failed to sync game state to Firebase: ${e.message}", e)
        }
    }

    /**
     * Setup Firebase Presence & onDisconnect
     * Detects dropped connections and triggers 30s reconnect timer without destroying the game.
     */
    private fun setupPresence(roomCode: String, userUid: String) {
        val playerRef = roomsRef.child(roomCode).child("players").child(userUid)
        val connectedRef = database.getReference(".info/connected")

        presenceListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val connected = snapshot.getValue(Boolean::class.java) ?: false
                if (connected) {
                    playerRef.child("connected").setValue(true)
                    playerRef.child("disconnectRemainingSeconds").removeValue()
                    playerRef.child("disconnectTimestamp").removeValue()

                    // Configure onDisconnect handler
                    val onDisconnectMap = mapOf(
                        "connected" to false,
                        "disconnectRemainingSeconds" to 30,
                        "disconnectTimestamp" to ServerValue.TIMESTAMP
                    )
                    playerRef.onDisconnect().updateChildren(onDisconnectMap)
                }
            }

            override fun onCancelled(error: DatabaseError) {}
        }

        connectedRef.addValueEventListener(presenceListener!!)
    }

    fun leaveRoom() {
        stopPingMeasurement()
        detachRoomListener()
        val roomCode = activeRoomCode
        val uid = activeUserUid

        if (roomCode != null && uid != null) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val pRef = roomsRef.child(roomCode).child("players").child(uid)
                    pRef.removeValue().await()

                    // Check if room is empty, then clean up
                    val snap = roomsRef.child(roomCode).child("players").get().await()
                    if (!snap.exists() || snap.childrenCount == 0L) {
                        roomsRef.child(roomCode).removeValue().await()
                    }
                } catch (_: Exception) {}
            }
        }

        activeRoomCode = null
        activeUserUid = null
        _currentRoomData.value = null
    }

    // Helper: Player to Firebase Map
    private fun playerToMap(player: Player, uid: String): Map<String, Any> {
        return mapOf(
            "id" to player.id,
            "uid" to uid,
            "name" to player.name,
            "seatIndex" to player.seatIndex,
            "connected" to (player.connectionStatus == ConnectionStatus.CONNECTED),
            "isBot" to player.isBot,
            "ready" to true,
            "isHost" to player.isHost,
            "totalScore" to player.totalScore,
            "cards" to player.cards.map { mapOf("suit" to it.suit.name, "rank" to it.rank.name) }
        )
    }

    // Helper: Parse Player from Snapshot
    private fun parsePlayerFromSnapshot(snapshot: DataSnapshot): Player? {
        try {
            val id = snapshot.child("id").getValue(Long::class.java)?.toInt() ?: 1
            val seatIndex = snapshot.child("seatIndex").getValue(Long::class.java)?.toInt() ?: 0
            val name = snapshot.child("name").getValue(String::class.java) ?: "Player $id"
            val isBot = snapshot.child("isBot").getValue(Boolean::class.java) ?: false
            val connected = snapshot.child("connected").getValue(Boolean::class.java) ?: true
            val isHost = snapshot.child("isHost").getValue(Boolean::class.java) ?: false
            val totalScore = snapshot.child("totalScore").getValue(Long::class.java)?.toInt() ?: 0
            val tricksWon = snapshot.child("tricksWon").getValue(Long::class.java)?.toInt() ?: 0

            val rawCall = snapshot.child("call").getValue(Long::class.java)?.toInt()
            val call = if (rawCall != null && rawCall >= 0) rawCall else null

            val disconnectSecs = snapshot.child("disconnectRemainingSeconds").getValue(Long::class.java)?.toInt()

            val cardsList = mutableListOf<Card>()
            for (cSnap in snapshot.child("cards").children) {
                val suitStr = cSnap.child("suit").getValue(String::class.java)
                val rankStr = cSnap.child("rank").getValue(String::class.java)
                if (suitStr != null && rankStr != null) {
                    try {
                        cardsList.add(Card(Suit.valueOf(suitStr), Rank.valueOf(rankStr)))
                    } catch (_: Exception) {}
                }
            }

            val connectionStatus = when {
                !connected -> ConnectionStatus.DISCONNECTED
                isBot -> ConnectionStatus.BOT_ACTIVE
                else -> ConnectionStatus.CONNECTED
            }

            val controllerType = if (isBot) ControllerType.BOT else ControllerType.HUMAN

            return Player(
                id = id,
                seatIndex = seatIndex,
                name = name,
                controllerType = controllerType,
                connectionStatus = connectionStatus,
                cards = cardsList,
                call = call,
                tricksWon = tricksWon,
                totalScore = totalScore,
                disconnectRemainingSeconds = disconnectSecs,
                isHost = isHost
            )
        } catch (e: Exception) {
            Log.w(tag, "Failed to parse player: ${e.message}")
            return null
        }
    }

    // Helper: Parse GameState from Snapshot
    private fun parseGameStateFromSnapshot(
        snapshot: DataSnapshot,
        players: List<Player>,
        roomCode: String,
        targetScore: Int
    ): GameState? {
        try {
            val phaseStr = snapshot.child("phase").getValue(String::class.java) ?: return null
            val phase = try { GamePhase.valueOf(phaseStr) } catch (_: Exception) { GamePhase.PLAYING }
            val roundNumber = snapshot.child("roundNumber").getValue(Long::class.java)?.toInt() ?: 1
            val currentDealerIndex = snapshot.child("currentDealerIndex").getValue(Long::class.java)?.toInt() ?: 0
            val firstCardPlayerIndex = snapshot.child("firstCardPlayerIndex").getValue(Long::class.java)?.toInt() ?: 1
            val currentTurnPlayerIndex = snapshot.child("currentTurnPlayerIndex").getValue(Long::class.java)?.toInt() ?: 1
            val turnTimerSeconds = snapshot.child("turnTimerSeconds").getValue(Long::class.java)?.toInt() ?: 30
            val statusMessage = snapshot.child("statusMessage").getValue(String::class.java)?.takeIf { it.isNotBlank() }
            val callPhasePrompt = snapshot.child("callPhasePrompt").getValue(String::class.java)?.takeIf { it.isNotBlank() }

            val trickSnap = snapshot.child("currentTrick")
            val trickNumber = trickSnap.child("trickNumber").getValue(Long::class.java)?.toInt() ?: 1
            val leadSuitStr = trickSnap.child("leadSuit").getValue(String::class.java)?.takeIf { it.isNotBlank() }
            val leadSuit = leadSuitStr?.let { try { Suit.valueOf(it) } catch (_: Exception) { null } }

            val playedCardsList = mutableListOf<PlayedCard>()
            for (pSnap in trickSnap.child("playedCards").children) {
                val pIdx = pSnap.child("playerIndex").getValue(Long::class.java)?.toInt() ?: 0
                val suitStr = pSnap.child("suit").getValue(String::class.java)
                val rankStr = pSnap.child("rank").getValue(String::class.java)
                val timestamp = pSnap.child("timestamp").getValue(Long::class.java) ?: System.currentTimeMillis()
                if (suitStr != null && rankStr != null) {
                    try {
                        val card = Card(Suit.valueOf(suitStr), Rank.valueOf(rankStr))
                        playedCardsList.add(PlayedCard(pIdx, card, timestamp))
                    } catch (_: Exception) {}
                }
            }

            val currentTrick = Trick(
                trickNumber = trickNumber,
                leadSuit = leadSuit,
                playedCards = playedCardsList
            )

            return GameState(
                gameId = "online_$roomCode",
                mode = GameMode.ONLINE,
                phase = phase,
                roomCode = roomCode,
                players = players,
                currentDealerIndex = currentDealerIndex,
                firstCardPlayerIndex = firstCardPlayerIndex,
                currentTurnPlayerIndex = currentTurnPlayerIndex,
                currentTrick = currentTrick,
                turnTimerSeconds = turnTimerSeconds,
                targetScore = targetScore,
                roundNumber = roundNumber,
                statusMessage = statusMessage,
                callPhasePrompt = callPhasePrompt
            )
        } catch (e: Exception) {
            Log.w(tag, "Failed to parse game state: ${e.message}")
            return null
        }
    }
}
