package com.example.model

enum class ControllerType {
    HUMAN,
    BOT
}

enum class ConnectionStatus {
    CONNECTED,
    DISCONNECTED,
    RECONNECTING,
    BOT_ACTIVE
}

data class Player(
    val id: Int, // 1, 2, 3, 4
    val seatIndex: Int, // 0..3 (0: Bottom/User, 1: Left, 2: Top, 3: Right)
    val name: String,
    val controllerType: ControllerType = ControllerType.HUMAN,
    val connectionStatus: ConnectionStatus = ConnectionStatus.CONNECTED,
    val cards: List<Card> = emptyList(),
    val call: Int? = null, // 1..8
    val tricksWon: Int = 0,
    val roundScore: Int = 0,
    val totalScore: Int = 0,
    val disconnectRemainingSeconds: Int? = null,
    val failedTurnAttempts: Int = 0,
    val hasModifiedCall: Boolean = false,
    val isHost: Boolean = false
) {
    val remainingCardsCount: Int
        get() = cards.size

    val isBot: Boolean
        get() = controllerType == ControllerType.BOT || connectionStatus == ConnectionStatus.BOT_ACTIVE
}
