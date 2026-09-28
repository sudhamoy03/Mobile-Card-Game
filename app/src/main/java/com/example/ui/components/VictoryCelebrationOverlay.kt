package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.AuthoritativeRuleEngine
import com.example.model.Player
import kotlin.random.Random

/**
 * A refined, restrained celebration overlay that highlights the winner's name
 * without blocking the game table.
 *
 * Designed with:
 * - A gentle, translucent scrim so the table felt, cards, and players remain clearly visible.
 * - Gentle ambient golden stardust particles drifting gracefully in the background.
 * - A delicate, compact glassmorphic victory banner highlighting the winner's name.
 * - Toggleable visibility so players can view the table completely unobstructed.
 */
@Composable
fun VictoryCelebrationOverlay(
    winnerName: String,
    winnerScore: Int,
    targetScore: Int,
    players: List<Player>,
    prizeEnabled: Boolean,
    prizeAmount: Int,
    onRematchClick: () -> Unit,
    onHomeClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isBannerMinimized by remember { mutableStateOf(false) }

    // Smooth entry animatable
    val contentScale = remember { Animatable(0.92f) }
    val contentAlpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        contentAlpha.animateTo(1f, tween(600, easing = FastOutSlowInEasing))
        contentScale.animateTo(1f, tween(600, easing = FastOutSlowInEasing))
    }

    // Gentle breathing halo pulse
    val infiniteTransition = rememberInfiniteTransition(label = "victoryAura")
    val auraGlow by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 0.90f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "auraGlow"
    )

    // Gentle floating stardust animation progress
    val stardustProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "stardustProgress"
    )

    // Pre-computed restrained particle coordinates for zero-allocation rendering
    val particles = remember {
        val rand = Random(42)
        List(28) {
            ParticleData(
                xNorm = rand.nextFloat(),
                yInit = rand.nextFloat(),
                speed = 0.08f + (rand.nextFloat() * 0.12f),
                radiusDp = 1.2f + (rand.nextFloat() * 1.8f),
                baseAlpha = 0.25f + (rand.nextFloat() * 0.45f),
                color = when (rand.nextInt(3)) {
                    0 -> Color(0xFFFFD54F)
                    1 -> Color(0xFFFBBF24)
                    else -> Color(0xFFFFFBEB)
                }
            )
        }
    }

    val rankings = remember(players) {
        AuthoritativeRuleEngine.calculateRankings(players)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            // Gentle translucent veil - lets felt and table show through clearly
            .background(Color.Black.copy(alpha = 0.22f))
            .testTag("victory_celebration_overlay"),
        contentAlignment = Alignment.Center
    ) {
        // Refined, subtle golden stardust particles drifting in the ambient air
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            if (width > 0 && height > 0) {
                particles.forEach { p ->
                    val yProgress = (p.yInit - (stardustProgress * p.speed)) % 1f
                    val yNorm = if (yProgress < 0f) yProgress + 1f else yProgress
                    val px = p.xNorm * width
                    val py = yNorm * height

                    // Subtle pulsating alpha
                    val particleAlpha = (p.baseAlpha * auraGlow).coerceIn(0.1f, 0.75f)
                    drawCircle(
                        color = p.color.copy(alpha = particleAlpha),
                        radius = p.radiusDp.dp.toPx(),
                        center = Offset(px, py)
                    )
                }
            }
        }

        // Floating Toggle Button in Top Corner: Allows completely peeking at the table
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 10.dp, end = 12.dp)
        ) {
            Surface(
                onClick = { isBannerMinimized = !isBannerMinimized },
                shape = RoundedCornerShape(20.dp),
                color = Color.Black.copy(alpha = 0.65f),
                border = BorderStroke(0.8.dp, Color(0xFFD4AF37).copy(alpha = 0.6f)),
                modifier = Modifier.testTag("toggle_celebration_banner_btn")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = if (isBannerMinimized) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                        contentDescription = if (isBannerMinimized) "Show Banner" else "View Table",
                        tint = Color(0xFFFFD54F),
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = if (isBannerMinimized) "SHOW VICTORY" else "VIEW TABLE",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFFF3D0)
                    )
                }
            }
        }

        // =========================================================================
        // REFINED RESTRAINED CELEBRATION CARD
        // Compact, non-blocking center card that highlights the winner's achievement
        // =========================================================================
        AnimatedVisibility(
            visible = !isBannerMinimized,
            enter = fadeIn(tween(350)) + scaleIn(tween(350, easing = FastOutSlowInEasing), initialScale = 0.94f),
            exit = fadeOut(tween(250)) + scaleOut(tween(250), targetScale = 0.94f)
        ) {
            Surface(
                modifier = Modifier
                    .widthIn(min = 340.dp, max = 460.dp)
                    .graphicsLayer {
                        scaleX = contentScale.value
                        scaleY = contentScale.value
                        alpha = contentAlpha.value
                    }
                    .shadow(16.dp, RoundedCornerShape(16.dp))
                    .clip(RoundedCornerShape(16.dp))
                    .border(
                        BorderStroke(
                            1.2.dp,
                            Brush.linearGradient(
                                listOf(
                                    Color(0xFFD4AF37).copy(alpha = auraGlow),
                                    Color(0xFFFBBF24).copy(alpha = 0.7f),
                                    Color(0xFFD4AF37).copy(alpha = auraGlow)
                                )
                            )
                        ),
                        RoundedCornerShape(16.dp)
                    )
                    .testTag("victory_banner_card"),
                shape = RoundedCornerShape(16.dp),
                color = Color.Transparent
            ) {
                Box(
                    modifier = Modifier
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color(0xF00A2315),
                                    Color(0xF805140C)
                                )
                            )
                        )
                        .padding(horizontal = 18.dp, vertical = 12.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Header with Trophy icon & Victory badge
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.EmojiEvents,
                                contentDescription = "Trophy",
                                tint = Color(0xFFFFD54F),
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "MATCH VICTORY",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = FontFamily.Serif,
                                color = Color(0xFFFFD54F),
                                letterSpacing = 2.sp
                            )
                            Icon(
                                imageVector = Icons.Filled.EmojiEvents,
                                contentDescription = "Trophy",
                                tint = Color(0xFFFFD54F),
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Prominent Winner Name with gold glow
                        Text(
                            text = winnerName,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFFFFF7ED),
                            letterSpacing = 0.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        // Score & Target achievement
                        Text(
                            text = "Target of $targetScore Reached • $winnerScore Points",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF93C5FD)
                        )

                        // Optional Prize badge
                        if (prizeEnabled) {
                            Box(
                                modifier = Modifier
                                    .background(
                                        Color(0xFFB45309).copy(alpha = 0.25f),
                                        RoundedCornerShape(8.dp)
                                    )
                                    .border(0.8.dp, Color(0xFFF59E0B).copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 10.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "PRIZE: ₹$prizeAmount",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFFFFD700)
                                )
                            }
                        }

                        // Compact Final Standings summary (1st to 4th)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.Black.copy(alpha = 0.40f), RoundedCornerShape(8.dp))
                                .border(0.6.dp, Color(0xFF234431), RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            rankings.forEach { (player, rank) ->
                                val isTopWinner = rank == 1
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "#$rank",
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (isTopWinner) Color(0xFFFFD54F) else Color(0xFF94A3B8)
                                    )
                                    Text(
                                        text = player.name,
                                        fontSize = 9.sp,
                                        fontWeight = if (isTopWinner) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isTopWinner) Color(0xFFFFF3D0) else Color(0xFFCBD5E1),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${player.totalScore} pts",
                                        fontSize = 8.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isTopWinner) Color(0xFF4ADE80) else Color(0xFF94A3B8)
                                    )
                                }
                            }
                        }

                        // Compact Action Buttons
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = onHomeClick,
                                modifier = Modifier
                                    .height(34.dp)
                                    .testTag("celebration_home_button"),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, Color(0xFF64748B)),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = Color(0xFFE2E8F0)
                                )
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Home,
                                        contentDescription = "Home",
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text(
                                        text = "HOME",
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Button(
                                onClick = onRematchClick,
                                modifier = Modifier
                                    .height(34.dp)
                                    .testTag("celebration_rematch_button"),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Replay,
                                        contentDescription = "Rematch",
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text(
                                        text = "REMATCH",
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class ParticleData(
    val xNorm: Float,
    val yInit: Float,
    val speed: Float,
    val radiusDp: Float,
    val baseAlpha: Float,
    val color: Color
)
