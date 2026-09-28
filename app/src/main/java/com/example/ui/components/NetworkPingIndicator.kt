package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A subtle, compact network ping indicator designed for multiplayer card game tables.
 * Displays connection latency with color-coded quality states:
 * - Green (< 100ms): Excellent connection
 * - Amber (100 - 249ms): Moderate connection
 * - Red (250ms+): High latency
 */
@Composable
fun NetworkPingIndicator(
    pingMs: Int?,
    modifier: Modifier = Modifier
) {
    val (statusColor, pingText) = when {
        pingMs == null -> Pair(Color(0xFF94A3B8), "...")
        pingMs < 100 -> Pair(Color(0xFF10B981), "$pingMs ms")
        pingMs < 250 -> Pair(Color(0xFFF59E0B), "$pingMs ms")
        else -> Pair(Color(0xFFEF4444), "$pingMs ms")
    }

    val animatedColor by animateColorAsState(
        targetValue = statusColor,
        animationSpec = tween(durationMillis = 300),
        label = "ping_color"
    )

    Box(
        modifier = modifier
            .testTag("network_ping_indicator")
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.45f))
            .border(
                width = 0.8.dp,
                color = animatedColor.copy(alpha = 0.4f),
                shape = RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 6.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            // Signal Dot
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(animatedColor)
            )

            Spacer(modifier = Modifier.width(5.dp))

            // Latency text
            Text(
                text = pingText,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = animatedColor.copy(alpha = 0.95f),
                letterSpacing = 0.2.sp
            )
        }
    }
}
