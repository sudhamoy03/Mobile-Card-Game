package com.example.engine

import com.example.model.Card
import com.example.model.PlayedCard
import com.example.model.Player
import com.example.model.Rank
import com.example.model.Suit
import com.example.model.Trick
import kotlin.random.Random

object AuthoritativeRuleEngine {

    /**
     * Generates a fresh, full 52-card standard deck.
     */
    fun createStandardDeck(): List<Card> {
        val deck = mutableListOf<Card>()
        for (suit in Suit.values()) {
            for (rank in Rank.values()) {
                deck.add(Card(suit, rank))
            }
        }
        return deck
    }

    /**
     * Performs an authoritative, unbiased shuffle and deals 13 cards to each of the 4 players.
     */
    fun dealCards(random: Random = Random.Default): List<List<Card>> {
        val deck = createStandardDeck().shuffled(random)
        return listOf(
            deck.subList(0, 13).sorted(),
            deck.subList(13, 26).sorted(),
            deck.subList(26, 39).sorted(),
            deck.subList(39, 52).sorted()
        )
    }

    /**
     * Validates deal integrity and rules:
     * 1. Total exactly 52 cards, 4 hands of 13 unique cards.
     * 2. No player has all four Aces.
     * 3. Every player has at least one Spade.
     * 4. Every player has at least one J/Q/K/A.
     * Returns true if valid, false if deal should be DISMISSED.
     */
    fun validateDeal(hands: List<List<Card>>): Boolean {
        if (hands.size != 4) return false
        val allCards = hands.flatten()
        if (allCards.size != 52) return false
        if (allCards.distinctBy { it.id }.size != 52) return false

        for (hand in hands) {
            if (hand.size != 13) return false

            // Rule 1: No player has all four Aces
            val aceCount = hand.count { it.rank == Rank.ACE }
            if (aceCount == 4) return false

            // Rule 2: Every player has at least one Spade
            val spadeCount = hand.count { it.suit == Suit.SPADES }
            if (spadeCount == 0) return false

            // Rule 3: Every player has at least one J/Q/K/A
            val faceOrAceCount = hand.count { it.rank.isFaceOrAce }
            if (faceOrAceCount == 0) return false
        }

        return true
    }

    /**
     * Calculate legal cards a player can play given the current trick context and player's hand.
     */
    fun getLegalCards(
        hand: List<Card>,
        currentTrick: Trick,
        isFirstTrick: Boolean
    ): List<Card> {
        if (hand.isEmpty()) return emptyList()

        val playedCards = currentTrick.playedCards
        // If leading the trick:
        if (playedCards.isEmpty()) {
            // First trick rule: first card of the first trick cannot be Spades unless player has only Spades
            if (isFirstTrick) {
                val nonSpades = hand.filter { it.suit != Suit.SPADES }
                if (nonSpades.isNotEmpty()) {
                    return nonSpades
                }
            }
            return hand
        }

        // The first card played establishes the LEAD SUIT
        val leadSuit = currentTrick.leadSuit ?: playedCards.first().card.suit
        val cardsOfLeadSuit = hand.filter { it.suit == leadSuit }

        // If a player has one or more cards of the LEAD SUIT, that player MUST play a card from the LEAD SUIT.
        // There is NO requirement to play a higher card.
        // If the player has multiple cards of the LEAD SUIT, they may choose ANY card from that suit, whether higher or lower.
        if (cardsOfLeadSuit.isNotEmpty()) {
            return cardsOfLeadSuit
        }

        // If the player has NO card of the LEAD SUIT, they may play ANY card from their hand.
        return hand
    }

    /**
     * Determines the winner of a completed 4-card trick.
     * Returns the playerIndex (0..3) of the winning player.
     */
    fun determineTrickWinner(trick: Trick): Int {
        val played = trick.playedCards
        require(played.isNotEmpty()) { "Trick cannot be empty" }

        val leadSuit = trick.leadSuit ?: played.first().card.suit
        val spadesPlayed = played.filter { it.card.suit == Suit.SPADES }

        val winningPlayedCard = if (spadesPlayed.isNotEmpty()) {
            spadesPlayed.maxByOrNull { it.card.rank.value }!!
        } else {
            played.filter { it.card.suit == leadSuit }.maxByOrNull { it.card.rank.value }!!
        }

        return winningPlayedCard.playerIndex
    }

    /**
     * Calculate score for a round for a single player.
     * Calls 1-7:
     *   Achieved >= Call: Call * 10 + (Achieved - Call)
     *   Achieved < Call: -(Call * 10)
     * Call 8 (Bumper):
     *   Achieved >= 8: +160
     *   Achieved < 8: -80
     */
    fun calculateRoundScore(call: Int, tricksWon: Int): Int {
        return if (call == 8) {
            if (tricksWon >= 8) 160 else -80
        } else {
            if (tricksWon >= call) {
                (call * 10) + (tricksWon - call)
            } else {
                -(call * 10)
            }
        }
    }

    /**
     * Calculate ranking for players (1st, 2nd, 3rd, 4th) based on total score descending.
     */
    fun calculateRankings(players: List<Player>): List<Pair<Player, Int>> {
        val sorted = players.sortedByDescending { it.totalScore }
        return sorted.mapIndexed { index, player ->
            player to (index + 1)
        }
    }

    /**
     * Validates calls: 1..8 allowed, sum must be >= 9.
     */
    fun isCallValid(call: Int): Boolean = call in 1..8

    fun isTotalCallValid(calls: List<Int?>): Boolean {
        if (calls.any { it == null }) return false
        val sum = calls.filterNotNull().sum()
        return sum >= 9
    }
}
