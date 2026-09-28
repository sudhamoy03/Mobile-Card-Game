package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.auth.AuthState
import com.example.model.GamePhase
import com.example.ui.components.FourCardsLogo
import com.example.ui.screens.GameTableScreen
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.OnlineLobbyScreen
import com.example.ui.screens.OpeningAnimationScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.WithFriendsDialog
import com.example.ui.screens.account.AccountScreen
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.runtime.DisposableEffect
import com.example.ui.screens.auth.CreateProfileScreen
import com.example.ui.screens.auth.LoginScreen
import com.example.ui.theme.MobileCardGameTheme
import com.example.viewmodel.GameViewModel
import kotlinx.coroutines.launch

enum class OverlayScreen {
    NONE,
    SETTINGS,
    HISTORY,
    ACCOUNT
}

class MainActivity : ComponentActivity() {

    private val viewModel: GameViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val authState by viewModel.authState.collectAsState()
            val authLoading by viewModel.authLoading.collectAsState()
            val authErrorMessage by viewModel.authErrorMessage.collectAsState()

            val gameState by viewModel.gameState.collectAsState()
            val hasSavedGame by viewModel.hasSavedGame.collectAsState()
            val settings by viewModel.settings.collectAsState()
            val completedGames by viewModel.gameRepository.completedGamesFlow.collectAsState(initial = emptyList())

            var overlayScreen by remember { mutableStateOf(OverlayScreen.NONE) }
            val coroutineScope = rememberCoroutineScope()

            val isConnectingOnline by viewModel.isConnectingOnline.collectAsState()
            val socketConnectionState by viewModel.socketConnectionState.collectAsState()
            val socketErrorMessage by viewModel.socketErrorMessage.collectAsState()
            val networkPingMs by viewModel.networkPingMs.collectAsState()
            var showWithFriendsDialog by remember { mutableStateOf(false) }

            val windowInsetsController = remember(this) {
                WindowCompat.getInsetsController(window, window.decorView)
            }

            val isGameplay = gameState.phase !in listOf(
                GamePhase.OPENING_ANIMATION,
                GamePhase.HOME,
                GamePhase.LOCAL_SETUP,
                GamePhase.ONLINE_LOBBY
            ) && overlayScreen == OverlayScreen.NONE

            DisposableEffect(isGameplay) {
                if (isGameplay) {
                    windowInsetsController.systemBarsBehavior =
                        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
                } else {
                    windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
                }
                onDispose {
                    windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
                }
            }

