package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.runtime.key
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.model.Card
import com.example.ui.theme.MotionConstants
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun InteractiveHand(
    cards: List<Card>,
    legalCards: List<Card>,
    selectedCard: Card?,
    isTurnActive: Boolean,
    onSelectCard: (Card) -> Unit,
    onPlayCard: (Card) -> Unit,
    modifier: Modifier = Modifier,
    cardWidth: Dp = 46.dp,
    onDragStateChange: ((isDragging: Boolean, isOverDropZone: Boolean) -> Unit)? = null,
    onInvalidAction: (() -> Unit)? = null
) {
    val coroutineScope = rememberCoroutineScope()
    var draggingCardId by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(88.dp)
            .testTag("interactive_hand_container"),
        contentAlignment = Alignment.BottomCenter
    ) {
        val cardCount = cards.size
        // Dynamically compute card overlap step based on card count
        val step = if (cardCount > 1) {
            val totalAvailableWidth = 440.dp
            val calculatedStep = (totalAvailableWidth - cardWidth) / (cardCount - 1)
            calculatedStep.coerceIn(16.dp, 32.dp)
        } else {
            0.dp
        }

        Box(
            modifier = Modifier.padding(bottom = 2.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            val totalFanWidth = if (cardCount > 1) (step * (cardCount - 1)) + cardWidth else cardWidth
            val startOffset = -(totalFanWidth / 2) + (cardWidth / 2)

            cards.forEachIndexed { index, card ->
                key(card.id) {
                    val isSelected = selectedCard?.id == card.id
                    val isPlayable = isTurnActive && legalCards.any { it.id == card.id }
                    val isDragging = draggingCardId == card.id
                    val xPos = startOffset + (step * index)

                    // Fan rotation curvature
                    val naturalRotation = if (cardCount > 3) {
                        val normalizedIndex = (index - (cardCount - 1) / 2f) / ((cardCount - 1) / 2f)
                        normalizedIndex * 9f // -9 deg to +9 deg
                    } else 0f

                    DraggableCardItem(
                        card = card,
                        cardWidth = cardWidth,
                        xPos = xPos,
                        naturalRotation = naturalRotation,
                        index = index,
                        isSelected = isSelected,
                        isPlayable = if (isTurnActive) isPlayable else true,
                        isTurnActive = isTurnActive,
                        isDragging = isDragging,
                        onDragStart = {
                            draggingCardId = card.id
                            onDragStateChange?.invoke(true, false)
                        },
                        onDragDelta = { offset ->
                            val isOverDrop = offset.y < -55f
                            onDragStateChange?.invoke(true, isOverDrop)
                        },
                        onDragEnd = { finalOffset ->
                            draggingCardId = null
                            onDragStateChange?.invoke(false, false)
                            val isOverDrop = finalOffset.y < -55f
                            if (isOverDrop && isTurnActive) {
                                if (isPlayable) {
                                    onPlayCard(card)
                                } else {
                                    onInvalidAction?.invoke()
                                }
                            }
                        },
                        onClick = {
                            if (isSelected && isTurnActive) {
                                onPlayCard(card)
                            } else {
                                onSelectCard(card)
                            }
                        }
                    )
                }
            }
        }

        // Quick "Play Card" confirmation button when a card is selected
        if (selectedCard != null && isTurnActive) {
            Button(
                onClick = { onPlayCard(selectedCard) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-16).dp, y = (-10).dp)
                    .height(34.dp)
                    .testTag("play_selected_card_button")
            ) {
                Text(
                    text = "PLAY ${selectedCard.rank.shortName}${selectedCard.suit.symbol}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun DraggableCardItem(
    card: Card,
    cardWidth: Dp,
    xPos: Dp,
    naturalRotation: Float,
    index: Int,
    isSelected: Boolean,
    isPlayable: Boolean,
    isTurnActive: Boolean,
    isDragging: Boolean,
    onDragStart: () -> Unit,
    onDragDelta: (Offset) -> Unit,
    onDragEnd: (Offset) -> Unit,
    onClick: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val dragOffset = remember { Animatable(Offset.Zero, Offset.VectorConverter) }

    val elevation by animateDpAsState(
        targetValue = when {
            isDragging -> 14.dp
            isSelected -> 8.dp
            else -> 4.dp
        },
        animationSpec = MotionConstants.CardElevationDpSpec,
        label = "itemElevation"
    )

    val scale by animateFloatAsState(
        targetValue = if (isDragging) 1.05f else if (isSelected) 1.02f else 1.0f,
        animationSpec = MotionConstants.HandScaleSpringSpec,
        label = "itemScale"
    )

    Box(
        modifier = Modifier
            .offset(x = xPos)
            .zIndex(if (isDragging) 1000f else if (isSelected) 100f else index.toFloat())
            .graphicsLayer {
                val offset = dragOffset.value
                translationX = offset.x
                translationY = offset.y
                val tilt = if (isDragging) {
                    naturalRotation + (offset.x * 0.10f).coerceIn(-24f, 24f)
                } else {
                    naturalRotation
                }
                rotationZ = tilt
                scaleX = scale
                scaleY = scale
            }
            .pointerInput(card.id, isTurnActive, isPlayable) {
                detectDragGestures(
                    onDragStart = {
                        onDragStart()
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        coroutineScope.launch {
                            val newX = dragOffset.value.x + dragAmount.x
                            val newY = dragOffset.value.y + dragAmount.y
                            dragOffset.snapTo(Offset(newX, newY))
                            onDragDelta(Offset(newX, newY))
                        }
                    },
                    onDragEnd = {
                        val finalOffset = dragOffset.value
                        onDragEnd(finalOffset)
                        coroutineScope.launch {
                            // Realistic smooth snap-back to hand
                            dragOffset.animateTo(
                                targetValue = Offset.Zero,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessMediumLow
                                )
                            )
                        }
                    },
                    onDragCancel = {
                        onDragEnd(dragOffset.value)
                        coroutineScope.launch {
                            dragOffset.animateTo(
                                targetValue = Offset.Zero,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessMediumLow
                                )
                            )
                        }
                    }
                )
            }
    ) {
        RealisticCard(
            card = card,
            width = cardWidth,
            isSelected = isSelected,
            isPlayable = if (isTurnActive) isPlayable else true,
            elevation = elevation,
            rotation = 0f,
            onClick = onClick
        )
    }
}
