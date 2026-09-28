package com.example.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

/**
 * Legacy entity for single-row quick game snapshot persistence.
 */
@Entity(tableName = "active_game")
data class ActiveGameEntity(
    @PrimaryKey val id: Int = 1,
    val gameId: String,
    val mode: String, // LOCAL or ONLINE
    val roomCode: String?,
    val serializedPlayers: String, // JSON
    val currentDealerIndex: Int,
    val firstCardPlayerIndex: Int,
    val currentTurnPlayerIndex: Int,
    val serializedCurrentTrick: String, // JSON
    val serializedCompletedTricks: String, // JSON
    val roundNumber: Int,
    val targetScore: Int,
    val phase: String,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Historical record of finished games.
 */
@Entity(tableName = "completed_games")
data class CompletedGameEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val gameId: String,
    val timestamp: Long = System.currentTimeMillis(),
    val mode: String,
    val playerNames: String,
    val finalScores: String,
    val winnerName: String,
    val winnerScore: Int,
    val targetScore: Int
)

/**
 * Room Entity storing the current multiplayer game session state,
 * including turn tracking, active phase, dealer/first-card rotation, and targets.
 */
@Entity(tableName = "game_sessions")
data class GameSessionEntity(
    @PrimaryKey val gameId: String,
    val mode: String, // "LOCAL" or "ONLINE"
    val roomCode: String?,
    val roundNumber: Int,
    val targetScore: Int,
    val phase: String, // "DEALING_ANIMATION", "CALL_PHASE", "PLAYING", "TRICK_EVALUATION", "ROUND_END", "GAME_COMPLETE"
    val currentDealerIndex: Int, // 0..3
    val firstCardPlayerIndex: Int, // 0..3
    val currentTurnPlayerIndex: Int, // 0..3 - Tracks active player in the turn order
    val turnTimerSeconds: Int = 30,
    val totalCalls: Int = 0,
    val callPhasePrompt: String? = null,
    val statusMessage: String? = null,
    val isDealing: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Room Entity storing each player's state in a multiplayer game,
 * including player scores (cumulative totalScore, roundScore), call, tricks won,
 * and turn order / seat index.
 */
@Entity(
    tableName = "game_players",
    foreignKeys = [
        ForeignKey(
            entity = GameSessionEntity::class,
            parentColumns = ["gameId"],
            childColumns = ["gameId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["gameId"]),
        Index(value = ["gameId", "seatIndex"], unique = true)
    ]
)
data class GamePlayerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val gameId: String,
    val playerId: Int, // 1..4
    val seatIndex: Int, // 0: Bottom (Local), 1: Left, 2: Top, 3: Right
    val turnOrder: Int, // 0..3 relative turn position for current round
    val name: String,
    val isBot: Boolean,
    val isHost: Boolean = false,
    val connectionStatus: String = "CONNECTED", // CONNECTED, DISCONNECTED, RECONNECTING, BOT_ACTIVE
    val call: Int? = null, // 1..8
    val tricksWon: Int = 0,
    val roundScore: Int = 0,
    val totalScore: Int = 0, // Cumulative score across all rounds
    val remainingCardsCount: Int = 13,
    val isCurrentTurn: Boolean = false
)

/**
 * Room Entity storing individual card positions across all multiplayer participants,
 * the active trick table, the undealt deck, or discarded cards.
 */
@Entity(
    tableName = "card_positions",
    foreignKeys = [
        ForeignKey(
            entity = GameSessionEntity::class,
            parentColumns = ["gameId"],
            childColumns = ["gameId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["gameId"]),
        Index(value = ["gameId", "location"]),
        Index(value = ["gameId", "ownerSeatIndex"]),
        Index(value = ["gameId", "cardId"])
    ]
)
data class CardPositionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val gameId: String,
    val cardId: String, // e.g. "SPADES_ACE", "HEARTS_TEN"
    val suit: String, // "SPADES", "HEARTS", "CLUBS", "DIAMONDS"
    val rank: String, // "TWO", "THREE", ..., "ACE"
    val rankValue: Int, // 2..14
    val location: String, // "HAND", "TRICK_TABLE", "DECK", "DISCARDED"
    val ownerSeatIndex: Int? = null, // 0..3 (whose hand it belongs to or who played it into trick)
    val cardIndexInHand: Int? = null, // 0..12 (position/order in the player's fanned hand)
    val trickOrder: Int? = null, // 1..4 (play order in the current trick on the table)
    val isLeadCard: Boolean = false,
    val isLegalMove: Boolean = false
)

/**
 * Relational model aggregating the complete multiplayer game state:
 * session, players with scores & turn order, and all card positions.
 */
data class MultiplayerGameState(
    @Embedded val session: GameSessionEntity,
    @Relation(
        parentColumn = "gameId",
        entityColumn = "gameId"
    )
    val players: List<GamePlayerEntity>,
    @Relation(
        parentColumn = "gameId",
        entityColumn = "gameId"
    )
    val cardPositions: List<CardPositionEntity>
)

/**
 * Compact projection for player scores and turn standings.
 */
data class PlayerScoreSummary(
    val seatIndex: Int,
    val name: String,
    val totalScore: Int,
    val roundScore: Int,
    val tricksWon: Int,
    val call: Int?
)
