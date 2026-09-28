package com.example.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.Card
import com.example.model.Rank
import com.example.model.Suit
import com.example.ui.components.RealisticCard
import com.example.ui.theme.MotionConstants

@Composable
fun OpeningAnimationScreen(
    onAnimationEnd: () -> Unit
) {
    val cardFanProgress = remember { Animatable(0f) }
    val textAlpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        // Smooth playing-card shuffle/deal fanning movement using standardized motion spec
        cardFanProgress.animateTo(
            targetValue = 1f,
            animationSpec = MotionConstants.CardFanSpec
        )
        textAlpha.animateTo(
            targetValue = 1f,
            animationSpec = MotionConstants.TextFadeSpec
        )
        kotlinx.coroutines.delay(600)
        onAnimationEnd()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF0D2818),
                        Color(0xFF06140C)
                    )
                )
            )
            .testTag("opening_animation_screen"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Realistic 4-card animated shuffle/deal fan
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.height(140.dp)
            ) {
                // Diamond Jack (spreads left)
                RealisticCard(
                    card = Card(Suit.DIAMONDS, Rank.JACK),
                    width = 68.dp,
                    rotation = 0f,
                    elevation = 4.dp,
                    modifier = Modifier.graphicsLayer {
                        val p = cardFanProgress.value
                        rotationZ = -22f * p
                        translationX = (-55 * p).dp.toPx()
                        translationY = (6 * p).dp.toPx()
                    }
                )

                // Club Queen
                RealisticCard(
                    card = Card(Suit.CLUBS, Rank.QUEEN),
                    width = 70.dp,
                    rotation = 0f,
                    elevation = 6.dp,
                    modifier = Modifier.graphicsLayer {
                        val p = cardFanProgress.value
                        rotationZ = -8f * p
                        translationX = (-20 * p).dp.toPx()
                        translationY = (-2 * p).dp.toPx()
                    }
                )

                // Heart King
                RealisticCard(
                    card = Card(Suit.HEARTS, Rank.KING),
                    width = 72.dp,
                    rotation = 0f,
                    elevation = 8.dp,
                    modifier = Modifier.graphicsLayer {
                        val p = cardFanProgress.value
                        rotationZ = 8f * p
                        translationX = (20 * p).dp.toPx()
                        translationY = (-2 * p).dp.toPx()
                    }
                )

                // Ace of Spades (Centerpiece)
                RealisticCard(
                    card = Card(Suit.SPADES, Rank.ACE),
                    width = 74.dp,
                    rotation = 0f,
                    elevation = 12.dp,
                    modifier = Modifier.graphicsLayer {
                        val p = cardFanProgress.value
                        rotationZ = 22f * p
                        translationX = (55 * p).dp.toPx()
                        translationY = (6 * p).dp.toPx()
                    }
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Title & Author
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.alpha(textAlpha.value)
            ) {
                Text(
                    text = "Mobile Card Game",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.Serif,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Powered by Sudhamoy",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                    letterSpacing = 2.sp,
                    style = androidx.compose.ui.text.TextStyle(
                        shadow = androidx.compose.ui.graphics.Shadow(
                            color = Color.Black.copy(alpha = 0.45f),
                            offset = androidx.compose.ui.geometry.Offset(1f, 1f),
                            blurRadius = 3f
                        )
                    )
                )
            }
        }
    }
}
