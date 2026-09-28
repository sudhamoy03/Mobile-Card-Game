package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface GameDao {

    // ==========================================
    // 1. LEGACY ACTIVE GAME & HISTORY
    // ==========================================

    @Query("SELECT * FROM active_game WHERE id = 1 LIMIT 1")
    fun getActiveGame(): Flow<ActiveGameEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveActiveGame(game: ActiveGameEntity)

    @Query("DELETE FROM active_game WHERE id = 1")
    suspend fun clearActiveGame()

    @Query("SELECT * FROM completed_games ORDER BY timestamp DESC")
    fun getAllCompletedGames(): Flow<List<CompletedGameEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCompletedGame(game: CompletedGameEntity)

    @Query("DELETE FROM completed_games")
    suspend fun clearHistory()

    // ==========================================
    // 2. MULTIPLAYER GAME SESSION & STATE
    // ==========================================

    @Transaction
    @Query("SELECT * FROM game_sessions WHERE gameId = :gameId LIMIT 1")
    fun getMultiplayerGameState(gameId: String): Flow<MultiplayerGameState?>

    @Transaction
    @Query("SELECT * FROM game_sessions ORDER BY updatedAt DESC LIMIT 1")
    fun getLatestMultiplayerGameState(): Flow<MultiplayerGameState?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGameSession(session: GameSessionEntity)

    @Query("DELETE FROM game_sessions WHERE gameId = :gameId")
    suspend fun deleteGameSession(gameId: String)

    @Query("DELETE FROM game_sessions")
    suspend fun clearAllGameSessions()

    // ==========================================
    // 3. TURN ORDER & ACTIVE TURN
    // ==========================================

    @Query("SELECT currentTurnPlayerIndex FROM game_sessions WHERE gameId = :gameId LIMIT 1")
    fun getTurnOrder(gameId: String): Flow<Int?>

    @Query("UPDATE game_sessions SET currentTurnPlayerIndex = :nextTurnIndex, updatedAt = :updatedAt WHERE gameId = :gameId")
    suspend fun updateTurnOrder(gameId: String, nextTurnIndex: Int, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE game_sessions SET phase = :phase, statusMessage = :statusMessage, updatedAt = :updatedAt WHERE gameId = :gameId")
    suspend fun updateGamePhase(gameId: String, phase: String, statusMessage: String?, updatedAt: Long = System.currentTimeMillis())

    // ==========================================
    // 4. PLAYER SCORES & PLAYER ROSTER
    // ==========================================

    @Query("SELECT * FROM game_players WHERE gameId = :gameId ORDER BY seatIndex ASC")
    fun getPlayersForGame(gameId: String): Flow<List<GamePlayerEntity>>

    @Query("SELECT seatIndex, name, totalScore, roundScore, tricksWon, call FROM game_players WHERE gameId = :gameId ORDER BY seatIndex ASC")
    fun getPlayerScores(gameId: String): Flow<List<PlayerScoreSummary>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlayers(players: List<GamePlayerEntity>)

    @Query("UPDATE game_players SET totalScore = :totalScore, roundScore = :roundScore, tricksWon = :tricksWon WHERE gameId = :gameId AND seatIndex = :seatIndex")
    suspend fun updatePlayerScores(gameId: String, seatIndex: Int, totalScore: Int, roundScore: Int, tricksWon: Int)

    @Query("UPDATE game_players SET call = :call WHERE gameId = :gameId AND seatIndex = :seatIndex")
    suspend fun updatePlayerCall(gameId: String, seatIndex: Int, call: Int)

    @Query("UPDATE game_players SET isCurrentTurn = (seatIndex = :activeSeatIndex) WHERE gameId = :gameId")
    suspend fun setActiveTurnPlayer(gameId: String, activeSeatIndex: Int)

    // ==========================================
    // 5. CARD POSITIONS & TABLE TRACKING
    // ==========================================

    @Query("SELECT * FROM card_positions WHERE gameId = :gameId ORDER BY id ASC")
    fun getAllCardPositions(gameId: String): Flow<List<CardPositionEntity>>

    @Query("SELECT * FROM card_positions WHERE gameId = :gameId AND location = :location ORDER BY cardIndexInHand ASC")
    fun getCardsByLocation(gameId: String, location: String): Flow<List<CardPositionEntity>>

    @Query("SELECT * FROM card_positions WHERE gameId = :gameId AND ownerSeatIndex = :seatIndex AND location = 'HAND' ORDER BY cardIndexInHand ASC")
    fun getHandCardPositions(gameId: String, seatIndex: Int): Flow<List<CardPositionEntity>>

    @Query("SELECT * FROM card_positions WHERE gameId = :gameId AND location = 'TRICK_TABLE' ORDER BY trickOrder ASC")
    fun getTrickTableCardPositions(gameId: String): Flow<List<CardPositionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCardPositions(cards: List<CardPositionEntity>)

    @Query("UPDATE card_positions SET location = :location, ownerSeatIndex = :ownerSeatIndex, cardIndexInHand = :cardIndexInHand, trickOrder = :trickOrder WHERE gameId = :gameId AND cardId = :cardId")
    suspend fun updateCardPosition(
        gameId: String,
        cardId: String,
        location: String,
        ownerSeatIndex: Int?,
        cardIndexInHand: Int?,
        trickOrder: Int?
    )

    @Query("UPDATE card_positions SET location = 'TRICK_TABLE', ownerSeatIndex = :playerSeatIndex, trickOrder = :trickOrder, cardIndexInHand = null WHERE gameId = :gameId AND cardId = :cardId")
    suspend fun playCardToTrick(gameId: String, cardId: String, playerSeatIndex: Int, trickOrder: Int)

    @Query("UPDATE card_positions SET location = 'DISCARDED', trickOrder = null WHERE gameId = :gameId AND location = 'TRICK_TABLE'")
    suspend fun clearTrickToDiscard(gameId: String)

    @Query("DELETE FROM card_positions WHERE gameId = :gameId")
    suspend fun clearCardsForGame(gameId: String)

    @Query("DELETE FROM game_players WHERE gameId = :gameId")
    suspend fun clearPlayersForGame(gameId: String)

    // ==========================================
    // 6. ATOMIC FULL STATE SAVE
    // ==========================================

    @Transaction
    suspend fun saveCompleteMultiplayerState(
        session: GameSessionEntity,
        players: List<GamePlayerEntity>,
        cards: List<CardPositionEntity>
    ) {
        insertGameSession(session)
        clearPlayersForGame(session.gameId)
        insertPlayers(players)
        clearCardsForGame(session.gameId)
        insertCardPositions(cards)
    }
}
