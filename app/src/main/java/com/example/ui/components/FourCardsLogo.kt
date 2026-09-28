package com.example.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.model.Card
import com.example.model.Rank
import com.example.model.Suit

@Composable
fun FourCardsLogo(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(width = 240.dp, height = 150.dp)
            .padding(8.dp)
            .testTag("home_four_cards_logo"),
        contentAlignment = Alignment.Center
    ) {
        // Card 1: 10 of Diamonds ♦ (far left, angle -22 deg)
        RealisticCard(
            card = Card(Suit.DIAMONDS, Rank.JACK),
            width = 72.dp,
            elevation = 4.dp,
            rotation = -22f,
            modifier = Modifier.offset(x = (-62).dp, y = 8.dp)
        )

        // Card 2: Queen of Clubs ♣ (center left, angle -7 deg)
        RealisticCard(
            card = Card(Suit.CLUBS, Rank.QUEEN),
            width = 74.dp,
            elevation = 6.dp,
            rotation = -7f,
            modifier = Modifier.offset(x = (-22).dp, y = (-2).dp)
        )

        // Card 3: King of Hearts ♥ (center right, angle +8 deg)
        RealisticCard(
            card = Card(Suit.HEARTS, Rank.KING),
            width = 75.dp,
            elevation = 8.dp,
            rotation = 8f,
            modifier = Modifier.offset(x = 22.dp, y = (-2).dp)
        )

        // Card 4: Ace of Spades ♠ (foreground hero card, angle +22 deg)
        RealisticCard(
            card = Card(Suit.SPADES, Rank.ACE),
            width = 78.dp,
            elevation = 12.dp,
            rotation = 22f,
            modifier = Modifier.offset(x = 64.dp, y = 8.dp)
        )
    }
}
