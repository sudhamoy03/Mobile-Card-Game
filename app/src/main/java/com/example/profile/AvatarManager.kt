package com.example.profile

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

data class PresetAvatar(
    val id: String,
    val name: String,
    val icon: ImageVector,
    val backgroundColor: Color,
    val iconColor: Color
)

object AvatarManager {
    val PRESET_AVATARS: List<PresetAvatar> = listOf(
        PresetAvatar(
            id = "preset_01",
            name = "Ace of Spades",
            icon = Icons.Filled.Style,
            backgroundColor = Color(0xFF1E293B),
            iconColor = Color(0xFFF1F5F9)
        ),
        PresetAvatar(
            id = "preset_02",
            name = "Royal King",
            icon = Icons.Filled.EmojiEvents,
            backgroundColor = Color(0xFF78350F),
            iconColor = Color(0xFFFFD54F)
        ),
        PresetAvatar(
            id = "preset_03",
            name = "Diamond Queen",
            icon = Icons.Filled.WorkspacePremium,
            backgroundColor = Color(0xFF831843),
            iconColor = Color(0xFFF472B6)
        ),
        PresetAvatar(
            id = "preset_04",
            name = "Golden Star",
            icon = Icons.Filled.Star,
            backgroundColor = Color(0xFF854D0E),
            iconColor = Color(0xFFFDE047)
        ),
        PresetAvatar(
            id = "preset_05",
            name = "High Roller",
            icon = Icons.Filled.Casino,
            backgroundColor = Color(0xFF14532D),
            iconColor = Color(0xFF4ADE80)
        ),
        PresetAvatar(
            id = "preset_06",
            name = "Cyber Master",
            icon = Icons.Filled.SmartToy,
            backgroundColor = Color(0xFF1E3A8A),
            iconColor = Color(0xFF60A5FA)
        ),
        PresetAvatar(
            id = "preset_07",
            name = "Tactician",
            icon = Icons.Filled.MilitaryTech,
            backgroundColor = Color(0xFF581C87),
            iconColor = Color(0xFFC084FC)
        ),
        PresetAvatar(
            id = "preset_08",
            name = "Card Player",
            icon = Icons.Filled.Person,
            backgroundColor = Color(0xFF334155),
            iconColor = Color(0xFF94A3B8)
        )
    )

    fun getPresetById(id: String): PresetAvatar {
        return PRESET_AVATARS.find { it.id == id } ?: PRESET_AVATARS[0]
    }
}
