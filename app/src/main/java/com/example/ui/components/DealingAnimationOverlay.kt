package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.model.Player
import com.example.ui.theme.MotionConstants
import kotlinx.coroutines.delay
import kotlin.math.sin

enum class DealingAnimationStage {
    SHUFFLE_GATHER,        // 0.0s - 0.7s: Cards gather into squared deck
    SHUFFLE_SPLIT,         // 0.7s - 1.7s: Two packets slide apart with natural tilt
    SHUFFLE_RIFFLE,        // 1.7s - 3.0s: Multi-step interleaved riffle cascade
    SHUFFLE_BRIDGE,        // 3.0s - 3.9s: Waterfall arch flexes under tension & cascades
    SHUFFLE_SQUARE_SETTLE, // 3.9s - 4.3s: Squared deck settles with realistic cushion
    NATURAL_PAUSE,         // 4.3s - 5.0s: Natural short pause; complete deck rests at center
    DEALING_52_CARDS,      // 5.0s - 11.8s: True sequential 52-card distribution (P1->P2->P3->P4)
    HANDS_SETTLING,        // 11.8s - 12.5s: 52nd card settles; final hands fan and stabilize
    COMPLETE               // 12.5s: Done! Transition to Call Phase
}

/**
 * True Sequential 4-Player Card Dealing Animation.
 *
 * Sequence:
 *   Center Deck
 *   → Player 1 (Bottom / You)
 *   → Player 2 (Left / Bot 2)
 *   → Player 3 (Top / Bot 3)
 *   → Player 4 (Right / Bot 4)
 *   → repeat until all 52 cards are distributed one-by-one.
 *
 * Each card visibly departs the central deck, arcs across the table with natural
 * acceleration/deceleration, realistic rotation variation, and soft landing, while
 * the player's card stack / hand visually accumulates each received card.
 */
