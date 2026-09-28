package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.data.AppThemeMode

@Composable
fun ThemeToggle(
    currentTheme: AppThemeMode,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = currentTheme != AppThemeMode.LIGHT

    val thumbOffset by animateDpAsState(
        targetValue = if (isDark) 34.dp else 4.dp,
        animationSpec = tween(durationMillis = 200),
        label = "themeThumbOffset"
    )

    val trackColor by animateColorAsState(
        targetValue = if (isDark) Color(0xFF1B2E24) else Color(0xFFE2EBE5),
        animationSpec = tween(durationMillis = 200),
        label = "themeTrackColor"
    )

    val thumbColor by animateColorAsState(
        targetValue = if (isDark) MaterialTheme.colorScheme.primary else Color(0xFFFFA000),
        animationSpec = tween(durationMillis = 200),
        label = "themeThumbColor"
    )

    Box(
        modifier = modifier
            .width(68.dp)
            .height(36.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(trackColor)
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), RoundedCornerShape(18.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Switch,
                onClick = onToggle
            )
            .padding(vertical = 4.dp)
            .testTag("theme_toggle_button"),
        contentAlignment = Alignment.CenterStart
    ) {
        // Background indicators
        Row(
            modifier = Modifier
                .width(68.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.LightMode,
                contentDescription = "Light mode indicator",
                tint = if (!isDark) Color.Transparent else Color(0xFF8B9A90),
                modifier = Modifier.size(16.dp)
            )
            Box(modifier = Modifier.weight(1f))
            Icon(
                imageVector = Icons.Filled.DarkMode,
                contentDescription = "Dark mode indicator",
                tint = if (isDark) Color.Transparent else Color(0xFF8B9A90),
                modifier = Modifier.size(16.dp)
            )
        }

        // Animated Sliding Thumb
        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .size(28.dp)
                .shadow(4.dp, CircleShape)
                .clip(CircleShape)
                .background(thumbColor),
            contentAlignment = Alignment.Center
        ) {
            if (isDark) {
                Icon(
                    imageVector = Icons.Filled.DarkMode,
                    contentDescription = "Dark mode active",
                    tint = Color.Black,
                    modifier = Modifier.size(17.dp)
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.LightMode,
                    contentDescription = "Light mode active",
                    tint = Color.White,
                    modifier = Modifier.size(17.dp)
                )
            }
        }
    }
}
