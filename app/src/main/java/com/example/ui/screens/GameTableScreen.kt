package com.example.ui.screens

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.engine.AuthoritativeRuleEngine
import com.example.model.Card
import com.example.model.GameMode
import com.example.model.GamePhase
import com.example.model.GameState
import com.example.model.Player
import com.example.ui.components.CallDialog
import com.example.ui.components.DealingAnimationOverlay
import com.example.ui.components.InteractiveHand
import com.example.ui.components.NetworkPingIndicator
import com.example.ui.components.PlayerPanel
import com.example.ui.components.TableFelt
import com.example.ui.components.TurnTimerIndicator
import com.example.ui.components.VictoryCelebrationOverlay

private val TableBackgroundBrush = Brush.radialGradient(
    colors = listOf(
        Color(0xFF0F2E1B),
        Color(0xFF0A1F13),
        Color(0xFF040E08)
    )
)

@Composable
fun GameTableScreen(
    gameState: GameState,
    isSoundEnabled: Boolean,
    onToggleSound: () -> Unit,
    onSubmitCall: (seatIndex: Int, call: Int) -> Unit,
    onPlayCard: (seatIndex: Int, card: Card) -> Unit,
    onExitGame: () -> Unit,
    onSimulateDisconnect: (seatIndex: Int) -> Unit,
    onReconnect: (seatIndex: Int) -> Unit,
    onCardDealtSound: () -> Unit = {},
    onInvalidAction: () -> Unit = {},
    onRestartGame: () -> Unit = {},
    onHomeClick: () -> Unit = {},
    networkPingMs: Int? = null
) {
    val context = LocalContext.current
    val activity = context as? Activity

    // Rule 12: Automatically switch to LANDSCAPE when gameplay begins
    DisposableEffect(Unit) {
        val originalOrientation = activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

        // Rule 2: Fullscreen Immersive Mode during gameplay (hide status bar & navigation UI)
        val window = activity?.window
        val insetsController = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        insetsController?.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insetsController?.hide(WindowInsetsCompat.Type.systemBars())

        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER
            insetsController?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    var showExitConfirmDialog by remember { mutableStateOf(false) }
    var selectedCard by remember { mutableStateOf<Card?>(null) }
    var isDropTargetActive by remember { mutableStateOf(false) }

    // Seat arrangements:
    // Seat 0: Bottom (Local user)
    // Seat 1: Left
    // Seat 2: Top
    // Seat 3: Right
    val bottomPlayer = gameState.players.getOrNull(0) ?: Player(1, 0, "Player 1")
    val leftPlayer = gameState.players.getOrNull(1) ?: Player(2, 1, "Player 2")
    val topPlayer = gameState.players.getOrNull(2) ?: Player(3, 2, "Player 3")
    val rightPlayer = gameState.players.getOrNull(3) ?: Player(4, 3, "Player 4")

    // Active player taking turn
    val activeTurnPlayer = gameState.players.getOrNull(gameState.currentTurnPlayerIndex) ?: bottomPlayer
    val isLocalTurn = gameState.currentTurnPlayerIndex == 0

    // Calling player for Call Phase:
    // In Solo and Online: seat 0 (You)
    // In Local: currentTurnPlayerIndex (active calling player)
    val activeCallingSeat = if (gameState.mode == GameMode.LOCAL && gameState.phase == GamePhase.CALL_PHASE) {
        gameState.currentTurnPlayerIndex
    } else 0
    val activeCallingPlayer = gameState.players.getOrNull(activeCallingSeat) ?: bottomPlayer

    // Hand card visibility rules (Requirements 4, 5, 9, 10, 11):
    // - Solo: Player 1 (You) sees complete 13 cards, bots remain hidden
    // - Online: Human sees ONLY their own 13 cards, others hidden
    // - Local: Active human player sees their own cards
    val displayedCards = if (gameState.mode == GameMode.LOCAL) {
        gameState.players.getOrNull(if (gameState.phase == GamePhase.CALL_PHASE) activeCallingSeat else gameState.currentTurnPlayerIndex)?.cards ?: bottomPlayer.cards
    } else {
        bottomPlayer.cards
    }
    val isTurnActive = isLocalTurn && gameState.phase == GamePhase.PLAYING

    val isFirstTrick = gameState.completedTricks.isEmpty()
    val legalCards = if (isTurnActive) {
        AuthoritativeRuleEngine.getLegalCards(
            hand = displayedCards,
            currentTrick = gameState.currentTrick,
            isFirstTrick = isFirstTrick
        )
    } else {
        emptyList()
    }

    // Back button confirmation
    BackHandler {
        showExitConfirmDialog = true
    }

    if (showExitConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showExitConfirmDialog = false },
            title = { Text("Leave Game?") },
            text = { Text("Your game state is automatically saved. You can continue later from the Home screen.") },
            confirmButton = {
                Button(
                    onClick = {
                        showExitConfirmDialog = false
                        onExitGame()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) {
                    Text("Exit to Home")
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitConfirmDialog = false }) {
                    Text("Resume")
                }
            }
        )
    }

    // Full landscape layout respecting safe area & Android insets
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TableBackgroundBrush)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .testTag("game_table_screen")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 2.dp)
        ) {
            // ==================== 1. TOP HEADER ROW ====================
            // Compact: Round info, Target, Room code, Timer, Sound toggle, Exit
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(30.dp)
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { showExitConfirmDialog = true },
                        modifier = Modifier
                            .size(30.dp)
                            .testTag("table_exit_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Exit Game",
                            tint = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    Text(
                        text = "ROUND ${gameState.roundNumber} • TARGET: ${gameState.targetScore}" +
                                if (gameState.mode == GameMode.ONLINE && gameState.roomCode != null) " • ROOM: ${gameState.roomCode}" else "",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Turn Timer Indicator (Requirement 9: applies to human player's turn only)
                    if (gameState.phase == GamePhase.PLAYING && isTurnActive) {
                        TurnTimerIndicator(
                            secondsRemaining = gameState.turnTimerSeconds,
                            isLocalPlayerTurn = true,
                            activePlayerName = "You"
                        )
                    }

                    // Subtle Network Ping Indicator during multiplayer matches
                    if (gameState.mode == GameMode.ONLINE) {
                        Spacer(modifier = Modifier.width(6.dp))
                        NetworkPingIndicator(pingMs = networkPingMs)
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    IconButton(
                        onClick = onToggleSound,
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = if (isSoundEnabled) Icons.Filled.VolumeUp else Icons.Filled.VolumeOff,
                            contentDescription = "Toggle Sound",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Compact Status / Round Message Banner
            if (gameState.statusMessage != null) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.Black.copy(alpha = 0.75f))
                            .border(0.8.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 10.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = gameState.statusMessage,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // ==================== 2. TOP PLAYER (Seat 2) ====================
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 1.dp, bottom = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                PlayerPanel(
                    player = topPlayer,
                    isCurrentTurn = gameState.currentTurnPlayerIndex == 2,
                    isLocalPlayer = false,
                    isWinner = topPlayer.id == gameState.winnerPlayerId
                )
            }

            // ==================== 3. MIDDLE GAMEPLAY ROW ====================
            // LEFT PLAYER        GAME TABLE        RIGHT PLAYER
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // LEFT PLAYER (Seat 1)
                PlayerPanel(
                    player = leftPlayer,
                    isCurrentTurn = gameState.currentTurnPlayerIndex == 1,
                    isLocalPlayer = false,
                    modifier = Modifier.padding(start = 2.dp),
                    isWinner = leftPlayer.id == gameState.winnerPlayerId
                )

                // GAME TABLE FELT (Dedicated Center, 100% Unobstructed)
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    TableFelt(
                        currentTrick = gameState.currentTrick,
                        trickWinnerName = gameState.currentTrick.winnerIndex?.let { gameState.players.getOrNull(it)?.name },
                        isDropTargetActive = isDropTargetActive
                    )
                }

                // RIGHT PLAYER (Seat 3)
                PlayerPanel(
                    player = rightPlayer,
                    isCurrentTurn = gameState.currentTurnPlayerIndex == 3,
                    isLocalPlayer = false,
                    modifier = Modifier.padding(end = 2.dp),
                    isWinner = rightPlayer.id == gameState.winnerPlayerId
                )
            }

            // ==================== 4. CALL CONTROLS (DEDICATED SAFE AREA ABOVE HAND) ====================
            // Requirements 4, 5, 6, 7, 8: Controls placed in dedicated area above hand.
            // Never covers the cards. Hand remains 100% visible, fanned, and inspectable.
            if (gameState.phase == GamePhase.CALL_PHASE && (activeCallingPlayer.call == null || gameState.callPhasePrompt != null)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CallDialog(
                        playerName = activeCallingPlayer.name,
                        currentCall = activeCallingPlayer.call,
                        hasModifiedCall = activeCallingPlayer.hasModifiedCall,
                        totalCallsSoFar = gameState.totalCalls,
                        callPrompt = gameState.callPhasePrompt,
                        onSelectCall = { call ->
                            onSubmitCall(activeCallingSeat, call)
                        }
                    )
                }
            }

            // ==================== 5. BOTTOM PLAYER & HAND ====================
            // BOTTOM PLAYER
            // HAND
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Bottom player row with active turn badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 2.dp)
                ) {
                    PlayerPanel(
                        player = bottomPlayer,
                        isCurrentTurn = gameState.currentTurnPlayerIndex == 0,
                        isLocalPlayer = true,
                        isWinner = bottomPlayer.id == gameState.winnerPlayerId
                    )

                    // Turn indicator (Requirements 5 & 6)
                    if (gameState.phase == GamePhase.PLAYING) {
                        val turnLabel = if (isLocalTurn) {
                            "YOUR TURN"
                        } else {
                            "${activeTurnPlayer.name.uppercase()}'S TURN"
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    if (isLocalTurn)
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                                    else Color.Black.copy(alpha = 0.45f)
                                )
                                .border(
                                    1.dp,
                                    if (isLocalTurn)
                                        MaterialTheme.colorScheme.primary
                                    else Color.Gray.copy(alpha = 0.4f),
                                    RoundedCornerShape(6.dp)
                                )
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = turnLabel,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (isLocalTurn)
                                    MaterialTheme.colorScheme.primary
                                else Color.White
                            )
                        }
                    }
                }

                // Interactive Hand (Smooth drag-and-drop & play)
                InteractiveHand(
                    cards = displayedCards,
                    legalCards = legalCards,
                    selectedCard = selectedCard,
                    isTurnActive = isTurnActive,
                    onSelectCard = { card ->
                        selectedCard = card
                    },
                    onPlayCard = { card ->
                        onPlayCard(0, card)
                        selectedCard = null
                    },
                    onDragStateChange = { isDragging, isOverDropZone ->
                        isDropTargetActive = isDragging && isOverDropZone
                    },
                    onInvalidAction = onInvalidAction
                )
            }
        }

        // ==================== 6. DEALING ANIMATION OVERLAY ====================
        if (gameState.phase == GamePhase.DEALING_ANIMATION || gameState.isDealing) {
            val dealerPlayer = gameState.players.getOrNull(gameState.currentDealerIndex)
            DealingAnimationOverlay(
                dealerName = dealerPlayer?.name ?: "Player 1",
                roundNumber = gameState.roundNumber,
                players = gameState.players,
                onCardDealt = onCardDealtSound,
                onDealingComplete = { /* Handled automatically by ViewModel timing */ },
                modifier = Modifier.fillMaxSize()
            )
        }

        // ==================== 7. VICTORY CELEBRATION OVERLAY ====================
        // Refined, restrained celebration animation highlighting the winner's name without blocking the game table
        if (gameState.phase == GamePhase.GAME_COMPLETE || (gameState.winnerName != null && gameState.winnerScore != null)) {
            VictoryCelebrationOverlay(
                winnerName = gameState.winnerName ?: "Player 1",
                winnerScore = gameState.winnerScore ?: gameState.targetScore,
                targetScore = gameState.targetScore,
                players = gameState.players,
                prizeEnabled = gameState.prizeEnabled,
                prizeAmount = gameState.prizeAmount,
                onRematchClick = onRestartGame,
                onHomeClick = onHomeClick,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