@Composable
fun DealingAnimationOverlay(
    dealerName: String,
    roundNumber: Int,
    players: List<Player>,
    onCardDealt: () -> Unit,
    onDealingComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var stage by remember { mutableStateOf(DealingAnimationStage.SHUFFLE_GATHER) }

    // Shuffle state animatables
    val leftPacketOffset = remember { Animatable(0f) }
    val rightPacketOffset = remember { Animatable(0f) }
    val leftPacketRotation = remember { Animatable(0f) }
    val rightPacketRotation = remember { Animatable(0f) }
    val bridgeArchY = remember { Animatable(0f) }
    val deckSettledScale = remember { Animatable(1f) }
    val riffleInterleave = remember { Animatable(0f) }

    // Single in-flight card flight properties (reused for maximum 60 FPS efficiency)
    val cardFlightProgress = remember { Animatable(0f) }
    var currentTargetSeat by remember { mutableIntStateOf(0) } // 0: Bottom, 1: Left, 2: Top, 3: Right
    var activeCardIndex by remember { mutableIntStateOf(0) }   // 0..51
    var flightBaseRotation by remember { mutableFloatStateOf(0f) }
    var flightFlickVariation by remember { mutableFloatStateOf(0f) }
    var isCardInFlight by remember { mutableStateOf(false) }

    // Cards accumulated at each seat (0: Bottom, 1: Left, 2: Top, 3: Right)
    val cardsReceivedBySeat = remember { mutableStateListOf(0, 0, 0, 0) }

    val infiniteTransition = rememberInfiniteTransition(label = "bannerPulseGlow")
    val bannerGlow by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bannerGlow"
    )

    LaunchedEffect(Unit) {
        // =========================================================================
        // 1. SHUFFLE: GATHER (700ms)
        // =========================================================================
        stage = DealingAnimationStage.SHUFFLE_GATHER
        deckSettledScale.animateTo(1.08f, tween(350, easing = FastOutSlowInEasing))
        deckSettledScale.animateTo(1.0f, tween(350, easing = FastOutSlowInEasing))

        // =========================================================================
        // 2. SHUFFLE: SPLIT TWO PACKETS (1000ms)
        // =========================================================================
        stage = DealingAnimationStage.SHUFFLE_SPLIT
        leftPacketOffset.animateTo(-42f, tween(450, easing = FastOutSlowInEasing))
        rightPacketOffset.animateTo(42f, tween(450, easing = FastOutSlowInEasing))
        leftPacketRotation.animateTo(-7.5f, tween(400))
        rightPacketRotation.animateTo(7.5f, tween(400))
        delay(150)

        // =========================================================================
        // 3. SHUFFLE: PHYSICAL RIFFLE INTERLEAVE (1300ms)
        // =========================================================================
        stage = DealingAnimationStage.SHUFFLE_RIFFLE
        for (step in 1..6) {
            val progress = step / 6f
            riffleInterleave.animateTo(progress, tween(85, easing = LinearEasing))
            leftPacketOffset.animateTo(-42f + (progress * 36f), tween(95, easing = LinearEasing))
            rightPacketOffset.animateTo(42f - (progress * 36f), tween(95, easing = LinearEasing))
            delay(30)
        }

        // =========================================================================
        // 4. SHUFFLE: WATERFALL BRIDGE CASCADE (900ms)
        // =========================================================================
        stage = DealingAnimationStage.SHUFFLE_BRIDGE
        bridgeArchY.animateTo(
            targetValue = -22f,
            animationSpec = tween(
                durationMillis = MotionConstants.DurationCardBridgeArchMs,
                easing = MotionConstants.CardShuffleBridgeEasing
            )
        )
        delay(100)
        bridgeArchY.animateTo(
            targetValue = 0f,
            animationSpec = tween(
                durationMillis = MotionConstants.DurationCardBridgeReleaseMs,
                easing = FastOutSlowInEasing
            )
        )
        leftPacketOffset.animateTo(0f, tween(280, easing = FastOutSlowInEasing))
        rightPacketOffset.animateTo(0f, tween(280, easing = FastOutSlowInEasing))
        leftPacketRotation.animateTo(0f, tween(280))
        rightPacketRotation.animateTo(0f, tween(280))

        // =========================================================================
        // 5. SHUFFLE: SQUARED DECK SETTLE (400ms)
        // =========================================================================
        stage = DealingAnimationStage.SHUFFLE_SQUARE_SETTLE
        deckSettledScale.animateTo(0.96f, tween(180, easing = FastOutSlowInEasing))
        deckSettledScale.animateTo(1.0f, tween(220, easing = FastOutSlowInEasing))

        // =========================================================================
        // 6. SHORT NATURAL PAUSE BEFORE DEAL (700ms)
        // Complete deck sits clearly visible at the table center
        // =========================================================================
        stage = DealingAnimationStage.NATURAL_PAUSE
        delay(700)

        // =========================================================================
        // 7. SEQUENTIAL 52-CARD DISTRIBUTION ANIMATION (~6.8 seconds)
        // Exactly ONE card in flight at a time.
        // Sequence: Deck -> P1 (Bottom) -> P2 (Left) -> P3 (Top) -> P4 (Right) -> repeat
        // =========================================================================
        stage = DealingAnimationStage.DEALING_52_CARDS

        val dealEasing = CubicBezierEasing(0.22f, 0.05f, 0.25f, 1.0f)

        for (cardIdx in 0 until 52) {
            val targetSeat = cardIdx % 4 // 0: Bottom, 1: Left, 2: Top, 3: Right
            currentTargetSeat = targetSeat
            activeCardIndex = cardIdx

            // Base rotation according to seat direction
            flightBaseRotation = when (targetSeat) {
                0 -> 0f      // Bottom: upright
                1 -> 90f     // Left: turned 90 deg
                2 -> 180f    // Top: inverted 180 deg
                3 -> 270f    // Right: turned 270 deg
                else -> 0f
            }

            // Subtle, deterministic dealer flick variation (+/- 4 degrees)
            flightFlickVariation = (((cardIdx * 13 + 7) % 9) - 4) * 1.1f

            // Card lifts from the center deck
            cardFlightProgress.snapTo(0f)
            isCardInFlight = true

            // Trigger card deal sound on launch
            onCardDealt()

            // Smooth physical flight trajectory outward from center deck to player position
            // Duration: 105ms flight with natural acceleration and deceleration
            cardFlightProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = 105,
                    easing = dealEasing
                )
            )

            // Card soft landing & settling into player's hand/stack
            cardsReceivedBySeat[targetSeat] = (cardIdx / 4) + 1
            isCardInFlight = false

            // Small gap between cards (25ms) before next card leaves the deck
            delay(25)
        }

        // =========================================================================
        // 8. 52nd CARD SETTLES & FINAL HANDS STABILIZE (700ms)
        // =========================================================================
        stage = DealingAnimationStage.HANDS_SETTLING
        delay(700)

        stage = DealingAnimationStage.COMPLETE
        onDealingComplete()
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f))
            .testTag("dealing_animation_overlay"),
        contentAlignment = Alignment.Center
    ) {
        val maxW = maxWidth.value
        val maxH = maxHeight.value

        // Target coordinates from center (0,0) matching actual responsive player areas
        val seatTargets = remember(maxW, maxH) {
            val bottomTargetY = (maxH * 0.36f).coerceIn(100f, 150f)
            val topTargetY = -(maxH * 0.36f).coerceIn(-150f, -100f)
            val leftTargetX = -(maxW * 0.38f).coerceIn(-280f, -150f)
            val rightTargetX = (maxW * 0.38f).coerceIn(150f, 280f)
            listOf(
                Pair(0f, bottomTargetY),     // Seat 0: Bottom (Player 1 / You)
                Pair(leftTargetX, 0f),      // Seat 1: Left (Player 2 / Bot 2)
                Pair(0f, topTargetY),       // Seat 2: Top (Player 3 / Bot 3)
                Pair(rightTargetX, 0f)      // Seat 3: Right (Player 4 / Bot 4)
            )
        }

        val bottomPlayer = players.getOrNull(0) ?: Player(1, 0, "You")
        val leftPlayer = players.getOrNull(1) ?: Player(2, 1, "Player 2")
        val topPlayer = players.getOrNull(2) ?: Player(3, 2, "Player 3")
        val rightPlayer = players.getOrNull(3) ?: Player(4, 3, "Player 4")

        // =========================================================================
        // 1. ALL FOUR PLAYER DESTINATIONS & STACKS
        // TOP: Player 3 / Bot 3
        // LEFT: Player 2 / Bot 2
        // RIGHT: Player 4 / Bot 4
        // BOTTOM: Player 1 / You
        // =========================================================================

        // TOP STATION (Seat 2: Player 3 / Bot 3)
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 8.dp)
        ) {
            DealingPlayerStationWithStack(
                player = topPlayer,
                seatLabel = "TOP",
                cardsCount = cardsReceivedBySeat[2],
                isTargeted = stage == DealingAnimationStage.DEALING_52_CARDS && currentTargetSeat == 2,
                orientation = StationOrientation.HORIZONTAL
            )
        }

        // LEFT STATION (Seat 1: Player 2 / Bot 2)
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 10.dp)
        ) {
            DealingPlayerStationWithStack(
                player = leftPlayer,
                seatLabel = "LEFT",
                cardsCount = cardsReceivedBySeat[1],
                isTargeted = stage == DealingAnimationStage.DEALING_52_CARDS && currentTargetSeat == 1,
                orientation = StationOrientation.VERTICAL
            )
        }

        // RIGHT STATION (Seat 3: Player 4 / Bot 4)
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 10.dp)
        ) {
            DealingPlayerStationWithStack(
                player = rightPlayer,
                seatLabel = "RIGHT",
                cardsCount = cardsReceivedBySeat[3],
                isTargeted = stage == DealingAnimationStage.DEALING_52_CARDS && currentTargetSeat == 3,
                orientation = StationOrientation.VERTICAL
            )
        }

        // BOTTOM STATION & GROWING HAND (Seat 0: Player 1 / You)
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                DealingPlayerStationWithStack(
                    player = bottomPlayer,
                    seatLabel = "BOTTOM",
                    cardsCount = cardsReceivedBySeat[0],
                    isTargeted = stage == DealingAnimationStage.DEALING_52_CARDS && currentTargetSeat == 0,
                    isLocalYou = true,
                    orientation = StationOrientation.HORIZONTAL
                )

                // Accumulating hand for local player (cards fan out as they arrive)
                if (cardsReceivedBySeat[0] > 0) {
                    AccumulatingHandView(
                        count = cardsReceivedBySeat[0],
                        maxCards = 13
                    )
                }
            }
        }

        // =========================================================================
        // 2. CENTER TABLE FELT: REALISTIC COMPLETE DECK (Shrinks as dealt)
        // =========================================================================
        Box(
            modifier = Modifier.graphicsLayer {
                val s = deckSettledScale.value
                scaleX = s
                scaleY = s
            },
            contentAlignment = Alignment.Center
        ) {
            when (stage) {
                DealingAnimationStage.SHUFFLE_SPLIT -> {
                    // Left split packet
                    Box(
                        modifier = Modifier.graphicsLayer {
                            translationX = leftPacketOffset.value.dp.toPx()
                            rotationZ = leftPacketRotation.value
                        }
                    ) {
                        RealisticCard(card = null, width = 44.dp, isFaceUp = false, elevation = 6.dp)
                    }
                    // Right split packet
                    Box(
                        modifier = Modifier.graphicsLayer {
                            translationX = rightPacketOffset.value.dp.toPx()
                            rotationZ = rightPacketRotation.value
                        }
                    ) {
                        RealisticCard(card = null, width = 44.dp, isFaceUp = false, elevation = 6.dp)
                    }
                }

                DealingAnimationStage.SHUFFLE_RIFFLE -> {
                    val interleaveFrac = riffleInterleave.value
                    for (step in 0..4) {
                        val sideSign = if (step % 2 == 0) -1f else 1f
                        val offsetDist = (34f * (1f - interleaveFrac)) * sideSign
                        val rot = (sideSign * 4f) * (1f - interleaveFrac)
                        Box(
                            modifier = Modifier.graphicsLayer {
                                translationX = offsetDist.dp.toPx()
                                translationY = ((step - 2) * 1.5f).dp.toPx()
                                rotationZ = rot
                            }
                        ) {
                            RealisticCard(card = null, width = 44.dp, isFaceUp = false, elevation = (4 + step).dp)
                        }
                    }
                }

                DealingAnimationStage.SHUFFLE_BRIDGE -> {
                    Box(
                        modifier = Modifier.graphicsLayer {
                            translationY = bridgeArchY.value.dp.toPx()
                        }
                    ) {
                        RealisticCard(card = null, width = 44.dp, isFaceUp = false, elevation = 12.dp)
                    }
                    Box(
                        modifier = Modifier.graphicsLayer {
                            translationX = (-12).dp.toPx()
                            translationY = (bridgeArchY.value * 0.55f).dp.toPx()
                            rotationZ = -9f
                        }
                    ) {
                        RealisticCard(card = null, width = 44.dp, isFaceUp = false, elevation = 8.dp)
                    }
                    Box(
                        modifier = Modifier.graphicsLayer {
                            translationX = 12.dp.toPx()
                            translationY = (bridgeArchY.value * 0.55f).dp.toPx()
                            rotationZ = 9f
                        }
                    ) {
                        RealisticCard(card = null, width = 44.dp, isFaceUp = false, elevation = 8.dp)
                    }
                }

                else -> {
                    // Complete central deck stack: visibly diminishes from 52 down to 0
                    val remainingInDeck = if (stage == DealingAnimationStage.DEALING_52_CARDS) {
                        (52 - activeCardIndex).coerceIn(0, 52)
                    } else if (stage == DealingAnimationStage.HANDS_SETTLING || stage == DealingAnimationStage.COMPLETE) {
                        0
                    } else 52

                    if (remainingInDeck > 0) {
                        val visualThicknessLayers = (remainingInDeck / 13).coerceIn(1, 4)
                        for (i in visualThicknessLayers downTo 1) {
                            RealisticCard(
                                card = null,
                                width = 44.dp,
                                isFaceUp = false,
                                elevation = (i * 2).dp,
                                modifier = Modifier.graphicsLayer {
                                    translationX = (i * 0.9).dp.toPx()
                                    translationY = (i * 0.9).dp.toPx()
                                }
                            )
                        }
                        RealisticCard(
                            card = null,
                            width = 44.dp,
                            isFaceUp = false,
                            elevation = 8.dp
                        )
                    }
                }
            }

            // =========================================================================
            // 3. EXACTLY ONE IN-FLIGHT CARD (Active dealing flight)
            // Visibly travels from central deck to the specific destination player
            // =========================================================================
            if (stage == DealingAnimationStage.DEALING_52_CARDS && isCardInFlight) {
                val progress = cardFlightProgress.value
                val target = seatTargets.getOrElse(currentTargetSeat) { Pair(0f, 0f) }

                // Parabolic arc: card lifts upward from felt during flight
                val arcLift = sin(progress * Math.PI.toFloat()) * -16f

                RealisticCard(
                    card = null,
                    width = 44.dp,
                    isFaceUp = false,
                    elevation = 14.dp,
                    modifier = Modifier
                        .zIndex(50f)
                        .graphicsLayer {
                            if (progress <= 0f) {
                                alpha = 0f
                            } else {
                                translationX = (target.first * progress).dp.toPx()
                                translationY = ((target.second * progress) + arcLift).dp.toPx()
                                rotationZ = (flightBaseRotation + flightFlickVariation) * progress
                                val s = 1.05f - (progress * 0.12f)
                                scaleX = s
                                scaleY = s
                                alpha = (1f - (progress * 0.15f)).coerceIn(0.85f, 1f)
                            }
                        }
                )
            }
        }

        // =========================================================================
        // 4. DEALING STATUS HUD PILL (Positioned safely above bottom player)
        // =========================================================================
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 74.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xF20A2214), Color(0xFA05120B))
                    ),
                    RoundedCornerShape(16.dp)
                )
                .border(
                    0.8.dp,
                    Color(0xFFD4AF37).copy(alpha = bannerGlow),
                    RoundedCornerShape(16.dp)
                )
                .padding(horizontal = 14.dp, vertical = 3.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val statusText = when (stage) {
                DealingAnimationStage.SHUFFLE_GATHER,
                DealingAnimationStage.SHUFFLE_SPLIT,
                DealingAnimationStage.SHUFFLE_RIFFLE,
                DealingAnimationStage.SHUFFLE_BRIDGE,
                DealingAnimationStage.SHUFFLE_SQUARE_SETTLE -> "Physical Riffle Shuffle in Progress..."
                DealingAnimationStage.NATURAL_PAUSE -> "Deck Squared on Felt • Ready to Deal"
                DealingAnimationStage.DEALING_52_CARDS -> "Dealing Card ${activeCardIndex + 1}/52 → ${
                    when (currentTargetSeat) {
                        0 -> "You (Bottom)"
                        1 -> "${leftPlayer.name} (Left)"
                        2 -> "${topPlayer.name} (Top)"
                        else -> "${rightPlayer.name} (Right)"
                    }
                }"
                DealingAnimationStage.HANDS_SETTLING -> "Final Hands Settling & Fanning..."
                DealingAnimationStage.COMPLETE -> "All 52 Cards Distributed • Hand Ready"
            }

            Text(
                text = "ROUND $roundNumber • $dealerName",
                color = Color(0xFFFFD54F),
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.5.sp
            )
            Text(
                text = statusText,
                color = Color(0xFFE2E8F0),
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

private enum class StationOrientation {
    HORIZONTAL,
    VERTICAL
}

/**
 * Player station widget that displays identity and a physical card stack
 * accumulating dealt card backs.
 */
@Composable
private fun DealingPlayerStationWithStack(
    player: Player,
    seatLabel: String,
    cardsCount: Int,
    isTargeted: Boolean,
    orientation: StationOrientation,
    isLocalYou: Boolean = false,
    modifier: Modifier = Modifier
) {
    val borderColor = if (isTargeted) Color(0xFFFFD54F) else Color(0xFF234431).copy(alpha = 0.6f)
    val backgroundBrush = if (isTargeted) {
        Brush.verticalGradient(listOf(Color(0xF2183824), Color(0xF80E2317)))
    } else {
        Brush.verticalGradient(listOf(Color(0xD80D1D14), Color(0xE608140E)))
    }

    Box(
        modifier = modifier
            .widthIn(min = 104.dp, max = 138.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundBrush)
            .border(
                width = if (isTargeted) 1.2.dp else 0.8.dp,
                color = borderColor,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 6.dp, vertical = 3.dp)
            .testTag("dealing_station_${player.id}")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            // Avatar
            Box(
                modifier = Modifier
                    .size(15.dp)
                    .clip(CircleShape)
                    .background(
                        if (player.isBot) Color(0xFF3B82F6).copy(alpha = 0.3f)
                        else Color(0xFFD4AF37).copy(alpha = 0.25f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (player.isBot) Icons.Filled.SmartToy else Icons.Filled.Person,
                    contentDescription = null,
                    tint = if (player.isBot) Color(0xFF93C5FD) else Color(0xFFFFE082),
                    modifier = Modifier.size(9.5.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (isLocalYou) "You" else player.name,
                        fontSize = 9.5.sp,
                        fontWeight = if (isTargeted) FontWeight.ExtraBold else FontWeight.Bold,
                        color = if (isTargeted) Color(0xFFFFF3D0) else Color(0xFFE2E8F0),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = seatLabel,
                        fontSize = 7.sp,
                        color = Color(0xFF94A3B8),
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Dealt card count badge and visual miniature stacked indicators
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = "CARDS:",
                        fontSize = 7.sp,
                        color = Color(0xFF94A3B8),
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "$cardsCount/13",
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (cardsCount == 13) Color(0xFF4ADE80) else Color(0xFFFFD54F)
                    )

                    // Stack depth pips
                    if (cardsCount > 0) {
                        Spacer(modifier = Modifier.width(2.dp))
                        Box(
                            modifier = Modifier
                                .height(5.dp)
                                .width((cardsCount * 2.2).coerceIn(4.0, 30.0).dp)
                                .clip(RoundedCornerShape(1.dp))
                                .background(Color(0xFFD4AF37).copy(alpha = 0.7f))
                        )
                    }
                }
            }
        }
    }
}

/**
 * Lightweight fanned card preview that visibly builds card by card
 * as Player 1 (Bottom / You) receives cards from the dealer.
 */
@Composable
private fun AccumulatingHandView(
    count: Int,
    maxCards: Int = 13,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(28.dp)
            .widthIn(min = 120.dp, max = 220.dp),
        contentAlignment = Alignment.Center
    ) {
        val step = 11.dp
        val startOffset = -(step * (count - 1) / 2)

        for (i in 0 until count) {
            val rot = (i - (count - 1) / 2f) * 2.2f
            Box(
                modifier = Modifier
                    .offset(x = startOffset + (step * i))
                    .graphicsLayer {
                        rotationZ = rot
                    }
                    .size(width = 18.dp, height = 26.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color(0xFF0F3622))
                    .border(0.6.dp, Color(0xFFD4AF37), RoundedCornerShape(3.dp))
            )
        }
    }
}
