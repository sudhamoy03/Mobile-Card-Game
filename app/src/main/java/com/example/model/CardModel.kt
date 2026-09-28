package com.example.model

enum class Suit(val symbol: String, val displayName: String, val isRed: Boolean, val order: Int) {
    SPADES("♠", "Spades", false, 4),
    HEARTS("♥", "Hearts", true, 3),
    CLUBS("♣", "Clubs", false, 2),
    DIAMONDS("♦", "Diamonds", true, 1)
}

enum class Rank(val value: Int, val label: String, val shortName: String) {
    TWO(2, "2", "2"),
    THREE(3, "3", "3"),
    FOUR(4, "4", "4"),
    FIVE(5, "5", "5"),
    SIX(6, "6", "6"),
    SEVEN(7, "7", "7"),
    EIGHT(8, "8", "8"),
    NINE(9, "9", "9"),
    TEN(10, "10", "10"),
    JACK(11, "Jack", "J"),
    QUEEN(12, "Queen", "Q"),
    KING(13, "King", "K"),
    ACE(14, "Ace", "A");

    val isFaceOrAce: Boolean
        get() = this in listOf(JACK, QUEEN, KING, ACE)
}

data class Card(
    val suit: Suit,
    val rank: Rank
) : Comparable<Card> {
    val id: String = "${suit.name}_${rank.name}"

    override fun compareTo(other: Card): Int {
        return if (this.suit == other.suit) {
            this.rank.value.compareTo(other.rank.value)
        } else {
            this.suit.order.compareTo(other.suit.order)
        }
    }

    override fun toString(): String = "${rank.shortName}${suit.symbol}"
}
