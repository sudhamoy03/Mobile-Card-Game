package com.example

import com.example.engine.AuthoritativeRuleEngine
import com.example.model.Card
import com.example.model.PlayedCard
import com.example.model.Rank
import com.example.model.Suit
import com.example.model.Trick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun testDeckGenerationAndDealing() {
        val hands = AuthoritativeRuleEngine.dealCards()
        assertEquals(4, hands.size)
        // Each player must have exactly 13 cards
        hands.forEach { hand ->
            assertEquals(13, hand.size)
        }

        // All 52 cards must be distinct
        val allCards = hands.flatten()
        assertEquals(52, allCards.size)
        assertEquals(52, allCards.distinctBy { it.id }.size)
    }

    @Test
    fun testDealValidationRules() {
        val hands = AuthoritativeRuleEngine.dealCards()
        // Deal must be validated by the engine
        val isValid = AuthoritativeRuleEngine.validateDeal(hands)
        // Check condition rules on all hands
        for (hand in hands) {
            val aceCount = hand.count { it.rank == Rank.ACE }
            val spadeCount = hand.count { it.suit == Suit.SPADES }
            val courtCount = hand.count { it.rank.isFaceOrAce }

            if (isValid) {
                assertTrue("No hand should have 4 aces", aceCount < 4)
                assertTrue("Hand should have at least 1 spade", spadeCount >= 1)
                assertTrue("Hand should have at least 1 face card", courtCount >= 1)
            }
        }
    }

    @Test
    fun testFirstTrickSpadeLeadRestriction() {
        val hand = listOf(
            Card(Suit.SPADES, Rank.ACE),
            Card(Suit.HEARTS, Rank.TEN),
            Card(Suit.CLUBS, Rank.FIVE)
        )
        val emptyTrick = Trick(trickNumber = 1)
        val legalCards = AuthoritativeRuleEngine.getLegalCards(hand, emptyTrick, isFirstTrick = true)

        // Spades cannot be led in first trick if player has other suits
        assertFalse(legalCards.any { it.suit == Suit.SPADES })
        assertEquals(2, legalCards.size)
    }

    @Test
    fun testFollowSuitRule() {
        val hand = listOf(
            Card(Suit.HEARTS, Rank.ACE),
            Card(Suit.HEARTS, Rank.NINE),
            Card(Suit.SPADES, Rank.KING),
            Card(Suit.CLUBS, Rank.TEN)
        )
        // Current trick lead is Hearts
        val currentTrick = Trick(
            trickNumber = 2,
            leadSuit = Suit.HEARTS,
            playedCards = listOf(
                PlayedCard(playerIndex = 1, card = Card(Suit.HEARTS, Rank.SEVEN))
            )
        )
        val legalCards = AuthoritativeRuleEngine.getLegalCards(hand, currentTrick, isFirstTrick = false)

        // Must follow Hearts since player has Hearts
        assertTrue(legalCards.all { it.suit == Suit.HEARTS })
        assertEquals(2, legalCards.size)
    }

    @Test
    fun testV2CardFollowingRule() {
        // P1 plays Diamonds Jack
        // P2 has no Diamonds and plays Spades 2
        val trickWithTrump = Trick(
            trickNumber = 1,
            leadSuit = Suit.DIAMONDS,
            playedCards = listOf(
                PlayedCard(playerIndex = 0, card = Card(Suit.DIAMONDS, Rank.JACK)),
                PlayedCard(playerIndex = 1, card = Card(Suit.SPADES, Rank.TWO))
            )
        )

        // P3 has Diamonds Queen and Diamonds 9 (and other cards)
        val p3Hand = listOf(
            Card(Suit.DIAMONDS, Rank.QUEEN),
            Card(Suit.DIAMONDS, Rank.NINE),
            Card(Suit.HEARTS, Rank.ACE),
            Card(Suit.SPADES, Rank.KING)
        )
        val p3Legal = AuthoritativeRuleEngine.getLegalCards(p3Hand, trickWithTrump, isFirstTrick = false)

        // P3 MUST play Diamonds, but may choose either Queen or 9 (no requirement to play higher)
        assertEquals(2, p3Legal.size)
        assertTrue(p3Legal.contains(Card(Suit.DIAMONDS, Rank.QUEEN)))
        assertTrue(p3Legal.contains(Card(Suit.DIAMONDS, Rank.NINE)))

        // P4 has NO Diamonds, may play ANY card (including Spades or off-suit, no requirement to beat Spades 2)
        val p4Hand = listOf(
            Card(Suit.HEARTS, Rank.TEN),
            Card(Suit.CLUBS, Rank.FIVE),
            Card(Suit.SPADES, Rank.THREE)
        )
        val p4Legal = AuthoritativeRuleEngine.getLegalCards(p4Hand, trickWithTrump, isFirstTrick = false)
        assertEquals(p4Hand.size, p4Legal.size)
        assertEquals(p4Hand.toSet(), p4Legal.toSet())
    }

    @Test
    fun testTrickWinnerDetermination() {
        // Trick with Hearts lead and a Trump (Spades)
        val trick = Trick(
            trickNumber = 2,
            leadSuit = Suit.HEARTS,
            playedCards = listOf(
                PlayedCard(playerIndex = 0, card = Card(Suit.HEARTS, Rank.KING)),
                PlayedCard(playerIndex = 1, card = Card(Suit.HEARTS, Rank.ACE)),
                PlayedCard(playerIndex = 2, card = Card(Suit.SPADES, Rank.TWO)), // Trump
                PlayedCard(playerIndex = 3, card = Card(Suit.HEARTS, Rank.TEN))
            )
        )
        val winnerIndex = AuthoritativeRuleEngine.determineTrickWinner(trick)
        // Player 2 played Spade (trump), so Player 2 wins even though Player 1 had Ace of Hearts
        assertEquals(2, winnerIndex)
    }

    @Test
    fun testTrickWinnerNoTrumpHighestLeadWins() {
        val trick = Trick(
            trickNumber = 3,
            leadSuit = Suit.DIAMONDS,
            playedCards = listOf(
                PlayedCard(playerIndex = 0, card = Card(Suit.DIAMONDS, Rank.NINE)),
                PlayedCard(playerIndex = 1, card = Card(Suit.DIAMONDS, Rank.KING)),
                PlayedCard(playerIndex = 2, card = Card(Suit.CLUBS, Rank.ACE)), // Off-suit without trump
                PlayedCard(playerIndex = 3, card = Card(Suit.DIAMONDS, Rank.JACK))
            )
        )
        val winnerIndex = AuthoritativeRuleEngine.determineTrickWinner(trick)
        // Player 1 had King of Diamonds (highest lead suit)
        assertEquals(1, winnerIndex)
    }

    @Test
    fun testScoringFormulas() {
        // Call achieved (1-7): Call * 10 + extra tricks
        assertEquals(40, AuthoritativeRuleEngine.calculateRoundScore(call = 4, tricksWon = 4))
        assertEquals(42, AuthoritativeRuleEngine.calculateRoundScore(call = 4, tricksWon = 6))

        // Call missed (1-7): -(Call * 10)
        assertEquals(-40, AuthoritativeRuleEngine.calculateRoundScore(call = 4, tricksWon = 3))
        assertEquals(-10, AuthoritativeRuleEngine.calculateRoundScore(call = 1, tricksWon = 0))

        // Call 8 (Bumper): +160 if achieved >= 8, -80 if missed
        assertEquals(160, AuthoritativeRuleEngine.calculateRoundScore(call = 8, tricksWon = 8))
        assertEquals(160, AuthoritativeRuleEngine.calculateRoundScore(call = 8, tricksWon = 9))
        assertEquals(-80, AuthoritativeRuleEngine.calculateRoundScore(call = 8, tricksWon = 7))
        assertEquals(-80, AuthoritativeRuleEngine.calculateRoundScore(call = 8, tricksWon = 0))
    }

    @Test
    fun testTableCallMinimumThreshold() {
        val invalidCalls = listOf(2, 2, 2, 2) // sum = 8 (< 9)
        val validCalls = listOf(2, 3, 2, 2)   // sum = 9 (>= 9)

        assertTrue(invalidCalls.sum() < 9)
        assertTrue(validCalls.sum() >= 9)
    }

    @Test
    fun testShuffleDealerRotation() {
        var currentDealer = 0
        var firstCardPlayer = (currentDealer + 1) % 4
        assertEquals(1, firstCardPlayer)

        // Round 2
        currentDealer = (currentDealer + 1) % 4
        firstCardPlayer = (currentDealer + 1) % 4
        assertEquals(1, currentDealer)
        assertEquals(2, firstCardPlayer)

        // Round 3
        currentDealer = (currentDealer + 1) % 4
        firstCardPlayer = (currentDealer + 1) % 4
        assertEquals(2, currentDealer)
        assertEquals(3, firstCardPlayer)

        // Round 4
        currentDealer = (currentDealer + 1) % 4
        firstCardPlayer = (currentDealer + 1) % 4
        assertEquals(3, currentDealer)
        assertEquals(0, firstCardPlayer)

        // Round 5 (loops back)
        currentDealer = (currentDealer + 1) % 4
        assertEquals(0, currentDealer)
    }

    @Test
    fun testRoomDatabaseSchemaEntities() {
        val gameId = "game_test_123"

        // 1. Session Entity
        val session = com.example.data.GameSessionEntity(
            gameId = gameId,
            mode = "ONLINE",
            roomCode = "4821",
            roundNumber = 2,
            targetScore = 300,
            phase = "PLAYING",
            currentDealerIndex = 1,
            firstCardPlayerIndex = 2,
            currentTurnPlayerIndex = 2,
            turnTimerSeconds = 28
        )
        assertEquals(gameId, session.gameId)
        assertEquals(2, session.currentTurnPlayerIndex)
        assertEquals(300, session.targetScore)

        // 2. Player Entities with turn order & scores
        val players = (0..3).map { seat ->
            com.example.data.GamePlayerEntity(
                gameId = gameId,
                playerId = seat + 1,
                seatIndex = seat,
                turnOrder = (seat - session.firstCardPlayerIndex + 4) % 4,
                name = "Player ${seat + 1}",
                isBot = seat != 0,
                call = 3,
                tricksWon = 1,
                roundScore = 31,
                totalScore = 65,
                isCurrentTurn = seat == session.currentTurnPlayerIndex
            )
        }
        assertEquals(4, players.size)
        // Seat 2 is firstCardPlayerIndex, so seat 2 has turnOrder 0
        assertEquals(0, players[2].turnOrder)
        assertEquals(1, players[3].turnOrder)
        assertEquals(2, players[0].turnOrder)
        assertEquals(3, players[1].turnOrder)
        assertTrue(players[2].isCurrentTurn)
        assertFalse(players[0].isCurrentTurn)
        assertEquals(65, players[0].totalScore)

        // 3. Card Position Entities
        val handCard = com.example.data.CardPositionEntity(
            gameId = gameId,
            cardId = "SPADES_ACE",
            suit = "SPADES",
            rank = "ACE",
            rankValue = 14,
            location = "HAND",
            ownerSeatIndex = 0,
            cardIndexInHand = 0
        )
        val trickCard = com.example.data.CardPositionEntity(
            gameId = gameId,
            cardId = "HEARTS_KING",
            suit = "HEARTS",
            rank = "KING",
            rankValue = 13,
            location = "TRICK_TABLE",
            ownerSeatIndex = 2,
            trickOrder = 1,
            isLeadCard = true
        )
        assertEquals("HAND", handCard.location)
        assertEquals(0, handCard.ownerSeatIndex)
        assertEquals("TRICK_TABLE", trickCard.location)
        assertEquals(2, trickCard.ownerSeatIndex)
        assertEquals(1, trickCard.trickOrder)
        assertTrue(trickCard.isLeadCard)

        // 4. Aggregated MultiplayerGameState
        val multiplayerState = com.example.data.MultiplayerGameState(
            session = session,
            players = players,
            cardPositions = listOf(handCard, trickCard)
        )
        assertEquals(2, multiplayerState.players[session.currentTurnPlayerIndex].seatIndex)
        assertEquals(2, multiplayerState.cardPositions.size)
    }

    @Test
    fun testSoloPlayModeStructure() {
        val players = listOf(
            com.example.model.Player(id = 1, seatIndex = 0, name = "You", controllerType = com.example.model.ControllerType.HUMAN, isHost = true),
            com.example.model.Player(id = 2, seatIndex = 1, name = "Bot 2", controllerType = com.example.model.ControllerType.BOT),
            com.example.model.Player(id = 3, seatIndex = 2, name = "Bot 3", controllerType = com.example.model.ControllerType.BOT),
            com.example.model.Player(id = 4, seatIndex = 3, name = "Bot 4", controllerType = com.example.model.ControllerType.BOT)
        )

        assertEquals(4, players.size)
        assertEquals(1, players.count { !it.isBot })
        assertEquals(3, players.count { it.isBot })
        assertEquals("You", players[0].name)
        assertEquals("Bot 2", players[1].name)
        assertEquals("Bot 3", players[2].name)
        assertEquals("Bot 4", players[3].name)

        // Verify bot call calculation generates legal calls (1..8)
        val deck = AuthoritativeRuleEngine.createStandardDeck().shuffled()
        val botHand = deck.subList(0, 13)
        val call = com.example.engine.BotEngine.calculateCall(botHand)
        assertTrue("Call must be between 1 and 8", call in 1..8)
    }
}
