package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.profile.AvatarManager
import com.example.profile.InvalidPlayerIdException
import com.example.profile.ProfileRepository
import com.example.profile.UserProfile
import com.example.profile.UserStatistics
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AuthProfileTest {

    private lateinit var context: Context
    private lateinit var repository: ProfileRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        repository = ProfileRepository(context)
        repository.clearLocalCache()
    }

    @Test
    fun testPlayerIdNormalization() {
        val normalized = repository.normalizePlayerId("@Sudha4827")
        assertEquals("sudha4827", normalized)

        val normalized2 = repository.normalizePlayerId("PLAYER_01")
        assertEquals("player_01", normalized2)
    }

    @Test
    fun testPlayerIdValidationSuccess() {
        val valid1 = repository.validatePlayerId("sudha4827")
        assertEquals("@SUDHA4827", valid1)

        val valid2 = repository.validatePlayerId("@king_99")
        assertEquals("@KING_99", valid2)
    }

    @Test(expected = InvalidPlayerIdException::class)
    fun testPlayerIdValidationTooShort() {
        repository.validatePlayerId("@ab")
    }

    @Test(expected = InvalidPlayerIdException::class)
    fun testPlayerIdValidationTooLong() {
        repository.validatePlayerId("@this_player_id_is_way_too_long_for_system")
    }

    @Test(expected = InvalidPlayerIdException::class)
    fun testPlayerIdValidationInvalidCharacters() {
        repository.validatePlayerId("@player#123$")
    }

    @Test
    fun testSuggestedPlayerIdGeneration() {
        val suggested = repository.generateSuggestedPlayerId("Sudha")
        assertTrue(suggested.startsWith("@SUDHA"))
        assertTrue(suggested.length >= 7)
    }

    @Test
    fun testCreateAndLoadProfileOffline() = runBlocking {
        val profile = repository.createProfile(
            uid = "test_user_123",
            displayName = "Card Champ",
            desiredPlayerId = "@CHAMP99",
            avatarType = UserProfile.AVATAR_TYPE_PRESET,
            avatarId = "preset_02",
            avatarUrl = null,
            accountType = UserProfile.ACCOUNT_TYPE_GOOGLE
        )

        assertEquals("test_user_123", profile.uid)
        assertEquals("Card Champ", profile.displayName)
        assertEquals("@CHAMP99", profile.playerId)
        assertEquals("champ99", profile.normalizedPlayerId)

        // Verify retrieval from local cache
        val loaded = repository.getUserProfile("test_user_123")
        assertNotNull(loaded)
        assertEquals(profile.displayName, loaded?.displayName)
        assertEquals(profile.playerId, loaded?.playerId)
    }

    @Test
    fun testStatisticsCalculation() {
        val stats = UserStatistics(
            gamesPlayed = 10,
            wins = 6,
            totalScore = 1850,
            tricksWon = 42
        )
        assertEquals(60.0f, stats.winRate, 0.01f)
    }

    @Test
    fun testPresetAvatarRetrieval() {
        val preset = AvatarManager.getPresetById("preset_02")
        assertEquals("Royal King", preset.name)

        // Fallback for unknown ID
        val fallback = AvatarManager.getPresetById("unknown_id")
        assertEquals("Ace of Spades", fallback.name)
    }

    @Test
    fun testAccountTypeConstantsAndProfilePreservation() = runBlocking {
        assertEquals("google", UserProfile.ACCOUNT_TYPE_GOOGLE)
        assertEquals("guest", UserProfile.ACCOUNT_TYPE_GUEST)

        // Verify profile bound to specific Firebase UID
        val uid = "firebase_uid_test_123"
        val profile = repository.createProfile(
            uid = uid,
            displayName = "Persistent Guest",
            desiredPlayerId = "@GUEST_77",
            avatarType = UserProfile.AVATAR_TYPE_PRESET,
            avatarId = "preset_01",
            avatarUrl = null,
            accountType = UserProfile.ACCOUNT_TYPE_GUEST
        )
        assertEquals(uid, profile.uid)
        assertEquals(UserProfile.ACCOUNT_TYPE_GUEST, profile.accountType)

        // Retrieve by Firebase UID
        val loaded = repository.getUserProfile(uid)
        assertNotNull(loaded)
        assertEquals(uid, loaded?.uid)
        assertEquals("Persistent Guest", loaded?.displayName)
        assertEquals("@GUEST_77", loaded?.playerId)
    }

    @Test
    fun testAuthManagerGuestPersistence() = runBlocking {
        val authManager = com.example.auth.AuthManager(context)
        authManager.signOut()
        org.junit.Assert.assertNull(authManager.currentUser)

        // Sign in anonymously
        val user = authManager.signInAnonymously()
        org.junit.Assert.assertNotNull(user)
        val initialUid = user.uid
        org.junit.Assert.assertTrue(user.isAnonymous)

        // Simulate app restart by instantiating new AuthManager
        val restartedAuthManager = com.example.auth.AuthManager(context)
        val restoredUser = restartedAuthManager.awaitInitialAuthState()
        org.junit.Assert.assertNotNull(restoredUser)
        assertEquals(initialUid, restoredUser?.uid)
        org.junit.Assert.assertTrue(restoredUser?.isAnonymous == true)

        // Sign out terminates session
        restartedAuthManager.signOut()
        org.junit.Assert.assertNull(restartedAuthManager.currentUser)
    }
}
