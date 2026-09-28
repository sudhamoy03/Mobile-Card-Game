package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun TurnTimerIndicator(
    secondsRemaining: Int,
    isLocalPlayerTurn: Boolean,
    activePlayerName: String,
    modifier: Modifier = Modifier
) {
    val progress = (secondsRemaining / 30f).coerceIn(0f, 1f)
    val timerColor by animateColorAsState(
        targetValue = when {
            secondsRemaining <= 5 -> Color(0xFFEF4444)
            secondsRemaining <= 10 -> Color(0xFFF59E0B)
            else -> MaterialTheme.colorScheme.primary
        },
        label = "timerColor"
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF0F1B14).copy(alpha = 0.85f))
            .border(1.dp, timerColor.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .testTag("turn_timer_indicator"),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(26.dp)
            ) {
                CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.size(26.dp),
                    color = timerColor,
                    strokeWidth = 2.5.dp,
                    trackColor = Color(0x33FFFFFF)
                )
                Text(
                    text = "$secondsRemaining",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = timerColor
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            Column {
                Text(
                    text = if (isLocalPlayerTurn) "YOUR TURN!" else "$activePlayerName's Turn",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isLocalPlayerTurn) timerColor else Color.White
                )
            }
        }
    }
}
