package com.example.ui.screens.account

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.profile.AvatarManager
import com.example.profile.UserProfile

@Composable
fun AccountScreen(
    profile: UserProfile,
    isGuest: Boolean,
    isLoading: Boolean,
    errorMessage: String?,
    onBackClick: () -> Unit,
    onLinkGoogleClick: () -> Unit,
    onLogoutClick: () -> Unit,
    onSaveProfile: (
        newName: String,
        newPlayerId: String,
        avatarType: String,
        avatarId: String,
        customBitmap: android.graphics.Bitmap?
    ) -> Unit,
    modifier: Modifier = Modifier
) {
    var showEditDialog by remember { mutableStateOf(false) }
    var showLogoutConfirmDialog by remember { mutableStateOf(false) }

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
            .testTag("account_screen")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBackClick, modifier = Modifier.testTag("account_back_button")) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color(0xFFFFD54F)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "ACCOUNT",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.Serif,
                    color = Color(0xFFFFD54F),
                    letterSpacing = 1.sp
                )
            }

            // Error banner if any
            if (!errorMessage.isNullOrBlank()) {
                Surface(
                    color = Color(0xFF7F1D1D).copy(alpha = 0.85f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                ) {
                    Text(
                        text = errorMessage,
                        color = Color(0xFFFEF2F2),
                        fontSize = 11.5.sp,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            // Profile Card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFF0C2417),
                border = BorderStroke(1.dp, Color(0xFFD4AF37).copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Avatar
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1E293B))
                            .border(1.5.dp, Color(0xFFFFD54F), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (profile.avatarType == UserProfile.AVATAR_TYPE_CUSTOM && !profile.avatarUrl.isNullOrEmpty()) {
                            AsyncImage(
                                model = profile.avatarUrl,
                                contentDescription = "Avatar",
                                modifier = Modifier.size(64.dp).clip(CircleShape)
                            )
                        } else {
                            val preset = AvatarManager.getPresetById(profile.avatarId)
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(preset.backgroundColor),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = preset.icon,
                                    contentDescription = preset.name,
                                    tint = preset.iconColor,
                                    modifier = Modifier.size(34.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = profile.displayName,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFFFBEB)
                        )
                        Text(
                            text = profile.playerId,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFFFFD54F)
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        // Account Type Badge
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (isGuest) Icons.Filled.Person else Icons.Filled.VerifiedUser,
                                contentDescription = null,
                                tint = if (isGuest) Color(0xFF94A3B8) else Color(0xFF4ADE80),
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isGuest) "Guest Player" else "Google Account",
                                fontSize = 10.5.sp,
                                color = if (isGuest) Color(0xFF94A3B8) else Color(0xFF4ADE80),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    // Edit Profile Button
                    OutlinedButton(
                        onClick = { showEditDialog = true },
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(0.8.dp, Color(0xFFD4AF37)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFFD54F)),
                        modifier = Modifier.testTag("account_edit_profile_button")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Edit,
                                contentDescription = null,
                                modifier = Modifier.size(13.dp)
                            )
                            Text("Edit", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Game Statistics Summary Card
            Text(
                text = "CAREER STATISTICS",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF94A3B8),
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF091C12),
                border = BorderStroke(0.8.dp, Color(0xFF1E3A2B))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp, horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatBox(label = "PLAYED", value = profile.statistics.gamesPlayed.toString())
                    StatBox(label = "WINS", value = profile.statistics.wins.toString(), valueColor = Color(0xFF4ADE80))
                    StatBox(
                        label = "WIN RATE",
                        value = "${profile.statistics.winRate.toInt()}%",
                        valueColor = Color(0xFFFFD54F)
                    )
                    StatBox(label = "POINTS", value = profile.statistics.totalScore.toString())
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            HorizontalDivider(color = Color(0xFF1E3A2B))
            Spacer(modifier = Modifier.height(16.dp))

            // PROFILE / ACCOUNT MANAGEMENT SECTION
            Text(
                text = "PROFILE / ACCOUNT",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF94A3B8),
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(10.dp))

            // Option: Link Google Account (GUEST ONLY)
            if (isGuest) {
                Button(
                    onClick = onLinkGoogleClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("link_google_button"),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color(0xFF0F172A)
                    )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Link,
                            contentDescription = null,
                            tint = Color(0xFF2563EB),
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Link Google Account",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            // Option: Log Out
            OutlinedButton(
                onClick = { showLogoutConfirmDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("account_logout_button"),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.6f)),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color(0xFFF87171)
                )
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.ExitToApp,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Log Out",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(30.dp))
        }
    }

    // Edit Profile Modal
    if (showEditDialog) {
        EditProfileDialog(
            profile = profile,
            isLoading = isLoading,
            errorMessage = errorMessage,
            onDismiss = { showEditDialog = false },
            onSaveProfile = { newName, newId, type, avatarId, bmp ->
                onSaveProfile(newName, newId, type, avatarId, bmp)
                showEditDialog = false
            }
        )
    }

    // Logout Confirmation Dialog
    if (showLogoutConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirmDialog = false },
            title = {
                Text(
                    text = "Log Out?",
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFFFBEB)
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to log out? Your stats and profile will be safely preserved in your account.",
                    color = Color(0xFFCBD5E1),
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showLogoutConfirmDialog = false
                        onLogoutClick()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                ) {
                    Text("Log Out", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirmDialog = false }) {
                    Text("Cancel", color = Color(0xFF94A3B8))
                }
            },
            containerColor = Color(0xFF0F2618),
            shape = RoundedCornerShape(14.dp)
        )
    }
}

@Composable
private fun StatBox(label: String, value: String, valueColor: Color = Color(0xFFFFFBEB)) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64748B))
        Text(text = value, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = valueColor)
    }
}
