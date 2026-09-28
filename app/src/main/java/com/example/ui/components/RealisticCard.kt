package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.graphicsLayer
import com.example.model.Card
import com.example.model.Rank
import com.example.model.Suit
import com.example.ui.theme.MotionConstants
import com.example.ui.theme.CardCream
import com.example.ui.theme.CardWhite
import com.example.ui.theme.SuitDark
import com.example.ui.theme.SuitRed

@Composable
fun RealisticCard(
    card: Card?,
    modifier: Modifier = Modifier,
    isFaceUp: Boolean = true,
    isSelected: Boolean = false,
    isPlayable: Boolean = true,
    width: Dp = 60.dp,
    elevation: Dp = 6.dp,
    rotation: Float = 0f,
    onClick: (() -> Unit)? = null
) {
    val animatedElevation by animateDpAsState(
        targetValue = if (isSelected) elevation + 4.dp else elevation,
        animationSpec = MotionConstants.CardElevationDpSpec,
        label = "cardElevation"
    )

    val animatedOffsetY by animateDpAsState(
        targetValue = if (isSelected) (-10).dp else 0.dp,
        animationSpec = MotionConstants.CardLiftDpSpec,
        label = "cardOffset"
    )

    val cornerRadius = width * 0.12f
    val shape = RoundedCornerShape(cornerRadius)

    val cardModifier = modifier
        .width(width)
        .aspectRatio(0.714f) // standard 2.5 : 3.5 ratio (~5:7)
        .graphicsLayer {
            translationY = animatedOffsetY.toPx()
            rotationZ = rotation
            shadowElevation = animatedElevation.toPx()
            this.shape = shape
            this.clip = true
        }
        .then(
            if (isSelected) {
                Modifier.border(
                    BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
                    shape
                )
            } else {
                Modifier.border(
                    BorderStroke(0.6.dp, Color(0xFFD4AF37).copy(alpha = 0.5f)),
                    shape
                )
            }
        )
        .then(
            if (onClick != null) {
                Modifier.clickable(enabled = isPlayable, onClick = onClick)
            } else {
                Modifier
            }
        )
        .testTag("card_${card?.id ?: "back"}")

    if (!isFaceUp || card == null) {
        CardBack(modifier = cardModifier, width = width, shape = shape)
    } else {
        CardFace(
            card = card,
            modifier = cardModifier,
            width = width,
            isPlayable = isPlayable
        )
    }
}

private val CardFaceGradient = Brush.verticalGradient(
    colors = listOf(
        CardWhite,
        CardCream,
        CardWhite
    )
)

private val CardBackGradient = Brush.radialGradient(
    colors = listOf(
        Color(0xFF283E6B),
        Color(0xFF0F1B33)
    )
)

@Composable
private fun CardFace(
    card: Card,
    modifier: Modifier = Modifier,
    width: Dp,
    isPlayable: Boolean
) {
    val suitColor = if (card.suit.isRed) SuitRed else SuitDark
    val fontSize = (width.value * 0.24f).sp
    val suitFontSize = (width.value * 0.22f).sp
    val centerSuitSize = (width.value * 0.42f).sp

    Surface(
        modifier = modifier,
        color = CardWhite,
        shape = RoundedCornerShape(width * 0.12f)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(CardFaceGradient)
                .padding(width * 0.08f)
        ) {
            // Subtle card inner border
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .border(
                        BorderStroke(0.5.dp, Color(0xFFE8E8E8)),
                        RoundedCornerShape(width * 0.08f)
                    )
            )

            // Top-Left Index (Rank + Suit)
            Column(
                modifier = Modifier.align(Alignment.TopStart),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = card.rank.shortName,
                    color = suitColor,
                    fontSize = fontSize,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Serif,
                    lineHeight = fontSize
                )
                Text(
                    text = card.suit.symbol,
                    color = suitColor,
                    fontSize = suitFontSize,
                    lineHeight = suitFontSize
                )
            }

            // Center Symbol / Graphic
            Box(
                modifier = Modifier.align(Alignment.Center),
                contentAlignment = Alignment.Center
            ) {
                if (card.rank.isFaceOrAce) {
                    // Face card badge
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = card.suit.symbol,
                            color = suitColor,
                            fontSize = centerSuitSize,
                            fontWeight = FontWeight.Bold
                        )
                        if (card.rank == Rank.ACE) {
                            Text(
                                text = "ACE",
                                color = suitColor,
                                fontSize = (width.value * 0.14f).sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        } else {
                            Text(
                                text = card.rank.shortName,
                                color = suitColor,
                                fontSize = (width.value * 0.18f).sp,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = FontFamily.Serif
                            )
                        }
                    }
                } else {
                    Text(
                        text = card.suit.symbol,
                        color = suitColor,
                        fontSize = centerSuitSize
                    )
                }
            }

            // Bottom-Right Inverted Index
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .graphicsLayer { rotationZ = 180f },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = card.rank.shortName,
                    color = suitColor,
                    fontSize = fontSize,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Serif,
                    lineHeight = fontSize
                )
                Text(
                    text = card.suit.symbol,
                    color = suitColor,
                    fontSize = suitFontSize,
                    lineHeight = suitFontSize
                )
            }

            // Dim overlay if not playable
            if (!isPlayable) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.35f))
                )
            }
        }
    }
}

@Composable
fun CardBack(
    modifier: Modifier = Modifier,
    width: Dp,
    shape: androidx.compose.ui.graphics.Shape
) {
    Surface(
        modifier = modifier,
        color = Color(0xFF1B2A4A),
        shape = shape
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(CardBackGradient)
                .padding(width * 0.08f)
        ) {
            // Intricate gold geometric casino back pattern
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .border(
                        BorderStroke(1.2.dp, Color(0xFFD4AF37).copy(alpha = 0.8f)),
                        RoundedCornerShape(width * 0.08f)
                    )
                    .padding(3.dp)
                    .border(
                        BorderStroke(0.6.dp, Color(0xFFD4AF37).copy(alpha = 0.4f)),
                        RoundedCornerShape(width * 0.06f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "♠ ♣",
                        color = Color(0xFFD4AF37).copy(alpha = 0.9f),
                        fontSize = (width.value * 0.16f).sp
                    )
                    Text(
                        text = "MCG",
                        color = Color(0xFFD4AF37),
                        fontSize = (width.value * 0.18f).sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                    Text(
                        text = "♥ ♦",
                        color = Color(0xFFD4AF37).copy(alpha = 0.9f),
                        fontSize = (width.value * 0.16f).sp
                    )
                }
            }
        }
    }
}
