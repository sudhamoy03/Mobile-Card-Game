package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Non-blocking in-layout Call Phase Control Bar.
 * Placed in the dedicated safe area ABOVE the 13-card hand.
 * Never covers the player's cards, allowing full inspection of suits and ranks before calling.
 *
 * Scoring Rule: Call 8 = BUMPER (+160 on success, -80 on failure).
 */
@Composable
fun CallDialog(
    playerName: String,
    currentCall: Int?,
    hasModifiedCall: Boolean,
    totalCallsSoFar: Int,
    callPrompt: String?,
    onSelectCall: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    // Current draft selection (defaults to currentCall or 2)
    var selectedNumber by remember(currentCall) {
        mutableIntStateOf(currentCall ?: 2)
    }

    Surface(
        modifier = modifier
            .widthIn(min = 360.dp, max = 560.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(
                BorderStroke(1.2.dp, Color(0xFFD4AF37).copy(alpha = 0.85f)),
                RoundedCornerShape(12.dp)
            )
            .shadow(8.dp, RoundedCornerShape(12.dp))
            .testTag("call_selection_dialog"),
        shape = RoundedCornerShape(12.dp),
        color = Color.Transparent
    ) {
        Box(
            modifier = Modifier
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xF20B2215),
                            Color(0xFA05130B)
                        )
                    )
                )
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Row 1: Header Info & Table Status
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "CALL FOR: $playerName",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFFFFD54F)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "8 ★ BUMPER (+160/-80)",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFBBF24)
                        )
                    }

                    Text(
                        text = "Table: $totalCallsSoFar (Min: 9)",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (totalCallsSoFar >= 9) Color(0xFF4ADE80) else Color(0xFFF59E0B)
                    )
                }

                // Optional Warning / Error Alert
                if (callPrompt != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0x33EF4444), RoundedCornerShape(6.dp))
                            .border(0.8.dp, Color(0x88EF4444), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = callPrompt,
                            color = Color(0xFFFF8A80),
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                }

                // Row 2: Number Chips (1..7 and 8 ★ BUMPER) + Prominent SUBMIT CALL Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Chips for 1 to 8
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        for (num in 1..8) {
                            val isBumper = num == 8
                            val isSelected = selectedNumber == num

                            CallSelectorChip(
                                number = num,
                                isSelected = isSelected,
                                isBumper = isBumper,
                                onClick = {
                                    selectedNumber = num
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // Prominent Submit Call Button: locks call
                    Button(
                        onClick = {
                            onSelectCall(selectedNumber)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selectedNumber == 8) Color(0xFFD97706) else MaterialTheme.colorScheme.primary,
                            contentColor = if (selectedNumber == 8) Color.White else MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .height(34.dp)
                            .testTag("call_submit_button"),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = "Submit Call",
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = if (selectedNumber == 8) "CALL 8 ★" else "CALL $selectedNumber",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CallSelectorChip(
    number: Int,
    isSelected: Boolean,
    isBumper: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = when {
        isSelected && isBumper -> Color(0xFFD97706)
        isSelected -> MaterialTheme.colorScheme.primary
        isBumper -> Color(0xFF78350F)
        else -> Color(0xFF1E3A2B)
    }

    val borderColor = when {
        isSelected -> Color(0xFFFFD54F)
        isBumper -> Color(0xFFF59E0B).copy(alpha = 0.6f)
        else -> Color(0xFF2E5A44)
    }

    Box(
        modifier = Modifier
            .size(width = if (isBumper) 42.dp else 28.dp, height = 32.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(backgroundColor)
            .border(if (isSelected) 1.5.dp else 0.8.dp, borderColor, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .testTag("call_btn_$number"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "$number",
                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Bold,
                fontSize = 11.5.sp,
                color = if (isSelected) Color.White else Color(0xFFE2E8F0)
            )
            if (isBumper) {
                Text(
                    text = "★ BMP",
                    fontSize = 6.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFFFFD700),
                    lineHeight = 7.sp
                )
            }
        }
    }
}
