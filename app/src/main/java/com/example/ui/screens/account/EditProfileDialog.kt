package com.example.ui.screens.account

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.profile.AvatarManager
import com.example.profile.UserProfile
import kotlinx.coroutines.launch

@Composable
fun EditProfileDialog(
    profile: UserProfile,
    isLoading: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onSaveProfile: (
        newName: String,
        newPlayerId: String,
        avatarType: String,
        avatarId: String,
        customBitmap: android.graphics.Bitmap?
    ) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var name by remember { mutableStateOf(profile.displayName) }
    var playerId by remember { mutableStateOf(profile.playerId) }
    var avatarType by remember { mutableStateOf(profile.avatarType) }
    var avatarId by remember { mutableStateOf(profile.avatarId) }
    var customBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var customUri by remember { mutableStateOf(profile.avatarUrl) }

    // Zero-permission Photo Picker for custom avatar
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                try {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        val bmp = BitmapFactory.decodeStream(stream)
                        if (bmp != null) {
                            customBitmap = bmp
                            avatarType = UserProfile.AVATAR_TYPE_CUSTOM
                            customUri = uri.toString()
                        }
                    }
                } catch (_: Exception) {}
            }
        }
    }

    Dialog(onDismissRequest = { if (!isLoading) onDismiss() }) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF0C2417),
            border = BorderStroke(1.2.dp, Color(0xFFD4AF37).copy(alpha = 0.6f)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
                .testTag("edit_profile_dialog")
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "EDIT PROFILE",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFFFFD54F),
                        letterSpacing = 1.sp
                    )
                    IconButton(onClick = onDismiss, enabled = !isLoading) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Close",
                            tint = Color(0xFF94A3B8)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Current Avatar Preview
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1E293B))
                        .border(2.dp, Color(0xFFD4AF37), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (avatarType == UserProfile.AVATAR_TYPE_CUSTOM && (customBitmap != null || customUri != null)) {
                        if (customBitmap != null) {
                            androidx.compose.foundation.Image(
                                bitmap = customBitmap!!.asImageBitmap(),
                                contentDescription = "Custom Avatar",
                                modifier = Modifier.size(72.dp).clip(CircleShape)
                            )
                        } else {
                            AsyncImage(
                                model = customUri,
                                contentDescription = "Custom Avatar",
                                modifier = Modifier.size(72.dp).clip(CircleShape)
                            )
                        }
                    } else {
                        val preset = AvatarManager.getPresetById(avatarId)
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(preset.backgroundColor),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = preset.icon,
                                contentDescription = preset.name,
                                tint = preset.iconColor,
                                modifier = Modifier.size(38.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Custom Avatar upload button
                OutlinedButton(
                    onClick = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    modifier = Modifier.height(32.dp),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Color(0xFF64748B)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFE2E8F0))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AddPhotoAlternate,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Text("Upload Custom Photo", fontSize = 10.5.sp)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Preset Avatars
                Text(
                    text = "Or choose a preset:",
                    fontSize = 11.sp,
                    color = Color(0xFF94A3B8),
                    modifier = Modifier.fillMaxWidth()
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(vertical = 6.dp)
                ) {
                    items(AvatarManager.PRESET_AVATARS) { avatar ->
                        val isSelected = avatarType == UserProfile.AVATAR_TYPE_PRESET && avatar.id == avatarId
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(avatar.backgroundColor)
                                .border(
                                    width = if (isSelected) 2.dp else 0.8.dp,
                                    color = if (isSelected) Color(0xFFFFD54F) else Color(0xFF334155),
                                    shape = CircleShape
                                )
                                .clickable {
                                    avatarType = UserProfile.AVATAR_TYPE_PRESET
                                    avatarId = avatar.id
                                    customBitmap = null
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = avatar.icon,
                                contentDescription = avatar.name,
                                tint = avatar.iconColor,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Display Name
                Text(
                    text = "DISPLAY NAME",
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF94A3B8),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFFFFD54F),
                        unfocusedBorderColor = Color(0xFF334155),
                        focusedTextColor = Color(0xFFFFFBEB),
                        unfocusedTextColor = Color(0xFFE2E8F0),
                        focusedContainerColor = Color(0xFF08180E),
                        unfocusedContainerColor = Color(0xFF08180E)
                    ),
                    shape = RoundedCornerShape(8.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Unique Player ID
                Text(
                    text = "UNIQUE PLAYER ID",
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF94A3B8),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = playerId,
                    onValueChange = { input ->
                        val clean = if (input.startsWith("@")) input else "@$input"
                        playerId = clean.uppercase()
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFFFFD54F),
                        unfocusedBorderColor = Color(0xFF334155),
                        focusedTextColor = Color(0xFFFFD54F),
                        unfocusedTextColor = Color(0xFFFFD54F),
                        focusedContainerColor = Color(0xFF08180E),
                        unfocusedContainerColor = Color(0xFF08180E)
                    ),
                    shape = RoundedCornerShape(8.dp)
                )

                // Error message
                if (!errorMessage.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        color = Color(0xFF7F1D1D).copy(alpha = 0.85f),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ErrorOutline,
                                contentDescription = null,
                                tint = Color(0xFFFCA5A5),
                                modifier = Modifier.size(14.dp)
                            )
                            Text(text = errorMessage, color = Color(0xFFFEF2F2), fontSize = 11.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Save button
                Button(
                    onClick = {
                        onSaveProfile(name, playerId, avatarType, avatarId, customBitmap)
                    },
                    enabled = !isLoading && name.isNotBlank() && playerId.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag("save_profile_button"),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFD4AF37),
                        contentColor = Color(0xFF0F172A)
                    )
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            color = Color(0xFF0F172A),
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Save,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Text("SAVE CHANGES", fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                        }
                    }
                }
            }
        }
    }
}
