package com.example.engine

import com.example.model.Card
import com.example.model.Player
import com.example.model.Rank
import com.example.model.Suit
import com.example.model.Trick

object BotEngine {

    /**
     * Determines a smart, legal call (1..8) for a bot player based on their hand.
     */
    fun calculateCall(hand: List<Card>): Int {
        var estimatedTricks = 0

        // High card value
        for (suit in Suit.values()) {
            val cardsInSuit = hand.filter { it.suit == suit }
            val hasAce = cardsInSuit.any { it.rank == Rank.ACE }
            val hasKing = cardsInSuit.any { it.rank == Rank.KING }
            val hasQueen = cardsInSuit.any { it.rank == Rank.QUEEN }

            if (hasAce) estimatedTricks += 1
            if (hasKing && cardsInSuit.size >= 2) estimatedTricks += 1
            if (hasQueen && cardsInSuit.size >= 3) estimatedTricks += 1
        }

        // Trump (Spades) length bonus
        val spades = hand.filter { it.suit == Suit.SPADES }
        if (spades.size >= 4) {
            estimatedTricks += (spades.size - 3)
        }

        // Clamp between 1 and 8
        return estimatedTricks.coerceIn(1, 8)
    }

    /**
     * Selects the optimal legal card to play from hand using authoritative rules.
     */
    fun chooseCardToPlay(
        botPlayer: Player,
        currentTrick: Trick,
        isFirstTrick: Boolean
    ): Card {
        val legalCards = AuthoritativeRuleEngine.getLegalCards(
            hand = botPlayer.cards,
            currentTrick = currentTrick,
            isFirstTrick = isFirstTrick
        )
        require(legalCards.isNotEmpty()) { "Bot has no legal cards to play" }

        val playedCards = currentTrick.playedCards
        val neededTricks = (botPlayer.call ?: 2) - botPlayer.tricksWon

        // Case 1: Bot is leading the trick
        if (playedCards.isEmpty()) {
            // If bot still needs tricks, prefer playing strong non-Spade Ace or King
            if (neededTricks > 0) {
                val nonSpadeAces = legalCards.filter { it.suit != Suit.SPADES && it.rank == Rank.ACE }
                if (nonSpadeAces.isNotEmpty()) return nonSpadeAces.first()

                val nonSpadeKings = legalCards.filter { it.suit != Suit.SPADES && it.rank == Rank.KING }
                if (nonSpadeKings.isNotEmpty()) return nonSpadeKings.first()
            }
            // Otherwise lead low card
            return legalCards.minByOrNull { it.rank.value } ?: legalCards.first()
        }

        // Case 2: Bot is following
        val currentWinnerIndex = AuthoritativeRuleEngine.determineTrickWinner(currentTrick)
        val leadSuit = currentTrick.leadSuit ?: playedCards.first().card.suit

        // If bot still needs tricks, try to win cheaply
        if (neededTricks > 0) {
            // Find lowest card among legal cards that would win the trick
            val winningCards = legalCards.filter { cardCandidate ->
                val simulatedTrick = currentTrick.copy(
                    playedCards = currentTrick.playedCards + com.example.model.PlayedCard(
                        playerIndex = botPlayer.seatIndex,
                        card = cardCandidate
                    )
                )
                AuthoritativeRuleEngine.determineTrickWinner(simulatedTrick) == botPlayer.seatIndex
            }

            if (winningCards.isNotEmpty()) {
                // Play lowest winning card
                return winningCards.minByOrNull { it.rank.value }!!
            }
        }

        // If bot doesn't need tricks or cannot win, play the lowest legal card to dump
        return legalCards.minByOrNull { it.rank.value } ?: legalCards.first()
    }
}
