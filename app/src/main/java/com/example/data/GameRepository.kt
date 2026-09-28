package com.example.data

import com.example.model.Card
import com.example.model.GameMode
import com.example.model.GamePhase
import com.example.model.GameState
import com.example.model.Player
import com.example.model.Rank
import com.example.model.Suit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class GameRepository(private val gameDao: GameDao) {

    // ==========================================
    // 1. LEGACY ACTIVE GAME SNAPSHOT FLOW
    // ==========================================

    val activeGameFlow: Flow<GameState?> = gameDao.getActiveGame().map { entity ->
        if (entity == null) null
        else {
            try {
                GameState(
                    gameId = entity.gameId,
                    mode = GameMode.valueOf(entity.mode),
                    roomCode = entity.roomCode,
                    phase = GamePhase.valueOf(entity.phase),
                    players = GameSerializer.deserializePlayers(entity.serializedPlayers),
                    currentDealerIndex = entity.currentDealerIndex,
                    firstCardPlayerIndex = entity.firstCardPlayerIndex,
                    currentTurnPlayerIndex = entity.currentTurnPlayerIndex,
                    currentTrick = GameSerializer.deserializeTrick(org.json.JSONObject(entity.serializedCurrentTrick)),
                    completedTricks = GameSerializer.deserializeTricks(entity.serializedCompletedTricks),
                    roundNumber = entity.roundNumber,
                    targetScore = entity.targetScore
                )
            } catch (_: Exception) {
                null
            }
        }
    }

    val completedGamesFlow: Flow<List<CompletedGameEntity>> = gameDao.getAllCompletedGames()

    // ==========================================
    // 2. NORMALIZED MULTIPLAYER FLOWS & QUERIES
    // ==========================================

    val latestMultiplayerGameStateFlow: Flow<MultiplayerGameState?> =
        gameDao.getLatestMultiplayerGameState()

    fun getMultiplayerGameStateFlow(gameId: String): Flow<MultiplayerGameState?> =
        gameDao.getMultiplayerGameState(gameId)

    fun getPlayerScoresFlow(gameId: String): Flow<List<PlayerScoreSummary>> =
        gameDao.getPlayerScores(gameId)

    fun getTurnOrderFlow(gameId: String): Flow<Int?> =
        gameDao.getTurnOrder(gameId)

    fun getHandCardsFlow(gameId: String, seatIndex: Int): Flow<List<CardPositionEntity>> =
        gameDao.getHandCardPositions(gameId, seatIndex)

    fun getTrickCardsFlow(gameId: String): Flow<List<CardPositionEntity>> =
        gameDao.getTrickTableCardPositions(gameId)

    // ==========================================
    // 3. PERSISTENCE & MUTATION LOGIC
    // ==========================================

    suspend fun saveActiveGame(state: GameState) {
        if (state.gameId.isBlank() || state.players.size != 4) return

        // 1. Save legacy snapshot
        val entity = ActiveGameEntity(
            id = 1,
            gameId = state.gameId,
            mode = state.mode.name,
            roomCode = state.roomCode,
            serializedPlayers = GameSerializer.serializePlayers(state.players),
            currentDealerIndex = state.currentDealerIndex,
            firstCardPlayerIndex = state.firstCardPlayerIndex,
            currentTurnPlayerIndex = state.currentTurnPlayerIndex,
            serializedCurrentTrick = GameSerializer.serializeTrick(state.currentTrick).toString(),
            serializedCompletedTricks = GameSerializer.serializeTricks(state.completedTricks),
            roundNumber = state.roundNumber,
            targetScore = state.targetScore,
            phase = state.phase.name
        )
        gameDao.saveActiveGame(entity)

        // 2. Save normalized multiplayer schema (GameSession, GamePlayers, CardPositions)
        val sessionEntity = GameSessionEntity(
            gameId = state.gameId,
            mode = state.mode.name,
            roomCode = state.roomCode,
            roundNumber = state.roundNumber,
            targetScore = state.targetScore,
            phase = state.phase.name,
            currentDealerIndex = state.currentDealerIndex,
            firstCardPlayerIndex = state.firstCardPlayerIndex,
            currentTurnPlayerIndex = state.currentTurnPlayerIndex,
            turnTimerSeconds = state.turnTimerSeconds,
            totalCalls = state.totalCalls,
            callPhasePrompt = state.callPhasePrompt,
            statusMessage = state.statusMessage,
            isDealing = state.isDealing,
            updatedAt = System.currentTimeMillis()
        )

        val playerEntities = state.players.map { player ->
            // Turn order relative to firstCardPlayerIndex (0 = leads, 1 = second, 2 = third, 3 = last)
            val turnOrder = (player.seatIndex - state.firstCardPlayerIndex + 4) % 4
            GamePlayerEntity(
                gameId = state.gameId,
                playerId = player.id,
                seatIndex = player.seatIndex,
                turnOrder = turnOrder,
                name = player.name,
                isBot = player.isBot,
                isHost = player.isHost,
                connectionStatus = player.connectionStatus.name,
                call = player.call,
                tricksWon = player.tricksWon,
                roundScore = player.roundScore,
                totalScore = player.totalScore,
                remainingCardsCount = player.remainingCardsCount,
                isCurrentTurn = player.seatIndex == state.currentTurnPlayerIndex
            )
        }

        val cardEntities = mutableListOf<CardPositionEntity>()

        // Hand cards across all players
        state.players.forEach { player ->
            player.cards.forEachIndexed { index, card ->
                cardEntities.add(
                    CardPositionEntity(
                        gameId = state.gameId,
                        cardId = card.id,
                        suit = card.suit.name,
                        rank = card.rank.name,
                        rankValue = card.rank.value,
                        location = "HAND",
                        ownerSeatIndex = player.seatIndex,
                        cardIndexInHand = index,
                        trickOrder = null,
                        isLeadCard = false,
                        isLegalMove = false
                    )
                )
            }
        }

        // Trick table cards
        state.currentTrick.playedCards.forEachIndexed { index, playedCard ->
            cardEntities.add(
                CardPositionEntity(
                    gameId = state.gameId,
                    cardId = playedCard.card.id,
                    suit = playedCard.card.suit.name,
                    rank = playedCard.card.rank.name,
                    rankValue = playedCard.card.rank.value,
                    location = "TRICK_TABLE",
                    ownerSeatIndex = playedCard.playerIndex,
                    cardIndexInHand = null,
                    trickOrder = index + 1,
                    isLeadCard = index == 0,
                    isLegalMove = true
                )
            )
        }

        // Completed tricks moved to discarded
        state.completedTricks.forEach { trick ->
            trick.playedCards.forEach { playedCard ->
                cardEntities.add(
                    CardPositionEntity(
                        gameId = state.gameId,
                        cardId = playedCard.card.id,
                        suit = playedCard.card.suit.name,
                        rank = playedCard.card.rank.name,
                        rankValue = playedCard.card.rank.value,
                        location = "DISCARDED",
                        ownerSeatIndex = playedCard.playerIndex,
                        cardIndexInHand = null,
                        trickOrder = null,
                        isLeadCard = false,
                        isLegalMove = false
                    )
                )
            }
        }

        gameDao.saveCompleteMultiplayerState(
            session = sessionEntity,
            players = playerEntities,
            cards = cardEntities
        )
    }

    suspend fun clearActiveGame() {
        gameDao.clearActiveGame()
        gameDao.clearAllGameSessions()
    }

    suspend fun recordCompletedGame(
        gameId: String,
        mode: GameMode,
        players: List<Player>,
        winnerName: String,
        winnerScore: Int,
        targetScore: Int
    ) {
        val playerNames = players.joinToString(", ") { it.name }
        val finalScores = players.joinToString(", ") { "${it.name}: ${it.totalScore}" }
        val entity = CompletedGameEntity(
            gameId = gameId,
            mode = mode.name,
            playerNames = playerNames,
            finalScores = finalScores,
            winnerName = winnerName,
            winnerScore = winnerScore,
            targetScore = targetScore
        )
        gameDao.insertCompletedGame(entity)
    }

    suspend fun clearHistory() {
        gameDao.clearHistory()
    }
}