            MobileCardGameTheme(
                themeMode = settings.themeMode,
                accentColor = settings.accentColor
            ) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    when (val currentAuthState = authState) {
                        // 1. App Launch: Checking Firebase Authentication State
                        is AuthState.InitialLoading -> {
                            LightweightSplashScreen()
                        }

                        // 2. Unauthenticated: Login Screen
                        is AuthState.Unauthenticated, is AuthState.Error -> {
                            LoginScreen(
                                isLoading = authLoading,
                                errorMessage = authErrorMessage ?: (currentAuthState as? AuthState.Error)?.message,
                                onLogin = {
                                    viewModel.signInAnonymously()
                                }
                            )
                        }

                        // 3. Authenticated without profile: First-Time Profile Creation
                        is AuthState.NeedsProfile -> {
                            val initialName = currentAuthState.defaultName
                            val initialId = remember(initialName) {
                                viewModel.profileRepository.generateSuggestedPlayerId(initialName)
                            }
                            CreateProfileScreen(
                                initialDisplayName = initialName,
                                initialPlayerId = initialId,
                                isLoading = authLoading,
                                errorMessage = authErrorMessage,
                                onGenerateNewId = {
                                    viewModel.profileRepository.generateSuggestedPlayerId("PLAYER")
                                },
                                onCreateProfile = { name, id, avatarType, avatarId ->
                                    viewModel.createProfile(name, id, avatarType, avatarId)
                                }
                            )
                        }

                        // 4. Fully Authenticated with Profile: Active Game
                        is AuthState.Authenticated -> {
                            val profile = currentAuthState.profile
                            val isGuest = currentAuthState.isGuest

                            when {
                                // Overlay: Account Screen
                                overlayScreen == OverlayScreen.ACCOUNT -> {
                                    AccountScreen(
                                        profile = profile,
                                        isGuest = isGuest,
                                        isLoading = authLoading,
                                        errorMessage = authErrorMessage,
                                        onBackClick = { overlayScreen = OverlayScreen.NONE },
                                        onLinkGoogleClick = {
                                            coroutineScope.launch {
                                                val result = viewModel.googleSignInHelper.getGoogleIdToken(activityContext = this@MainActivity)
                                                result.onSuccess { idToken ->
                                                    viewModel.linkGoogleAccount(idToken) { success, msg ->
                                                        Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show()
                                                    }
                                                }.onFailure { error ->
                                                    Toast.makeText(
                                                        this@MainActivity,
                                                        error.message ?: "Google linking failed",
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                }
                                            }
                                        },
                                        onLogoutClick = {
                                            viewModel.signOut()
                                            overlayScreen = OverlayScreen.NONE
                                        },
                                        onSaveProfile = { newName, newId, avatarType, avatarId, bmp ->
                                            viewModel.updateProfile(newName, newId, avatarType, avatarId, bmp)
                                        }
                                    )
                                }

                                // Overlay: Settings Screen
                                overlayScreen == OverlayScreen.SETTINGS -> {
                                    SettingsScreen(
                                        settings = settings,
                                        onBackClick = { overlayScreen = OverlayScreen.NONE },
                                        onUpdateTheme = { viewModel.settingsRepository.updateThemeMode(it) },
                                        onUpdateAccent = { viewModel.settingsRepository.updateAccentColor(it) },
                                        onUpdateSound = { m, a, w, v -> viewModel.settingsRepository.updateSoundSettings(m, a, w, v) },
                                        onUpdateHaptics = { e, s -> viewModel.settingsRepository.updateHaptics(e, s) },
                                        onUpdateAnimations = { viewModel.settingsRepository.updateAnimations(it) },
                                        onUpdateConfirmations = { l, n, e -> viewModel.settingsRepository.updateConfirmations(l, n, e) },
                                        onUpdatePrize = { e, a -> viewModel.settingsRepository.updatePrize(e, a) },
                                        onClearHistory = { viewModel.clearHistory() },
                                        onResetSettings = { viewModel.settingsRepository.resetSettings() },
                                        onCopyUpi = { viewModel.copyUpiId() },
                                        onPayUpi = { viewModel.payViaUpi() }
                                    )
                                }

                                // Overlay: History Screen
                                overlayScreen == OverlayScreen.HISTORY -> {
                                    HistoryScreen(
                                        historyList = completedGames,
                                        onBackClick = { overlayScreen = OverlayScreen.NONE },
                                        onClearHistory = { viewModel.clearHistory() }
                                    )
                                }

                                // Primary Game Phases
                                gameState.phase == GamePhase.OPENING_ANIMATION -> {
                                    OpeningAnimationScreen(
                                        onAnimationEnd = { viewModel.onOpeningAnimationComplete() }
                                    )
                                }

                                gameState.phase == GamePhase.HOME -> {
                                    HomeScreen(
                                        currentTheme = settings.themeMode,
                                        hasSavedGame = hasSavedGame,
                                        onToggleTheme = { viewModel.settingsRepository.toggleDarkLight() },
                                        onWithFriendsClick = {
                                            showWithFriendsDialog = true
                                            viewModel.connectToSocketServer()
                                        },
                                        onSoloPlayClick = { viewModel.startSoloGame() },
                                        onResumeGameClick = { viewModel.resumeSavedGame() },
                                        onOpenSettingsClick = { overlayScreen = OverlayScreen.SETTINGS },
                                        onOpenHistoryClick = { overlayScreen = OverlayScreen.HISTORY },
                                        onOpenAccountClick = { overlayScreen = OverlayScreen.ACCOUNT }
                                    )

                                    if (showWithFriendsDialog) {
                                        WithFriendsDialog(
                                            connectionState = socketConnectionState,
                                            errorMessage = socketErrorMessage,
                                            onRetryConnection = { viewModel.connectToSocketServer() },
                                            onDismiss = { showWithFriendsDialog = false },
                                            onCreateRoom = {
                                                viewModel.createOnlineRoom { success, msg ->
                                                    if (success) {
                                                        showWithFriendsDialog = false
                                                    } else {
                                                        Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            },
                                            onJoinRoom = { code ->
                                                viewModel.joinOnlineRoom(code) { success, msg ->
                                                    if (success) {
                                                        showWithFriendsDialog = false
                                                    } else {
                                                        Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            }
                                        )
                                    }
                                }

                                gameState.phase == GamePhase.LOCAL_SETUP -> {
                                    viewModel.navigateToHome()
                                }

                                gameState.phase == GamePhase.ONLINE_LOBBY -> {
                                    val isHost = gameState.players.firstOrNull { it.isHost }?.let {
                                        it.id == 1 || it.name.contains("Host", ignoreCase = true)
                                    } ?: true

                                    OnlineLobbyScreen(
                                        roomCode = gameState.roomCode,
                                        players = gameState.players,
                                        isHost = isHost,
                                        pingMs = networkPingMs,
                                        onBackClick = { viewModel.navigateToHome() },
                                        onAddBot = { viewModel.addOnlineBot() },
                                        onStartGame = { target ->
                                            viewModel.startOnlineGame(target)
                                        }
                                    )
                                }

                                // DEALING_ANIMATION, DEAL_COMPLETE, CALL_PHASE, PLAYING, TRICK_EVALUATION, ROUND_END, GAME_COMPLETE
                                else -> {
                                    GameTableScreen(
                                        gameState = gameState,
                                        isSoundEnabled = settings.masterSound,
                                        networkPingMs = networkPingMs,
                                        onToggleSound = {
                                            val next = !settings.masterSound
                                            viewModel.settingsRepository.updateSoundSettings(
                                                next,
                                                settings.actionSound,
                                                settings.winnerMusic,
                                                settings.soundVolume
                                            )
                                        },
                                        onSubmitCall = { seatIndex, call ->
                                            viewModel.submitCall(seatIndex, call)
                                        },
                                        onPlayCard = { seatIndex, card ->
                                            viewModel.playCard(seatIndex, card)
                                        },
                                        onExitGame = {
                                            viewModel.navigateToHome()
                                        },
                                        onSimulateDisconnect = { seatIndex ->
                                            viewModel.simulateDisconnect(seatIndex)
                                        },
                                        onReconnect = { seatIndex ->
                                            viewModel.reconnectPlayer(seatIndex)
                                        },
                                        onCardDealtSound = {
                                            viewModel.playDealingCardSlide()
                                        },
                                        onInvalidAction = {
                                            viewModel.playInvalidAction()
                                        },
                                        onRestartGame = {
                                            viewModel.restartGame()
                                        },
                                        onHomeClick = {
                                            viewModel.navigateToHome()
                                        }
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

@Composable
private fun LightweightSplashScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF071E12),
                        Color(0xFF030D08)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            FourCardsLogo()
            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = "Mobile Card Game",
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.Serif,
                color = Color(0xFFFFD54F),
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(24.dp))
            CircularProgressIndicator(
                color = Color(0xFFFFD54F),
                modifier = Modifier.size(28.dp),
                strokeWidth = 2.5.dp
            )
        }
    }
}
