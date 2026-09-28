package com.example.ui.screens.auth

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.profile.AvatarManager
import com.example.profile.UserProfile

@Composable
fun CreateProfileScreen(
    initialDisplayName: String,
    initialPlayerId: String,
    isLoading: Boolean,
    errorMessage: String?,
    onGenerateNewId: () -> String,
    onCreateProfile: (displayName: String, playerId: String, avatarType: String, avatarId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    var displayName by remember { mutableStateOf(initialDisplayName) }
    var playerId by remember { mutableStateOf(initialPlayerId) }
    var selectedAvatarId by remember { mutableStateOf(UserProfile.DEFAULT_AVATAR_ID) }

    val selectedAvatar = remember(selectedAvatarId) {
        AvatarManager.getPresetById(selectedAvatarId)
    }

    val scrollState = rememberScrollState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF071E12),
                        Color(0xFF030D08)
                    )
                )
            )
            .testTag("create_profile_screen"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 480.dp)
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 20.dp)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "CREATE PROFILE",
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.Serif,
                color = Color(0xFFFFD54F),
                letterSpacing = 1.sp
            )

            Text(
                text = "Choose your avatar and unique player handle",
                fontSize = 12.sp,
                color = Color(0xFFCBD5E1),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp, bottom = 20.dp)
            )

            // Current Avatar Preview
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(selectedAvatar.backgroundColor)
                    .border(2.dp, Color(0xFFD4AF37), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = selectedAvatar.icon,
                    contentDescription = selectedAvatar.name,
                    tint = selectedAvatar.iconColor,
                    modifier = Modifier.size(44.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Select an Avatar",
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFE2E8F0)
            )

            // Preset Avatars Carousel
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(vertical = 10.dp)
            ) {
                items(AvatarManager.PRESET_AVATARS) { avatar ->
                    val isSelected = avatar.id == selectedAvatarId
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .background(avatar.backgroundColor)
                            .border(
                                width = if (isSelected) 2.5.dp else 1.dp,
                                color = if (isSelected) Color(0xFFFFD54F) else Color(0xFF334155),
                                shape = CircleShape
                            )
                            .clickable { selectedAvatarId = avatar.id }
                            .testTag("avatar_option_${avatar.id}"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = avatar.icon,
                            contentDescription = avatar.name,
                            tint = avatar.iconColor,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Field 1: Display Name
            Text(
                text = "DISPLAY NAME",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF94A3B8),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedTextField(
                value = displayName,
                onValueChange = { displayName = it },
                singleLine = true,
                placeholder = { Text("Enter your name", color = Color(0xFF64748B)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("display_name_input"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFFFFD54F),
                    unfocusedBorderColor = Color(0xFF334155),
                    focusedTextColor = Color(0xFFFFFBEB),
                    unfocusedTextColor = Color(0xFFE2E8F0),
                    focusedContainerColor = Color(0xFF0F2618),
                    unfocusedContainerColor = Color(0xFF0A1C12)
                ),
                shape = RoundedCornerShape(10.dp)
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Field 2: Unique Player ID (@...)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "UNIQUE PLAYER ID",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF94A3B8)
                )
                IconButton(
                    onClick = { playerId = onGenerateNewId() },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = "Randomize ID",
                        tint = Color(0xFFFFD54F),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedTextField(
                value = playerId,
                onValueChange = { input ->
                    val clean = if (input.startsWith("@")) input else "@$input"
                    playerId = clean.uppercase()
                },
                singleLine = true,
                placeholder = { Text("@PLAYER1234", color = Color(0xFF64748B)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("player_id_input"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFFFFD54F),
                    unfocusedBorderColor = Color(0xFF334155),
                    focusedTextColor = Color(0xFFFFD54F),
                    unfocusedTextColor = Color(0xFFFFD54F),
                    focusedContainerColor = Color(0xFF0F2618),
                    unfocusedContainerColor = Color(0xFF0A1C12)
                ),
                shape = RoundedCornerShape(10.dp)
            )

            Text(
                text = "Must be globally unique (3-15 alphanumeric characters)",
                fontSize = 10.sp,
                color = Color(0xFF64748B),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp, bottom = 12.dp)
            )

            // Error Message
            if (!errorMessage.isNullOrBlank()) {
                Surface(
                    color = Color(0xFF7F1D1D).copy(alpha = 0.85f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Color(0xFFEF4444)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ErrorOutline,
                            contentDescription = "Error",
                            tint = Color(0xFFFCA5A5),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = errorMessage,
                            color = Color(0xFFFEF2F2),
                            fontSize = 11.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Create Profile Button
            Button(
                onClick = {
                    onCreateProfile(displayName, playerId, UserProfile.AVATAR_TYPE_PRESET, selectedAvatarId)
                },
                enabled = !isLoading && displayName.isNotBlank() && playerId.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("submit_create_profile_button"),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFD4AF37),
                    contentColor = Color(0xFF0F172A),
                    disabledContainerColor = Color(0xFF334155),
                    disabledContentColor = Color(0xFF64748B)
                )
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        color = Color(0xFF0F172A),
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "CREATE PROFILE",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        }
    }
}
