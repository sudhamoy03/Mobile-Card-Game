package com.example.viewmodel

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.SoundHapticManager
import com.example.auth.AuthManager
import com.example.auth.AuthState
import com.example.auth.GoogleSignInHelper
import com.example.data.AppDatabase
import com.example.data.AppSettings
import com.example.data.AppThemeMode
import com.example.data.CompletedGameEntity
import com.example.data.GameRepository
import com.example.data.SettingsRepository
import com.example.engine.AuthoritativeRuleEngine
import com.example.engine.BotEngine
import com.example.model.Card
import com.example.model.ConnectionStatus
import com.example.model.ControllerType
import com.example.model.GameMode
import com.example.model.GamePhase
import com.example.model.GameState
import com.example.model.PlayedCard
import com.example.model.Player
import com.example.model.Trick
import com.example.network.firebase.FirebaseRoomManager
import com.example.network.SocketIOManager
import com.example.network.ServerConnectionState
import com.example.profile.ProfileRepository
import com.example.profile.UserProfile
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import com.example.network.MultiplayerClient
import org.json.JSONObject
import java.util.UUID
import kotlin.random.Random

class GameViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    val gameRepository = GameRepository(db.gameDao())
    val settingsRepository = SettingsRepository(application)
    val soundHapticManager = SoundHapticManager(application)
    val authManager = AuthManager(application)
    val profileRepository = ProfileRepository(application)
    val googleSignInHelper = GoogleSignInHelper(application)

    private val _authState = MutableStateFlow<AuthState>(AuthState.InitialLoading)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _userProfile = MutableStateFlow<UserProfile?>(null)
    val userProfile: StateFlow<UserProfile?> = _userProfile.asStateFlow()

    private val _authLoading = MutableStateFlow(false)
    val authLoading: StateFlow<Boolean> = _authLoading.asStateFlow()

    private val _authErrorMessage = MutableStateFlow<String?>(null)
    val authErrorMessage: StateFlow<String?> = _authErrorMessage.asStateFlow()

    private val _gameState = MutableStateFlow(GameState(phase = GamePhase.OPENING_ANIMATION))
    val gameState: StateFlow<GameState> = _gameState.asStateFlow()

    private val _hasSavedGame = MutableStateFlow(false)
    val hasSavedGame: StateFlow<Boolean> = _hasSavedGame.asStateFlow()

    val settings: StateFlow<AppSettings> = settingsRepository.settings

    val completedGames: StateFlow<List<CompletedGameEntity>> = MutableStateFlow(emptyList())

    val firebaseRoomManager = FirebaseRoomManager()
    val multiplayerClient = MultiplayerClient()
    val socketIOManager = SocketIOManager()
    val socketConnectionState: StateFlow<ServerConnectionState> = socketIOManager.connectionState
    val socketErrorMessage: StateFlow<String?> = socketIOManager.lastErrorMessage

    fun connectToSocketServer(onConnected: (() -> Unit)? = null) {
        socketIOManager.connect(viewModelScope, onConnected)
    }

    val networkPingMs: StateFlow<Int?> = firebaseRoomManager.pingMs
    private var onlineSessionToken: String? = null
    private val _isConnectingOnline = MutableStateFlow(false)
    val isConnectingOnline: StateFlow<Boolean> = _isConnectingOnline.asStateFlow()

    private var turnTimerJob: Job? = null
    private var botActionJob: Job? = null
    private var disconnectTimerJob: Job? = null

    init {
        checkInitialAuthState()

        // Sync sound and haptics preferences
        viewModelScope.launch {
            settings.collectLatest { s ->
                soundHapticManager.isMasterSoundEnabled = s.masterSound
                soundHapticManager.isActionSoundEnabled = s.actionSound
                soundHapticManager.isWinnerMusicEnabled = s.winnerMusic
                soundHapticManager.soundVolume = s.soundVolume
                soundHapticManager.isHapticsEnabled = s.hapticsEnabled
                soundHapticManager.hapticStrength = s.hapticStrength
            }
        }

        // Check for active saved game
        viewModelScope.launch {
            gameRepository.activeGameFlow.collectLatest { savedState ->
                _hasSavedGame.value = savedState != null && savedState.players.size == 4 && savedState.phase != GamePhase.GAME_COMPLETE
            }
        }
    }

    fun checkInitialAuthState() {
        viewModelScope.launch {
            _authLoading.value = true
            try {
                android.util.Log.d("AuthDebug", "Auth state checking")
                val user = authManager.awaitInitialAuthState()

                if (user != null) {
                    val uid = user.uid
                    val provider = if (user.isAnonymous) "anonymous" else "google"
                    android.util.Log.d("AuthDebug", "Auth state changed")
                    android.util.Log.d("AuthDebug", "Current UID: $uid")
                    android.util.Log.d("AuthDebug", "Provider: $provider")
                    android.util.Log.d("AuthDebug", "Profile loading")

                    val profile = profileRepository.getUserProfile(uid)
                    if (profile != null) {
                        android.util.Log.d("AuthDebug", "Profile loaded")
                        android.util.Log.d("AuthDebug", "Navigating to Home")
                        _userProfile.value = profile
                        _authState.value = AuthState.Authenticated(profile, user.isAnonymous)
                    } else {
                        val suggestedName = user.displayName?.takeIf { it.isNotBlank() }
                            ?: if (user.isAnonymous) "Guest Player" else "Player"
                        _authState.value = AuthState.NeedsProfile(user, suggestedName)
                    }
                } else {
                    android.util.Log.d("AuthDebug", "Current UID: null (unauthenticated)")
                    _authState.value = AuthState.Unauthenticated
                }
            } catch (e: Exception) {
                android.util.Log.e("AuthDebug", "Error in checkInitialAuthState: ${e.message}")
                _authState.value = AuthState.Unauthenticated
            } finally {
                _authLoading.value = false
            }
        }
    }

    fun signInWithGoogle(idToken: String) {
        viewModelScope.launch {
            _authLoading.value = true
            _authErrorMessage.value = null
            try {
                val user = authManager.signInWithGoogle(idToken)
                val uid = user.uid
                android.util.Log.d("AuthDebug", "Current UID: $uid")
                android.util.Log.d("AuthDebug", "Provider: google")
                android.util.Log.d("AuthDebug", "Profile loading")
                val profile = profileRepository.getUserProfile(uid)
                if (profile != null) {
                    android.util.Log.d("AuthDebug", "Profile loaded")
                    android.util.Log.d("AuthDebug", "Navigating to Home")
                    _userProfile.value = profile
                    _authState.value = AuthState.Authenticated(profile, false)
                } else {
                    val suggestedName = user.displayName?.takeIf { it.isNotBlank() } ?: "Player"
                    _authState.value = AuthState.NeedsProfile(user, suggestedName)
                }
            } catch (e: Exception) {
                android.util.Log.e("AuthDebug", "Google Sign-In failed: ${e.message}")
                _authErrorMessage.value = e.message ?: "Google Sign-In failed"
                _authState.value = AuthState.Unauthenticated
            } finally {
                _authLoading.value = false
            }
        }
    }

    fun signInAnonymously() {
        viewModelScope.launch {
            _authLoading.value = true
            _authErrorMessage.value = null
            try {
                val user = authManager.signInAnonymously()
                val uid = user.uid
                android.util.Log.d("AuthDebug", "Current UID: $uid")
                android.util.Log.d("AuthDebug", "Provider: anonymous")
                android.util.Log.d("AuthDebug", "Profile loading")
                val profile = profileRepository.getUserProfile(uid)
                if (profile != null) {
                    android.util.Log.d("AuthDebug", "Profile loaded")
                    android.util.Log.d("AuthDebug", "Navigating to Home")
                    _userProfile.value = profile
                    _authState.value = AuthState.Authenticated(profile, true)
                } else {
                    _authState.value = AuthState.NeedsProfile(user, "Guest Player")
                }
            } catch (e: Exception) {
                android.util.Log.e("AuthDebug", "Guest Sign-In failed: ${e.message}")
                _authErrorMessage.value = e.message ?: "Guest Sign-In failed"
                _authState.value = AuthState.Unauthenticated
            } finally {
                _authLoading.value = false
            }
        }
    }

    fun createProfile(
        displayName: String,
        playerId: String,
        avatarType: String,
        avatarId: String
    ) {
        viewModelScope.launch {
            _authLoading.value = true
            _authErrorMessage.value = null
            try {
                val user = authManager.currentUser
                val uid = user?.uid ?: "user_${UUID.randomUUID().toString().take(8)}"
                val accountType = if (authManager.isGuest) UserProfile.ACCOUNT_TYPE_GUEST else UserProfile.ACCOUNT_TYPE_GOOGLE
                val profile = profileRepository.createProfile(
                    uid = uid,
                    displayName = displayName,
                    desiredPlayerId = playerId,
                    avatarType = avatarType,
                    avatarId = avatarId,
                    avatarUrl = null,
                    accountType = accountType
                )
                _userProfile.value = profile
                _authState.value = AuthState.Authenticated(profile, authManager.isGuest)
            } catch (e: Exception) {
                _authErrorMessage.value = e.message ?: "Failed to create profile"
            } finally {
                _authLoading.value = false
            }
        }
    }

    fun updateProfile(
        newName: String,
        newPlayerId: String,
        avatarType: String,
        avatarId: String,
        customBitmap: Bitmap?
    ) {
        val current = _userProfile.value ?: return
        viewModelScope.launch {
            _authLoading.value = true
            _authErrorMessage.value = null
            try {
                var customUrl: String? = null
                if (avatarType == UserProfile.AVATAR_TYPE_CUSTOM && customBitmap != null) {
                    customUrl = profileRepository.uploadCustomAvatar(current.uid, customBitmap)
                }
                val updated = profileRepository.updateProfile(
                    currentProfile = current,
                    newDisplayName = newName,
                    newPlayerId = newPlayerId,
                    newAvatarType = avatarType,
                    newAvatarId = avatarId,
                    newAvatarUrl = customUrl
                )
                _userProfile.value = updated
                _authState.value = AuthState.Authenticated(updated, authManager.isGuest)
            } catch (e: Exception) {
                _authErrorMessage.value = e.message ?: "Failed to update profile"
            } finally {
                _authLoading.value = false
            }
        }
    }

    fun linkGoogleAccount(idToken: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            _authLoading.value = true
            _authErrorMessage.value = null
            try {
                authManager.linkGoogleAccount(idToken)
                val current = _userProfile.value
                if (current != null) {
                    val updated = current.copy(
                        accountType = UserProfile.ACCOUNT_TYPE_GOOGLE,
                        updatedAt = System.currentTimeMillis()
                    )
                    profileRepository.saveUserProfile(updated)
                    _userProfile.value = updated
                    _authState.value = AuthState.Authenticated(updated, false)
                }
                onResult(true, "Google Account successfully linked!")
            } catch (e: Exception) {
                val msg = e.message ?: "Failed to link Google account"
                _authErrorMessage.value = msg
                onResult(false, msg)
            } finally {
                _authLoading.value = false
            }
        }
    }

    fun signOut() {
        authManager.signOut()
        _userProfile.value = null
        _authState.value = AuthState.Unauthenticated
        navigateToHome()
    }

    fun onOpeningAnimationComplete() {
        if (_gameState.value.phase == GamePhase.OPENING_ANIMATION) {
            _gameState.value = _gameState.value.copy(phase = GamePhase.HOME)
        }
    }

    fun navigateToHome() {
        stopTimers()
        firebaseRoomManager.leaveRoom()
        multiplayerClient.disconnectWebSocket()
        _gameState.value = _gameState.value.copy(phase = GamePhase.HOME)
    }

    fun navigateToLocalSetup() {
        stopTimers()
        _gameState.value = _gameState.value.copy(
            mode = GameMode.LOCAL,
            phase = GamePhase.LOCAL_SETUP
        )
    }

    fun createOnlineRoom(onResult: (Boolean, String) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            _isConnectingOnline.value = true
            stopTimers()
            val user = userProfile.value
            val name = user?.displayName?.ifBlank { null } ?: authManager.currentUser?.displayName ?: "Player 1 (Host)"
            val uid = authManager.currentUser?.uid ?: "u_${UUID.randomUUID().toString().take(8)}"

            if (socketIOManager.isConnected()) {
                socketIOManager.createRoom(playerName = name, uid = uid, targetScore = 300) { response ->
                    _isConnectingOnline.value = false
                    if (response.success && !response.roomCode.isNullOrBlank()) {
                        val realCode = response.roomCode
                        onlineSessionToken = response.sessionToken ?: realCode
                        val hostPlayer = Player(
                            id = 1,
                            seatIndex = 0,
                            name = name,
                            controllerType = ControllerType.HUMAN,
                            isHost = true,
                            connectionStatus = ConnectionStatus.CONNECTED
                        )
                        _gameState.value = _gameState.value.copy(
                            mode = GameMode.ONLINE,
                            roomCode = realCode,
                            phase = GamePhase.ONLINE_LOBBY,
                            players = listOf(hostPlayer),
                            targetScore = response.targetScore
                        )
                        firebaseRoomManager.attachRoomListener(
                            roomCode = realCode,
                            onRoomUpdated = { roomData ->
                                _gameState.value = _gameState.value.copy(players = roomData.players)
                            },
                            onGameStateUpdated = { remoteState ->
                                if (_gameState.value.mode == GameMode.ONLINE && _gameState.value.phase != GamePhase.ONLINE_LOBBY) {
                                    _gameState.value = remoteState
                                }
                            },
                            onError = { err ->
                                _gameState.value = _gameState.value.copy(statusMessage = err)
                            }
                        )
                        firebaseRoomManager.startPingMeasurement(viewModelScope)
                        onResult(true, "Room $realCode created successfully!")
                    } else {
                        onResult(false, response.error ?: "Failed to create room on server.")
                    }
                }
            } else {
                _isConnectingOnline.value = false
                onResult(false, "Socket server is not connected. Please verify connection.")
            }
        }
    }

    fun joinOnlineRoom(code: String, onResult: (Boolean, String) -> Unit) {
        val cleanCode = code.trim()
        if (cleanCode.length != 4 || !cleanCode.all { it.isDigit() }) {
            onResult(false, "Room code must be exactly 4 digits.")
            return
        }
        viewModelScope.launch {
            _isConnectingOnline.value = true
            stopTimers()
            val user = userProfile.value
            val name = user?.displayName?.ifBlank { null } ?: authManager.currentUser?.displayName ?: "Player"
            val uid = authManager.currentUser?.uid ?: "u_${UUID.randomUUID().toString().take(8)}"

            if (socketIOManager.isConnected()) {
                socketIOManager.joinRoom(roomCode = cleanCode, playerName = name, uid = uid) { response ->
                    _isConnectingOnline.value = false
                    if (response.success && !response.roomCode.isNullOrBlank()) {
                        val realCode = response.roomCode
                        onlineSessionToken = response.sessionToken ?: realCode
                        val joinedPlayer = Player(
                            id = 2,
                            seatIndex = 1,
                            name = name,
                            controllerType = ControllerType.HUMAN,
                            isHost = false,
                            connectionStatus = ConnectionStatus.CONNECTED
                        )
                        _gameState.value = _gameState.value.copy(
                            mode = GameMode.ONLINE,
                            roomCode = realCode,
                            phase = GamePhase.ONLINE_LOBBY,
                            players = listOf(joinedPlayer),
                            targetScore = response.targetScore
                        )
                        firebaseRoomManager.attachRoomListener(
                            roomCode = realCode,
                            onRoomUpdated = { roomData ->
                                _gameState.value = _gameState.value.copy(players = roomData.players)
                            },
                            onGameStateUpdated = { remoteState ->
                                if (_gameState.value.mode == GameMode.ONLINE) {
                                    _gameState.value = remoteState
                                }
                            },
                            onError = { err ->
                                _gameState.value = _gameState.value.copy(statusMessage = err)
                            }
                        )
                        firebaseRoomManager.startPingMeasurement(viewModelScope)
                        onResult(true, "Joined room $realCode!")
                    } else {
                        onResult(false, response.error ?: "Unable to join room $cleanCode.")
                    }
                }
            } else {
                _isConnectingOnline.value = false
                onResult(false, "Socket server is not connected. Please verify connection.")
            }
        }
    }

    fun addOnlineBot() {
        val roomCode = _gameState.value.roomCode ?: return
        viewModelScope.launch {
            val result = firebaseRoomManager.addBot(roomCode)
            result.onFailure {
                _gameState.value = _gameState.value.copy(statusMessage = it.message)
            }
        }
    }

    private fun handleMultiplayerEvent(event: String, payload: JSONObject) {
        viewModelScope.launch(Dispatchers.Main) {
            when (event) {
                "player_joined", "room_state_updated", "session_restored", "player_reconnected" -> {
                    val roomStateJson = payload.optJSONObject("roomState")
                    if (roomStateJson != null) {
                        val players = multiplayerClient.parsePlayers(roomStateJson.optJSONArray("players"))
                        if (players.isNotEmpty()) {
                            _gameState.value = _gameState.value.copy(players = players)
                        }
                    } else {
                        val playerObj = payload.optJSONObject("player")
                        if (playerObj != null) {
                            val player = multiplayerClient.parsePlayer(playerObj)
                            val current = _gameState.value.players.toMutableList()
                            val existingIndex = current.indexOfFirst { it.seatIndex == player.seatIndex || it.id == player.id }
                            if (existingIndex >= 0) {
                                current[existingIndex] = player
                            } else {
                                current.add(player)
                            }
                            _gameState.value = _gameState.value.copy(players = current)
                        }
                    }
                }
                "player_disconnected" -> {
                    val playerObj = payload.optJSONObject("player")
                    if (playerObj != null) {
                        val player = multiplayerClient.parsePlayer(playerObj)
                        val current = _gameState.value.players.map {
                            if (it.id == player.id) it.copy(connectionStatus = ConnectionStatus.DISCONNECTED, disconnectRemainingSeconds = 30)
                            else it
                        }
                        _gameState.value = _gameState.value.copy(players = current)
                    }
                }
                "player_timeout_bot_assigned" -> {
                    val playerObj = payload.optJSONObject("player")
                    if (playerObj != null) {
                        val player = multiplayerClient.parsePlayer(playerObj)
                        val current = _gameState.value.players.map {
                            if (it.id == player.id) it.copy(controllerType = ControllerType.BOT, connectionStatus = ConnectionStatus.BOT_ACTIVE)
                            else it
                        }
                        _gameState.value = _gameState.value.copy(players = current)
                    }
                }
            }
        }
    }

    fun removeOnlinePlayer(index: Int) {
        val currentPlayers = _gameState.value.players.toMutableList()
        if (index in currentPlayers.indices && index > 0) { // Don't remove host
            currentPlayers.removeAt(index)
            // Re-index seats
            val reindexed = currentPlayers.mapIndexed { i, p -> p.copy(seatIndex = i) }
            _gameState.value = _gameState.value.copy(players = reindexed)
        }
    }

    fun startSoloGame(targetScore: Int = 300) {
        stopTimers()
        val players = listOf(
            Player(
                id = 1,
                seatIndex = 0,
                name = "You",
                controllerType = ControllerType.HUMAN,
                isHost = true
            ),
            Player(
                id = 2,
                seatIndex = 1,
                name = "Bot 2",
                controllerType = ControllerType.BOT
            ),
            Player(
                id = 3,
                seatIndex = 2,
                name = "Bot 3",
                controllerType = ControllerType.BOT
            ),
            Player(
                id = 4,
                seatIndex = 3,
                name = "Bot 4",
                controllerType = ControllerType.BOT
            )
        )

        initGame(
            mode = GameMode.SOLO,
            players = players,
            target = targetScore,
            roomCode = null
        )
    }

    fun startLocalGame(
        player1Name: String,
        player2Name: String,
        player3Name: String,
        player4Name: String,
        targetScore: Int
    ) {
        val players = listOf(
            Player(id = 1, seatIndex = 0, name = player1Name.ifBlank { "Player 1" }, controllerType = ControllerType.HUMAN),
            Player(id = 2, seatIndex = 1, name = player2Name.ifBlank { "Player 2" }, controllerType = ControllerType.HUMAN),
            Player(id = 3, seatIndex = 2, name = player3Name.ifBlank { "Player 3" }, controllerType = ControllerType.HUMAN),
            Player(id = 4, seatIndex = 3, name = player4Name.ifBlank { "Player 4" }, controllerType = ControllerType.HUMAN)
        )

        initGame(
            mode = GameMode.LOCAL,
            players = players,
            target = targetScore,
            roomCode = null
        )
    }

    fun startOnlineGame(targetScore: Int) {
        val currentPlayers = _gameState.value.players.toMutableList()
        // Automatically fill empty seats with bots until exactly 4 players
        while (currentPlayers.size < 4) {
            val botNum = currentPlayers.size + 1
            currentPlayers.add(
                Player(
                    id = botNum,
                    seatIndex = currentPlayers.size,
                    name = "Bot $botNum",
                    controllerType = ControllerType.BOT
                )
            )
        }

        val roomCode = _gameState.value.roomCode
        initGame(
            mode = GameMode.ONLINE,
            players = currentPlayers,
            target = targetScore,
            roomCode = roomCode
        )

        if (roomCode != null) {
            viewModelScope.launch {
                firebaseRoomManager.syncGameState(roomCode, _gameState.value)
            }
        }
    }

    private fun initGame(
        mode: GameMode,
        players: List<Player>,
        target: Int,
        roomCode: String?
    ) {
        val newGameId = UUID.randomUUID().toString()
        _gameState.value = GameState(
            gameId = newGameId,
            mode = mode,
            phase = GamePhase.DEALING_ANIMATION,
            roomCode = roomCode,
            players = players,
            currentDealerIndex = 0, // Game 1: P1 shuffles
            firstCardPlayerIndex = 1, // P2 receives first card & leads trick 1
            currentTurnPlayerIndex = 1,
            targetScore = target,
            roundNumber = 1,
            prizeEnabled = settings.value.prizeEnabled,
            prizeAmount = settings.value.prizeAmount
        )

        executeDeal()
    }

    fun playDealingCardSlide() {
        soundHapticManager.playCardSlideSound()
        soundHapticManager.triggerHapticFeedback(SoundHapticManager.HapticType.SELECTION)
    }

    fun playInvalidAction() {
        soundHapticManager.playInvalidActionSound()
        soundHapticManager.triggerHapticFeedback(SoundHapticManager.HapticType.ERROR)
    }

    /**
     * Executes authoritative deal and validation according to shuffle rotation.
     * Flow: DEALING_ANIMATION (4.5s shuffle + 0.5s pause + 6.5s 52-card distribution = 11.5s)
     *       -> DEAL_COMPLETE (authoritative hand is visible and fanned on table, 0.8s settling)
     *       -> CALL_PHASE (Call controls open above the hand, hand stays fully readable)
     */
    private fun executeDeal() {
        stopTimers()
        soundHapticManager.playShuffleSound()

        viewModelScope.launch {
            var dealAttempts = 0
            var dealtHands: List<List<Card>>
            var isDealValid = false
            var currentDealer = _gameState.value.currentDealerIndex
            var firstCardPlayer = _gameState.value.firstCardPlayerIndex

            do {
                dealtHands = AuthoritativeRuleEngine.dealCards()
                isDealValid = AuthoritativeRuleEngine.validateDeal(dealtHands)
                if (!isDealValid) {
                    dealAttempts++
                    // A dismissed deal advances the rotation
                    currentDealer = (currentDealer + 1) % 4
                    firstCardPlayer = (currentDealer + 1) % 4
                }
            } while (!isDealValid && dealAttempts < 10)

            if (!isDealValid) {
                // Reshuffled safely
                dealtHands = AuthoritativeRuleEngine.dealCards()
            }

            // Phase 1: DEALING_ANIMATION (12.5s total: 4.3s shuffle + 0.7s pause + 6.8s 52-card distribution + 0.7s settling)
            _gameState.value = _gameState.value.copy(
                phase = GamePhase.DEALING_ANIMATION,
                isDealing = true,
                currentDealerIndex = currentDealer,
                firstCardPlayerIndex = firstCardPlayer,
                statusMessage = "Shuffling & Dealing 52 Cards..."
            )
            delay(12500)

            soundHapticManager.playDealSound()

            val updatedPlayers = _gameState.value.players.mapIndexed { index, player ->
                player.copy(
                    cards = dealtHands[index],
                    call = null,
                    tricksWon = 0,
                    roundScore = 0,
                    hasModifiedCall = false,
                    failedTurnAttempts = 0
                )
            }

            // Phase 2: DEAL_COMPLETE (authoritative cards are in hands, hand is revealed and fans/settles)
            _gameState.value = _gameState.value.copy(
                players = updatedPlayers,
                currentDealerIndex = currentDealer,
                firstCardPlayerIndex = firstCardPlayer,
                currentTurnPlayerIndex = firstCardPlayer,
                currentTrick = Trick(trickNumber = 1),
                completedTricks = emptyList(),
                phase = GamePhase.DEAL_COMPLETE,
                isDealing = false,
                statusMessage = "Cards Dealt • Hand Settling..."
            )

            // 0.8s settling time for cards to settle and fan into position
            delay(800)

            // Phase 3: CALL_PHASE (Call controls appear above hand, hand is 100% visible)
            _gameState.value = _gameState.value.copy(
                phase = GamePhase.CALL_PHASE,
                statusMessage = if (_gameState.value.mode == GameMode.SOLO) "Select your call (1-8)" else "CALL FOR: ${updatedPlayers[firstCardPlayer].name}"
            )

            persistActiveGame()
            if (_gameState.value.mode == GameMode.ONLINE) {
                processBotBids()
            }
        }
    }

    private fun processBotBids() {
        viewModelScope.launch {
            val players = _gameState.value.players
            for (player in players) {
                if (player.isBot && player.call == null) {
                    delay(Random.nextLong(900, 1500))
                    val botCall = BotEngine.calculateCall(player.cards)
                    submitCall(player.seatIndex, botCall)
                }
            }
        }
    }

    private fun processSoloBotBidsAfterHumanCall() {
        viewModelScope.launch {
            for (seat in 1..3) {
                val currentP = _gameState.value.players.getOrNull(seat) ?: continue
                if (currentP.isBot && currentP.call == null) {
                    delay(Random.nextLong(900, 1500))
                    val botBid = BotEngine.calculateCall(currentP.cards)
                    val updated = _gameState.value.players.toMutableList()
                    updated[seat] = currentP.copy(call = botBid)
                    _gameState.value = _gameState.value.copy(
                        players = updated,
                        statusMessage = "${currentP.name} calls $botBid"
                    )
                }
            }

            // All 4 calls now submitted
            val finalPlayers = _gameState.value.players.toMutableList()
            var total = finalPlayers.sumOf { it.call ?: 0 }

            if (total < 9) {
                // Table minimum rule: In Solo mode against 3 bots, authoritative engine automatically
                // bumps the strongest bot's call to satisfy the minimum 9 requirement.
                val shortage = 9 - total
                val botToBump = finalPlayers.filter { it.isBot && (it.call ?: 0) < 8 }
                    .maxByOrNull { bot ->
                        bot.cards.count { it.suit == com.example.model.Suit.SPADES } * 10 + bot.cards.sumOf { it.rank.value }
                    }
                if (botToBump != null) {
                    val currentCall = botToBump.call ?: 1
                    val newCall = (currentCall + shortage).coerceIn(1, 8)
                    finalPlayers[botToBump.seatIndex] = botToBump.copy(call = newCall)
                }
                total = finalPlayers.sumOf { it.call ?: 0 }
            }

            if (total >= 9) {
                _gameState.value = _gameState.value.copy(
                    players = finalPlayers,
                    phase = GamePhase.PLAYING,
                    callPhasePrompt = null,
                    currentTurnPlayerIndex = _gameState.value.firstCardPlayerIndex,
                    statusMessage = "All calls locked (Total: $total). Round ${_gameState.value.roundNumber} begins!"
                )
                persistActiveGame()
                startTurnTimer()
                checkBotTurn()
            } else {
                _gameState.value = _gameState.value.copy(
                    players = finalPlayers,
                    callPhasePrompt = "Total calls ($total) is under 9! Minimum is 9. Please adjust call.",
                    statusMessage = "Total calls ($total) is under 9! Minimum is 9."
                )
            }
        }
    }

    fun submitCall(seatIndex: Int, call: Int) {
        if (!AuthoritativeRuleEngine.isCallValid(call)) return

        val players = _gameState.value.players.toMutableList()
        val player = players.getOrNull(seatIndex) ?: return

        // A player may modify only their own call and only once
        if (player.call != null && player.hasModifiedCall) {
            soundHapticManager.playInvalidActionSound()
            soundHapticManager.triggerHapticFeedback(SoundHapticManager.HapticType.ERROR)
            return
        }

        val hasModified = player.call != null

        players[seatIndex] = player.copy(
            call = call,
            hasModifiedCall = hasModified
        )

        soundHapticManager.triggerHapticFeedback(SoundHapticManager.HapticType.SELECTION)

        if (_gameState.value.mode == GameMode.SOLO) {
            _gameState.value = _gameState.value.copy(
                players = players,
                statusMessage = "Your call ($call) submitted. Waiting for bot calls..."
            )
            processSoloBotBidsAfterHumanCall()
            return
        }

        val allCallsSubmitted = players.all { it.call != null }

        if (allCallsSubmitted) {
            val totalCalls = players.sumOf { it.call ?: 0 }
            if (totalCalls < 9) {
                // Rule 19: If total < 9: Remain in CALL_PHASE with alert
                soundHapticManager.playInvalidActionSound()
                _gameState.value = _gameState.value.copy(
                    players = players,
                    callPhasePrompt = "Total calls ($totalCalls) is under 9! Minimum is 9. Please adjust call.",
                    statusMessage = "Total calls ($totalCalls) is under 9! Minimum is 9."
                )
            } else {
                // Valid! Proceed to PLAYING
                _gameState.value = _gameState.value.copy(
                    players = players,
                    phase = GamePhase.PLAYING,
                    callPhasePrompt = null,
                    currentTurnPlayerIndex = _gameState.value.firstCardPlayerIndex,
                    statusMessage = null
                )
                persistActiveGame()
                startTurnTimer()
                checkBotTurn()
            }
        } else {
            // For Local Play: advance sequential call to next player whose call is null
            val nextBiddingIndex = (1..3).map { (seatIndex + it) % 4 }.firstOrNull { players[it].call == null } ?: ((seatIndex + 1) % 4)
            _gameState.value = _gameState.value.copy(
                players = players,
                currentTurnPlayerIndex = nextBiddingIndex,
                statusMessage = "CALL FOR: ${players[nextBiddingIndex].name}"
            )
        }

        if (_gameState.value.mode == GameMode.ONLINE) {
            val code = _gameState.value.roomCode
            if (code != null) {
                viewModelScope.launch {
                    firebaseRoomManager.syncGameState(code, _gameState.value)
                }
            }
        }
    }

    private fun startTurnTimer() {
        turnTimerJob?.cancel()
        val isHumanTurn = _gameState.value.currentTurnPlayerIndex == 0
        if (!isHumanTurn) {
            // Requirement 9: Bots do not require the user-facing 30-second timer
            return
        }
        _gameState.value = _gameState.value.copy(turnTimerSeconds = 30)

        turnTimerJob = viewModelScope.launch {
            while (_gameState.value.turnTimerSeconds > 0 && _gameState.value.phase == GamePhase.PLAYING && _gameState.value.currentTurnPlayerIndex == 0) {
                delay(1000)
                val nextSec = _gameState.value.turnTimerSeconds - 1
                _gameState.value = _gameState.value.copy(turnTimerSeconds = nextSec)
            }

            if (_gameState.value.turnTimerSeconds == 0 && _gameState.value.phase == GamePhase.PLAYING && _gameState.value.currentTurnPlayerIndex == 0) {
                handleTurnTimeout()
            }
        }
    }

    private fun handleTurnTimeout() {
        val currentTurnIndex = _gameState.value.currentTurnPlayerIndex
        val player = _gameState.value.players.getOrNull(currentTurnIndex) ?: return

        soundHapticManager.playInvalidActionSound()
        soundHapticManager.triggerHapticFeedback(SoundHapticManager.HapticType.ERROR)

        val updatedAttempts = player.failedTurnAttempts + 1
        val shouldTakeover = updatedAttempts >= 2

        val updatedPlayers = _gameState.value.players.toMutableList()
        updatedPlayers[currentTurnIndex] = player.copy(
            failedTurnAttempts = updatedAttempts,
            connectionStatus = if (shouldTakeover) ConnectionStatus.BOT_ACTIVE else player.connectionStatus,
            controllerType = if (shouldTakeover) ControllerType.BOT else player.controllerType
        )

        _gameState.value = _gameState.value.copy(
            players = updatedPlayers,
            statusMessage = if (shouldTakeover) "Turn expired! Bot took over ${player.name}'s seat." else "Turn expired for ${player.name}!"
        )

        // Make an authoritative legal card play automatically
        val legalCards = AuthoritativeRuleEngine.getLegalCards(
            hand = player.cards,
            currentTrick = _gameState.value.currentTrick,
            isFirstTrick = _gameState.value.completedTricks.isEmpty()
        )
        if (legalCards.isNotEmpty()) {
            val chosenCard = legalCards.first()
            playCard(currentTurnIndex, chosenCard)
        }
    }

    fun playCard(seatIndex: Int, card: Card) {
        val state = _gameState.value
        if (state.phase != GamePhase.PLAYING) return
        if (state.currentTurnPlayerIndex != seatIndex) {
            soundHapticManager.playInvalidActionSound()
            soundHapticManager.triggerHapticFeedback(SoundHapticManager.HapticType.ERROR)
            return
        }

        val player = state.players.getOrNull(seatIndex) ?: return
        if (!player.cards.any { it.id == card.id }) {
            soundHapticManager.playInvalidActionSound()
            return
        }

        val isFirstTrick = state.completedTricks.isEmpty()
        val legalCards = AuthoritativeRuleEngine.getLegalCards(
            hand = player.cards,
            currentTrick = state.currentTrick,
            isFirstTrick = isFirstTrick
        )

        if (!legalCards.any { it.id == card.id }) {
            // Illegal card move
            soundHapticManager.playInvalidActionSound()
            soundHapticManager.triggerHapticFeedback(SoundHapticManager.HapticType.ERROR)
            return
        }

        // Commit card play authoritatively
        soundHapticManager.playCardPlaySound()
        soundHapticManager.triggerHapticFeedback(SoundHapticManager.HapticType.CARD_PLAY)

        val updatedHand = player.cards.filterNot { it.id == card.id }
        val updatedPlayers = state.players.toMutableList()
        updatedPlayers[seatIndex] = player.copy(cards = updatedHand)

        val playedCard = PlayedCard(playerIndex = seatIndex, card = card)
        val updatedPlayedCards = state.currentTrick.playedCards + playedCard
        val updatedTrick = state.currentTrick.copy(
            leadSuit = state.currentTrick.leadSuit ?: card.suit,
            playedCards = updatedPlayedCards
        )

        turnTimerJob?.cancel()

        if (updatedPlayedCards.size == 4) {
            // Trick complete!
            val trickWinnerIndex = AuthoritativeRuleEngine.determineTrickWinner(updatedTrick)
            val winningPlayer = updatedPlayers[trickWinnerIndex]
            updatedPlayers[trickWinnerIndex] = winningPlayer.copy(tricksWon = winningPlayer.tricksWon + 1)

            _gameState.value = state.copy(
                players = updatedPlayers,
                currentTrick = updatedTrick.copy(winnerIndex = trickWinnerIndex),
                phase = GamePhase.TRICK_EVALUATION
            )

            viewModelScope.launch {
                soundHapticManager.playTrickWonSound()
                soundHapticManager.triggerHapticFeedback(SoundHapticManager.HapticType.TRICK_WON)
                delay(2800)

                val completedList = state.completedTricks + updatedTrick.copy(winnerIndex = trickWinnerIndex)
                if (completedList.size == 13) {
                    // All 13 tricks complete! Proceed to round scoring
                    handleRoundEnd(completedList, updatedPlayers)
                } else {
                    // Next trick, winner leads!
                    val nextTrickNum = completedList.size + 1
                    _gameState.value = _gameState.value.copy(
                        completedTricks = completedList,
                        currentTrick = Trick(trickNumber = nextTrickNum),
                        currentTurnPlayerIndex = trickWinnerIndex,
                        phase = GamePhase.PLAYING
                    )
                    persistActiveGame()
                    startTurnTimer()
                    checkBotTurn()
                }
            }
        } else {
            // Advance turn to next clockwise player
            val nextTurnIndex = (seatIndex + 1) % 4
            _gameState.value = state.copy(
                players = updatedPlayers,
                currentTrick = updatedTrick,
                currentTurnPlayerIndex = nextTurnIndex
            )
            persistActiveGame()
            startTurnTimer()
            checkBotTurn()
        }

        if (_gameState.value.mode == GameMode.ONLINE) {
            val code = _gameState.value.roomCode
            if (code != null) {
                viewModelScope.launch {
                    firebaseRoomManager.syncGameState(code, _gameState.value)
                }
            }
        }
    }

    private fun checkBotTurn() {
        val state = _gameState.value
        if (state.phase != GamePhase.PLAYING) return
        val currentPlayer = state.players.getOrNull(state.currentTurnPlayerIndex) ?: return

        if (currentPlayer.isBot) {
            botActionJob?.cancel()
            botActionJob = viewModelScope.launch {
                delay(Random.nextLong(1400, 2000))
                if (_gameState.value.phase == GamePhase.PLAYING && _gameState.value.currentTurnPlayerIndex == currentPlayer.seatIndex) {
                    val isFirstTrick = _gameState.value.completedTricks.isEmpty()
                    val botCard = BotEngine.chooseCardToPlay(
                        botPlayer = currentPlayer,
                        currentTrick = _gameState.value.currentTrick,
                        isFirstTrick = isFirstTrick
                    )
                    playCard(currentPlayer.seatIndex, botCard)
                }
            }
        }
    }

    private fun handleRoundEnd(completedTricks: List<Trick>, players: List<Player>) {
        stopTimers()

        val updatedPlayers = players.map { p ->
            val call = p.call ?: 1
            val won = p.tricksWon
            val roundPts = AuthoritativeRuleEngine.calculateRoundScore(call, won)
            p.copy(
                roundScore = roundPts,
                totalScore = p.totalScore + roundPts
            )
        }

        val completedRound = _gameState.value.roundNumber
        val target = _gameState.value.targetScore
        val winners = updatedPlayers.filter { it.totalScore >= target }

        viewModelScope.launch {
            soundHapticManager.playScoreUpdateSound()
            _gameState.value = _gameState.value.copy(
                players = updatedPlayers,
                completedTricks = completedTricks,
                phase = GamePhase.ROUND_END,
                statusMessage = "Round $completedRound complete • Updating Scores..."
            )
            persistActiveGame()

            // 1.8s for smooth score counting animation into new total
            delay(1800)

            if (winners.isNotEmpty()) {
                // First or highest player to cross target wins!
                val overallWinner = winners.maxByOrNull { it.totalScore } ?: winners.first()
                _gameState.value = _gameState.value.copy(
                    phase = GamePhase.GAME_COMPLETE,
                    winnerPlayerId = overallWinner.id,
                    winnerName = overallWinner.name,
                    winnerScore = overallWinner.totalScore,
                    statusMessage = "Target reached! ${overallWinner.name} wins the match!"
                )
                soundHapticManager.playWinnerMusic()
                soundHapticManager.triggerHapticFeedback(SoundHapticManager.HapticType.VICTORY)

                // Persist to completed games history
                gameRepository.recordCompletedGame(
                    gameId = _gameState.value.gameId,
                    mode = _gameState.value.mode,
                    players = updatedPlayers,
                    winnerName = overallWinner.name,
                    winnerScore = overallWinner.totalScore,
                    targetScore = target
                )
                gameRepository.clearActiveGame()
            } else {
                // Next round transition: short smooth pause before next shuffle begins
                val nextRound = completedRound + 1
                _gameState.value = _gameState.value.copy(
                    statusMessage = "Round $completedRound complete • Starting Round $nextRound..."
                )
                delay(2000)
                startNextRound()
            }
        }
    }

    fun startNextRound() {
        val state = _gameState.value
        if (state.phase == GamePhase.GAME_COMPLETE) return

        // Rule 12: Each round must have its own unique round/game ID
        val newRoundGameId = UUID.randomUUID().toString()
        val nextDealer = (state.currentDealerIndex + 1) % 4
        val nextFirstCard = (nextDealer + 1) % 4

        // Reset round transient state, preserving cumulative scores & player configs
        val freshPlayers = state.players.map { player ->
            player.copy(
                cards = emptyList(),
                call = null,
                tricksWon = 0,
                roundScore = 0,
                hasModifiedCall = false,
                failedTurnAttempts = 0
            )
        }

        _gameState.value = state.copy(
            gameId = newRoundGameId,
            roundNumber = state.roundNumber + 1,
            players = freshPlayers,
            currentDealerIndex = nextDealer,
            firstCardPlayerIndex = nextFirstCard,
            currentTurnPlayerIndex = nextFirstCard,
            currentTrick = Trick(trickNumber = 1),
            completedTricks = emptyList(),
            phase = GamePhase.DEALING_ANIMATION,
            callPhasePrompt = null,
            statusMessage = "Round ${state.roundNumber + 1}: Dealing cards..."
        )

        executeDeal()
    }

    fun restartGame() {
        stopTimers()
        val resetPlayers = _gameState.value.players.map { p ->
            p.copy(
                cards = emptyList(),
                call = null,
                tricksWon = 0,
                roundScore = 0,
                totalScore = 0,
                failedTurnAttempts = 0
            )
        }
        initGame(
            mode = _gameState.value.mode,
            players = resetPlayers,
            target = _gameState.value.targetScore,
            roomCode = _gameState.value.roomCode
        )
    }

    fun resumeSavedGame() {
        viewModelScope.launch {
            val savedState = _gameState.value
            // Observe or load from repository
            gameRepository.activeGameFlow.collectLatest { state ->
                if (state != null && state.players.size == 4) {
                    _gameState.value = state
                    if (state.phase == GamePhase.PLAYING) {
                        startTurnTimer()
                        checkBotTurn()
                    }
                }
            }
        }
    }

    private fun persistActiveGame() {
        val current = _gameState.value
        if (current.phase != GamePhase.HOME && current.phase != GamePhase.OPENING_ANIMATION && current.phase != GamePhase.GAME_COMPLETE) {
            viewModelScope.launch {
                gameRepository.saveActiveGame(current)
            }
        }
    }

    fun simulateDisconnect(seatIndex: Int) {
        val players = _gameState.value.players.toMutableList()
        val p = players.getOrNull(seatIndex) ?: return
        players[seatIndex] = p.copy(
            connectionStatus = ConnectionStatus.DISCONNECTED,
            disconnectRemainingSeconds = 30
        )
        _gameState.value = _gameState.value.copy(players = players)

        disconnectTimerJob?.cancel()
        disconnectTimerJob = viewModelScope.launch {
            for (sec in 30 downTo 1) {
                delay(1000)
                val curr = _gameState.value.players.getOrNull(seatIndex) ?: break
                if (curr.connectionStatus == ConnectionStatus.DISCONNECTED) {
                    val remaining = sec - 1
                    val updated = _gameState.value.players.toMutableList()
                    if (remaining == 0) {
                        updated[seatIndex] = curr.copy(
                            connectionStatus = ConnectionStatus.BOT_ACTIVE,
                            controllerType = ControllerType.BOT,
                            disconnectRemainingSeconds = null
                        )
                        _gameState.value = _gameState.value.copy(
                            players = updated,
                            statusMessage = "30s reconnect window expired. Bot assigned to ${curr.name}'s seat."
                        )
                        if (_gameState.value.mode == GameMode.ONLINE) {
                            val code = _gameState.value.roomCode
                            if (code != null) {
                                viewModelScope.launch {
                                    firebaseRoomManager.syncGameState(code, _gameState.value)
                                }
                            }
                        }
                        checkBotTurn()
                    } else {
                        updated[seatIndex] = curr.copy(disconnectRemainingSeconds = remaining)
                        _gameState.value = _gameState.value.copy(players = updated)
                    }
                } else break
            }
        }
    }

    fun reconnectPlayer(seatIndex: Int) {
        disconnectTimerJob?.cancel()
        val players = _gameState.value.players.toMutableList()
        val p = players.getOrNull(seatIndex) ?: return
        players[seatIndex] = p.copy(
            connectionStatus = ConnectionStatus.CONNECTED,
            controllerType = ControllerType.HUMAN,
            disconnectRemainingSeconds = null
        )
        _gameState.value = _gameState.value.copy(
            players = players,
            statusMessage = "${p.name} reconnected successfully!"
        )
        if (_gameState.value.mode == GameMode.ONLINE) {
            val code = _gameState.value.roomCode
            if (code != null) {
                viewModelScope.launch {
                    firebaseRoomManager.syncGameState(code, _gameState.value)
                }
            }
        }
        Toast.makeText(getApplication(), "${p.name} reconnected successfully!", Toast.LENGTH_SHORT).show()
    }

    fun copyUpiId() {
        val clipboard = getApplication<Application>().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("UPI ID", "Sudhamoy@upi")
        clipboard.setPrimaryClip(clip)
        soundHapticManager.triggerHapticFeedback(SoundHapticManager.HapticType.SELECTION)
        Toast.makeText(getApplication(), "Copied: Sudhamoy@upi", Toast.LENGTH_SHORT).show()
    }

    fun payViaUpi() {
        val upiUri = Uri.parse("upi://pay?pa=Sudhamoy@upi&pn=Sudhamoy&tn=Mobile%20Card%20Game")
        val intent = Intent(Intent.ACTION_VIEW, upiUri).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            getApplication<Application>().startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(getApplication(), "No compatible UPI app installed on device.", Toast.LENGTH_LONG).show()
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            gameRepository.clearHistory()
            Toast.makeText(getApplication(), "History cleared", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopTimers() {
        turnTimerJob?.cancel()
        botActionJob?.cancel()
        disconnectTimerJob?.cancel()
    }

    override fun onCleared() {
        super.onCleared()
        firebaseRoomManager.leaveRoom()
        multiplayerClient.disconnectWebSocket()
        stopTimers()
    }
}
