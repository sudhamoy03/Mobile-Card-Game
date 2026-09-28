package com.example.model

enum class GameMode {
    SOLO,
    ONLINE,
    LOCAL
}

enum class GamePhase {
    OPENING_ANIMATION,
    HOME,
    LOCAL_SETUP,
    ONLINE_LOBBY,
    DEALING_ANIMATION,
    DEAL_COMPLETE,
    CALL_PHASE,
    PLAYING,
    TRICK_EVALUATION,
    ROUND_END,
    GAME_COMPLETE,
    SETTINGS,
    HISTORY
}

data class PlayedCard(
    val playerIndex: Int, // index in players list (0..3)
    val card: Card,
    val timestamp: Long = System.currentTimeMillis()
)

data class Trick(
    val trickNumber: Int, // 1..13
    val leadSuit: Suit? = null,
    val playedCards: List<PlayedCard> = emptyList(),
    val winnerIndex: Int? = null
)

data class GameState(
    val gameId: String = "",
    val mode: GameMode = GameMode.SOLO,
    val phase: GamePhase = GamePhase.HOME,
    val roomCode: String? = null,
    val players: List<Player> = emptyList(),
    val currentDealerIndex: Int = 0, // 0..3 (who shuffled)
    val firstCardPlayerIndex: Int = 1, // rotation: who received first card / leads trick 1
    val currentTurnPlayerIndex: Int = 1, // active player who plays now
    val currentTrick: Trick = Trick(trickNumber = 1),
    val completedTricks: List<Trick> = emptyList(),
    val turnTimerSeconds: Int = 30,
    val targetScore: Int = 300,
    val roundNumber: Int = 1,
    val winnerPlayerId: Int? = null,
    val winnerName: String? = null,
    val winnerScore: Int? = null,
    val dismissedDealsCount: Int = 0,
    val statusMessage: String? = null,
    val callPhasePrompt: String? = null,
    val isDealing: Boolean = false,
    val prizeEnabled: Boolean = false,
    val prizeAmount: Int = 500
) {
    val localUserPlayer: Player?
        get() = players.getOrNull(0) // bottom player is seat 0

    val isLocalUserTurn: Boolean
        get() = currentTurnPlayerIndex == 0 && (phase == GamePhase.PLAYING || phase == GamePhase.CALL_PHASE)

    val currentTurnPlayer: Player?
        get() = players.getOrNull(currentTurnPlayerIndex)

    val allCallsSubmitted: Boolean
        get() = players.size == 4 && players.all { it.call != null }

    val totalCalls: Int
        get() = players.sumOf { it.call ?: 0 }

    val isTotalCallsValid: Boolean
        get() = totalCalls >= 9
}
