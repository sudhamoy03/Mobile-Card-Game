package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.key
import com.example.model.Card
import com.example.model.PlayedCard
import com.example.model.Trick
import com.example.ui.theme.MotionConstants
import kotlinx.coroutines.delay

private val DropTargetFeltBrush = Brush.radialGradient(
    colors = listOf(
        Color(0xFF1E6B3B),
        Color(0xFF124524),
        Color(0xFF071F11)
    )
)

private val DefaultFeltBrush = Brush.radialGradient(
    colors = listOf(
        Color(0xFF144D29),
        Color(0xFF0D331B),
        Color(0xFF061A0E)
    )
)

@Composable
fun TableFelt(
    currentTrick: Trick,
    modifier: Modifier = Modifier,
    trickWinnerName: String? = null,
    isDropTargetActive: Boolean = false
) {
    val feltBorderColor = if (isDropTargetActive) MaterialTheme.colorScheme.primary else Color(0xFFD4AF37).copy(alpha = 0.45f)
    val feltBorderWidth = if (isDropTargetActive) 2.5.dp else 1.2.dp

    val winnerIndex = currentTrick.winnerIndex
    val isCollecting = winnerIndex != null

    // Subtle winner glow transition
    val infiniteTransition = rememberInfiniteTransition(label = "winnerGlow")
    val winnerGlowPulse by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "winnerGlowPulse"
    )

    // Collection timeline animatables:
    // 0 to 500ms: pause / winner highlight
    // 500ms to 1200ms: gather toward center
    // 1200ms to 2400ms: glide smoothly toward winner
    // 2400ms to 2700ms: settle & fade
    val gatherProgress = remember { Animatable(0f) }
    val winnerCollectProgress = remember { Animatable(0f) }
    val collectAlpha = remember { Animatable(1f) }

    LaunchedEffect(winnerIndex) {
        if (winnerIndex != null) {
            gatherProgress.snapTo(0f)
            winnerCollectProgress.snapTo(0f)
            collectAlpha.snapTo(1f)

            // 1. Short Pause & Winner Highlight (500ms)
            delay(500)

            // 2. Cards Gather toward center (600ms)
            gatherProgress.animateTo(
                targetValue = 1f,
                animationSpec = MotionConstants.CardGatherSpec
            )

            // 3. Cards move smoothly toward winner's position (1200ms)
            winnerCollectProgress.animateTo(
                targetValue = 1f,
                animationSpec = MotionConstants.CardCollectSpec
            )

            // 4. Smoothly settle & fade (400ms)
            collectAlpha.animateTo(
                targetValue = 0f,
                animationSpec = MotionConstants.CardFadeOutSpec
            )
        } else {
            gatherProgress.snapTo(0f)
            winnerCollectProgress.snapTo(0f)
            collectAlpha.snapTo(1f)
        }
    }

    Box(
        modifier = modifier
            .testTag("game_table_felt")
            .size(width = 380.dp, height = 180.dp),
        contentAlignment = Alignment.Center
    ) {
        // Felt surface oval
        Box(
            modifier = Modifier
                .fillMaxSize()
                .shadow(if (isDropTargetActive) 16.dp else 10.dp, RoundedCornerShape(90.dp))
                .clip(RoundedCornerShape(90.dp))
                .background(if (isDropTargetActive) DropTargetFeltBrush else DefaultFeltBrush)
                .border(
                    BorderStroke(feltBorderWidth, feltBorderColor),
                    RoundedCornerShape(90.dp)
                )
                .padding(4.dp)
                .border(
                    BorderStroke(0.7.dp, if (isDropTargetActive) MaterialTheme.colorScheme.primary else Color(0xFFD4AF37).copy(alpha = 0.25f)),
                    RoundedCornerShape(86.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            // Subtle watermark or active drop indicator
            if (isDropTargetActive) {
                Text(
                    text = "⬇ RELEASE TO PLAY ⬇",
                    color = Color(0xFF6EE7B7),
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp
                )
            } else {
                Text(
                    text = "♠ CALL BREAK ♠",
                    color = Color(0xFFD4AF37).copy(alpha = 0.14f),
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp
                )
            }

            // Cards played in current trick with 1-1.5s physical entrance & settling
            currentTrick.playedCards.forEach { played ->
                key(played.card.id) {
                    val isWinningCard = winnerIndex == played.playerIndex

                    PlayedCardItem(
                        played = played,
                        isWinningCard = isWinningCard,
                        winnerIndex = winnerIndex,
                        gatherProgress = { gatherProgress.value },
                        winnerCollectProgress = { winnerCollectProgress.value },
                        overallAlpha = { collectAlpha.value },
                        winnerGlowPulse = { if (isWinningCard) winnerGlowPulse else 0f }
                    )
                }
            }

            // Restrained, elegant trick winner badge (positioned at top of felt, NOT obscuring center cards)
            AnimatedVisibility(
                visible = trickWinnerName != null && isCollecting,
                enter = fadeIn(tween(300)),
                exit = fadeOut(tween(300)),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = 6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xE60A1D13))
                        .border(0.8.dp, Color(0xFFFFD54F).copy(alpha = winnerGlowPulse), RoundedCornerShape(12.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.EmojiEvents,
                            contentDescription = null,
                            tint = Color(0xFFFFD700),
                            modifier = Modifier.size(11.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "$trickWinnerName Won",
                            color = Color(0xFFFFE082),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayedCardItem(
    played: PlayedCard,
    isWinningCard: Boolean,
    winnerIndex: Int?,
    gatherProgress: () -> Float,
    winnerCollectProgress: () -> Float,
    overallAlpha: () -> Float,
    winnerGlowPulse: () -> Float
) {
    // Resting position on the table felt
    val (finalRestX, finalRestY, finalRestRot) = remember(played.playerIndex) {
        when (played.playerIndex) {
            0 -> Triple(0f, 32f, 0f)       // Bottom (local)
            1 -> Triple(-48f, 0f, 8f)     // Left
            2 -> Triple(0f, -32f, -2f)    // Top
            3 -> Triple(48f, 0f, -8f)     // Right
            else -> Triple(0f, 0f, 0f)
        }
    }

    // Origin position (where the card flew from: player's hand/seat)
    val (originX, originY, originRot) = remember(played.playerIndex) {
        when (played.playerIndex) {
            0 -> Triple(0f, 110f, 0f)
            1 -> Triple(-140f, 0f, 25f)
            2 -> Triple(0f, -110f, -15f)
            3 -> Triple(140f, 0f, -25f)
            else -> Triple(0f, 0f, 0f)
        }
    }

    val (winnerTargetX, winnerTargetY) = remember(winnerIndex) {
        when (winnerIndex) {
            0 -> Pair(0f, 110f)
            1 -> Pair(-140f, 0f)
            2 -> Pair(0f, -110f)
            3 -> Pair(140f, 0f)
            else -> Pair(0f, 0f)
        }
    }

    // Physical card entrance animation (approx 1.1–1.3s complete visual movement with natural easing)
    val flightProgress = remember { Animatable(0f) }
    val flightRotation = remember { Animatable(originRot) }
    val flightScale = remember { Animatable(1.08f) }

    LaunchedEffect(played.card.id) {
        flightProgress.animateTo(
            targetValue = 1f,
            animationSpec = MotionConstants.CardEntranceSpec
        )
        flightRotation.animateTo(
            targetValue = finalRestRot,
            animationSpec = MotionConstants.CardRotationSpec
        )
        flightScale.animateTo(
            targetValue = 1.0f,
            animationSpec = MotionConstants.CardEntranceSpec
        )
    }

    Box(
        modifier = Modifier.graphicsLayer {
            val fProg = flightProgress.value
            val fRot = flightRotation.value
            val fScale = flightScale.value
            val gProg = gatherProgress()
            val wProg = winnerCollectProgress()
            val oAlpha = overallAlpha()

            // Interpolate card position during flight entrance
            val currentFlewX = originX + (finalRestX - originX) * fProg
            val currentFlewY = originY + (finalRestY - originY) * fProg

            // If trick is completing:
            // Gather towards center (0, 0)
            val afterGatherX = currentFlewX * (1f - gProg)
            val afterGatherY = currentFlewY * (1f - gProg)

            val finalX = afterGatherX + (winnerTargetX - afterGatherX) * wProg
            val finalY = afterGatherY + (winnerTargetY - afterGatherY) * wProg

            val finalScale = if (wProg > 0f) {
                1f - (wProg * 0.25f)
            } else {
                fScale
            }

            translationX = finalX.dp.toPx()
            translationY = finalY.dp.toPx()
            rotationZ = fRot
            scaleX = finalScale
            scaleY = finalScale
            this.alpha = (oAlpha * fProg.coerceIn(0.6f, 1f)).coerceIn(0f, 1f)
        }
    ) {
        RealisticCard(
            card = played.card,
            width = 44.dp,
            elevation = if (isWinningCard) 8.dp else 5.dp,
            rotation = 0f,
            modifier = if (isWinningCard && winnerIndex != null) {
                Modifier.drawBehind {
                    val pulse = winnerGlowPulse()
                    drawRoundRect(
                        color = Color(0xFFFFD700).copy(alpha = pulse),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.2.dp.toPx()),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(44.dp.toPx() * 0.12f)
                    )
                }
            } else {
                Modifier
            }
        )
    }
}
