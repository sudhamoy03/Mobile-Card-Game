package com.example.ui.components

import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ConnectionStatus
import com.example.model.Player

private val ActiveTurnBackgroundBrush = Brush.verticalGradient(
    colors = listOf(
        Color(0xE8142A1E),
        Color(0xF20B1C13)
    )
)

private val InactiveBackgroundBrush = Brush.verticalGradient(
    colors = listOf(
        Color(0xD80D1D14),
        Color(0xE608140E)
    )
)

@Composable
fun PlayerPanel(
    player: Player,
    isCurrentTurn: Boolean,
    isLocalPlayer: Boolean,
    modifier: Modifier = Modifier,
    remainingSeconds: Int? = null,
    isWinner: Boolean = false
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.55f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    val baseBorderColor by animateColorAsState(
        targetValue = when {
            isWinner -> Color(0xFFFFD54F)
            isCurrentTurn -> Color(0xFFD4AF37)
            player.connectionStatus == ConnectionStatus.DISCONNECTED -> Color(0xFFEF4444).copy(alpha = 0.7f)
            else -> Color(0xFF234431).copy(alpha = 0.5f)
        },
        animationSpec = tween(400),
        label = "panelBorder"
    )

    val displayName = if (isLocalPlayer) "You" else player.name

    val animatedScore by animateIntAsState(
        targetValue = player.totalScore,
        animationSpec = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
        label = "animatedScore"
    )

    Surface(
        modifier = modifier
            .widthIn(min = 104.dp, max = 132.dp)
            .testTag("player_panel_${player.id}")
            .clip(RoundedCornerShape(8.dp))
            .drawBehind {
                val strokeWidth = if (isWinner || isCurrentTurn) 1.2.dp.toPx() else 0.8.dp.toPx()
                val color = if (isWinner || isCurrentTurn) baseBorderColor.copy(alpha = pulseAlpha) else baseBorderColor
                drawRoundRect(
                    color = color,
                    style = Stroke(width = strokeWidth),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx())
                )
            },
        shape = RoundedCornerShape(8.dp),
        color = Color.Transparent,
        shadowElevation = if (isWinner || isCurrentTurn) 4.dp else 1.dp
    ) {
        Box(
            modifier = Modifier
                .background(if (isWinner || isCurrentTurn) ActiveTurnBackgroundBrush else InactiveBackgroundBrush)
                .padding(horizontal = 6.dp, vertical = 3.dp)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(1.5.dp)
            ) {
                // Row 1: Avatar, Name, Status Badge (Compact HUD)
                Row(
                    modifier = Modifier.widthIn(max = 120.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.5.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(13.dp)
                            .clip(CircleShape)
                            .background(
                                if (player.isBot) Color(0xFF3B82F6).copy(alpha = 0.25f)
                                else Color(0xFFD4AF37).copy(alpha = 0.22f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (player.isBot) Icons.Filled.SmartToy else Icons.Filled.Person,
                            contentDescription = if (player.isBot) "Bot player" else "Human player",
                            tint = if (player.isBot) Color(0xFF93C5FD) else Color(0xFFFFE082),
                            modifier = Modifier.size(9.dp)
                        )
                    }

                    Text(
                        text = displayName,
                        fontSize = 10.sp,
                        fontWeight = if (isCurrentTurn) FontWeight.ExtraBold else FontWeight.Bold,
                        color = if (isCurrentTurn) Color(0xFFFFF3D0) else Color(0xFFE2E8F0),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    // Compact Status Badge
                    when {
                        isWinner -> {
                            Text(
                                text = "WINNER 🏆",
                                color = Color(0xFFFFD700),
                                fontSize = 6.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier
                                    .background(Color(0xFF78350F).copy(alpha = 0.65f), RoundedCornerShape(2.dp))
                                    .padding(horizontal = 2.5.dp, vertical = 0.5.dp)
                            )
                        }
                        player.connectionStatus == ConnectionStatus.DISCONNECTED ||
                        player.connectionStatus == ConnectionStatus.RECONNECTING -> {
                            Text(
                                text = "DISC ${player.disconnectRemainingSeconds ?: remainingSeconds ?: 30}s",
                                color = Color(0xFFFCA5A5),
                                fontSize = 6.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier
                                    .background(Color(0xFF7F1D1D).copy(alpha = 0.45f), RoundedCornerShape(2.dp))
                                    .padding(horizontal = 2.dp, vertical = 0.5.dp)
                            )
                        }
                        player.connectionStatus == ConnectionStatus.BOT_ACTIVE -> {
                            Text(
                                text = "BOT ACT",
                                color = Color(0xFFFDE68A),
                                fontSize = 6.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier
                                    .background(Color(0xFF78350F).copy(alpha = 0.45f), RoundedCornerShape(2.dp))
                                    .padding(horizontal = 2.dp, vertical = 0.5.dp)
                            )
                        }
                        player.isBot -> {
                            Text(
                                text = "BOT",
                                color = Color(0xFF93C5FD),
                                fontSize = 6.5.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .background(Color(0xFF1E3A8A).copy(alpha = 0.45f), RoundedCornerShape(2.dp))
                                    .padding(horizontal = 2.5.dp, vertical = 0.5.dp)
                            )
                        }
                    }
                }

                // Row 2: Compact Stats HUD (CALL, WON, SCORE, CARDS)
                Row(
                    modifier = Modifier.widthIn(max = 120.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    StatPill(
                        label = "C",
                        value = player.call?.let { if (it == 8) "8★" else it.toString() } ?: "—",
                        isHighlighted = player.call != null,
                        highlightColor = Color(0xFFFFD54F)
                    )

                    StatPill(
                        label = "W",
                        value = "${player.tricksWon}",
                        isHighlighted = player.call != null && player.tricksWon >= player.call,
                        highlightColor = Color(0xFF4ADE80)
                    )

                    StatPill(
                        label = "S",
                        value = "$animatedScore",
                        isHighlighted = false
                    )

                    StatPill(
                        label = "R",
                        value = "${player.remainingCardsCount}",
                        isHighlighted = false
                    )
                }
            }
        }
    }
}

@Composable
private fun StatPill(
    label: String,
    value: String,
    isHighlighted: Boolean,
    highlightColor: Color = Color(0xFF6EE7B7)
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        Text(
            text = label,
            fontSize = 7.sp,
            color = Color(0xFF94A3B8),
            fontWeight = FontWeight.Medium
        )
        Text(
            text = value,
            fontSize = 8.5.sp,
            color = if (isHighlighted) highlightColor else Color(0xFFF1F5F9),
            fontWeight = FontWeight.Bold
        )
    }
}
