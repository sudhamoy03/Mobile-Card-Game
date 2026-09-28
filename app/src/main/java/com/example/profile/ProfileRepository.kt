package com.example.profile

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.Locale
import kotlin.random.Random

class PlayerIdAlreadyTakenException(message: String) : Exception(message)
class InvalidPlayerIdException(message: String) : Exception(message)

class ProfileRepository(private val context: Context) {

    private val prefs = context.getSharedPreferences("user_profile_cache", Context.MODE_PRIVATE)

    private val isFirebaseAvailable: Boolean
        get() = try {
            FirebaseApp.getApps(context).isNotEmpty()
        } catch (e: Exception) {
            false
        }

    private val firestore: FirebaseFirestore?
        get() = if (isFirebaseAvailable) {
            try { FirebaseFirestore.getInstance() } catch (e: Exception) { null }
        } else null

    private val storage: FirebaseStorage?
        get() = if (isFirebaseAvailable) {
            try { FirebaseStorage.getInstance() } catch (e: Exception) { null }
        } else null

    /**
     * Case-normalizes player ID (e.g. "@SUDHA4827" -> "sudha4827")
     */
    fun normalizePlayerId(playerId: String): String {
        return playerId.trim().removePrefix("@").lowercase(Locale.ROOT)
    }

    /**
     * Validates player ID rules:
     * - Must start with '@' or allow typing without '@' (auto-prefixed)
     * - 3 to 15 alphanumeric characters / underscores
     */
    fun validatePlayerId(playerId: String): String {
        val trimmed = playerId.trim()
        val withoutAt = trimmed.removePrefix("@")
        if (withoutAt.length < 3) {
            throw InvalidPlayerIdException("Player ID must be at least 3 characters.")
        }
        if (withoutAt.length > 15) {
            throw InvalidPlayerIdException("Player ID cannot exceed 15 characters.")
        }
        if (!withoutAt.matches(Regex("^[A-Za-z0-9_]+$"))) {
            throw InvalidPlayerIdException("Player ID can only contain letters, numbers, and underscores.")
        }
        return "@" + withoutAt.uppercase(Locale.ROOT)
    }

    /**
     * Generates a random available player ID suggestion
     */
    fun generateSuggestedPlayerId(displayName: String): String {
        val cleanName = displayName.filter { it.isLetterOrDigit() }.take(6).uppercase(Locale.ROOT)
        val prefix = if (cleanName.isNotEmpty()) cleanName else "PLAYER"
        val randomDigits = Random.nextInt(1000, 9999)
        return "@$prefix$randomDigits"
    }

    /**
     * Check if normalized player ID is available in Firestore
     */
    suspend fun isPlayerIdAvailable(normalizedId: String, currentUid: String): Boolean {
        val db = firestore ?: return true
        return try {
            val doc = db.collection("playerIds").document(normalizedId).get().await()
            if (!doc.exists()) {
                true
            } else {
                val ownerUid = doc.getString("uid")
                ownerUid == currentUid
            }
        } catch (e: Exception) {
            true // Allow offline
        }
    }

    /**
     * Fetch user profile from Firestore or local cache
     */
    suspend fun getUserProfile(uid: String): UserProfile? = withContext(Dispatchers.IO) {
        val db = firestore
        if (db != null) {
            try {
                val snapshot = db.collection("users").document(uid).get().await()
                if (snapshot.exists()) {
                    val profile = UserProfile.fromSnapshot(snapshot)
                    if (profile != null) {
                        saveProfileToLocalCache(profile)
                        return@withContext profile
                    }
                }
            } catch (e: Exception) {
                // Fallback to cache on network error
            }
        }
        loadProfileFromLocalCache(uid)
    }

    /**
     * Atomically creates or reserves player profile with Unique Player ID
     */
    suspend fun createProfile(
        uid: String,
        displayName: String,
        desiredPlayerId: String,
        avatarType: String,
        avatarId: String,
        avatarUrl: String?,
        accountType: String
    ): UserProfile = withContext(Dispatchers.IO) {
        val validatedId = validatePlayerId(desiredPlayerId)
        val normalizedId = normalizePlayerId(validatedId)
        val now = System.currentTimeMillis()

        val newProfile = UserProfile(
            uid = uid,
            displayName = displayName.trim().ifEmpty { "Player" },
            playerId = validatedId,
            normalizedPlayerId = normalizedId,
            avatarType = avatarType,
            avatarId = avatarId,
            avatarUrl = avatarUrl,
            accountType = accountType,
            createdAt = now,
            updatedAt = now
        )

        val db = firestore
        if (db != null) {
            try {
                db.runTransaction { transaction ->
                    val reservationRef = db.collection("playerIds").document(normalizedId)
                    val existingReservation = transaction.get(reservationRef)

                    if (existingReservation.exists()) {
                        val ownerUid = existingReservation.getString("uid")
                        if (ownerUid != null && ownerUid != uid) {
                            throw PlayerIdAlreadyTakenException("Player ID $validatedId is already taken. Please choose another.")
                        }
                    }

                    // Reserve Player ID
                    transaction.set(
                        reservationRef,
                        mapOf("uid" to uid, "createdAt" to now)
                    )

                    // Write User Profile
                    val userRef = db.collection("users").document(uid)
                    transaction.set(userRef, newProfile.toMap(), SetOptions.merge())
                }.await()
            } catch (e: Exception) {
                if (e is PlayerIdAlreadyTakenException) throw e
                // If offline, still allow local persistence
            }
        }

        saveProfileToLocalCache(newProfile)
        newProfile
    }

    /**
     * Save or update an existing user profile in local cache and Firestore
     */
    suspend fun saveUserProfile(profile: UserProfile) = withContext(Dispatchers.IO) {
        saveProfileToLocalCache(profile)
        firestore?.collection("users")?.document(profile.uid)?.set(
            profile.toMap(),
            SetOptions.merge()
        )
    }

    /**
     * Atomically updates user profile and changes Player ID if modified
     */
    suspend fun updateProfile(
        currentProfile: UserProfile,
        newDisplayName: String,
        newPlayerId: String,
        newAvatarType: String,
        newAvatarId: String,
        newAvatarUrl: String?
    ): UserProfile = withContext(Dispatchers.IO) {
        val validatedId = validatePlayerId(newPlayerId)
        val newNormalizedId = normalizePlayerId(validatedId)
        val oldNormalizedId = currentProfile.normalizedPlayerId
        val now = System.currentTimeMillis()

        val isChangingPlayerId = oldNormalizedId != newNormalizedId

        val updatedProfile = currentProfile.copy(
            displayName = newDisplayName.trim().ifEmpty { currentProfile.displayName },
            playerId = validatedId,
            normalizedPlayerId = newNormalizedId,
            avatarType = newAvatarType,
            avatarId = newAvatarId,
            avatarUrl = newAvatarUrl ?: currentProfile.avatarUrl,
            updatedAt = now
        )

        val db = firestore
        if (db != null) {
            try {
                db.runTransaction { transaction ->
                    if (isChangingPlayerId) {
                        val newReservationRef = db.collection("playerIds").document(newNormalizedId)
                        val existing = transaction.get(newReservationRef)
                        if (existing.exists()) {
                            val ownerUid = existing.getString("uid")
                            if (ownerUid != null && ownerUid != currentProfile.uid) {
                                throw PlayerIdAlreadyTakenException("Player ID $validatedId is already taken. Please choose another.")
                            }
                        }

                        // Reserve new ID
                        transaction.set(
                            newReservationRef,
                            mapOf("uid" to currentProfile.uid, "createdAt" to now)
                        )

                        // Release old ID
                        if (oldNormalizedId.isNotEmpty()) {
                            val oldReservationRef = db.collection("playerIds").document(oldNormalizedId)
                            transaction.delete(oldReservationRef)
                        }
                    }

                    // Update User Profile
                    val userRef = db.collection("users").document(currentProfile.uid)
                    transaction.set(userRef, updatedProfile.toMap(), SetOptions.merge())
                }.await()
            } catch (e: Exception) {
                if (e is PlayerIdAlreadyTakenException) throw e
            }
        }

        saveProfileToLocalCache(updatedProfile)
        updatedProfile
    }

    /**
     * Upload custom avatar to Firebase Storage (users/{uid}/avatar/profile.jpg)
     */
    suspend fun uploadCustomAvatar(uid: String, bitmap: Bitmap): String = withContext(Dispatchers.IO) {
        val st = storage ?: throw Exception("Firebase Storage not available")
        val avatarRef = st.reference.child("users/$uid/avatar/profile.jpg")

        val baos = ByteArrayOutputStream()
        // Resize to square max 512x512
        val scaled = Bitmap.createScaledBitmap(bitmap, 512, 512, true)
        scaled.compress(Bitmap.CompressFormat.JPEG, 85, baos)
        val data = baos.toByteArray()

        avatarRef.putBytes(data).await()
        val downloadUri = avatarRef.downloadUrl.await()
        downloadUri.toString()
    }

    /**
     * Record completed game statistics
     */
    suspend fun recordGameFinished(uid: String, isWin: Boolean, scoreEarned: Int) = withContext(Dispatchers.IO) {
        val profile = getUserProfile(uid) ?: return@withContext
        val oldStats = profile.statistics
        val newStats = oldStats.copy(
            gamesPlayed = oldStats.gamesPlayed + 1,
            wins = if (isWin) oldStats.wins + 1 else oldStats.wins,
            totalScore = oldStats.totalScore + scoreEarned
        )
        val updated = profile.copy(statistics = newStats, updatedAt = System.currentTimeMillis())
        saveProfileToLocalCache(updated)

        firestore?.collection("users")?.document(uid)?.set(
            mapOf("statistics" to newStats.toMap(), "updatedAt" to System.currentTimeMillis()),
            SetOptions.merge()
        )
    }

    // =========================================================================
    // Local Cache
    // =========================================================================

    private fun saveProfileToLocalCache(profile: UserProfile) {
        prefs.edit().apply {
            putString("cached_uid", profile.uid)
            putString("cached_displayName", profile.displayName)
            putString("cached_playerId", profile.playerId)
            putString("cached_normalizedPlayerId", profile.normalizedPlayerId)
            putString("cached_avatarType", profile.avatarType)
            putString("cached_avatarId", profile.avatarId)
            putString("cached_avatarUrl", profile.avatarUrl)
            putString("cached_accountType", profile.accountType)
            putLong("cached_createdAt", profile.createdAt)
            putLong("cached_updatedAt", profile.updatedAt)
            putInt("cached_gamesPlayed", profile.statistics.gamesPlayed)
            putInt("cached_wins", profile.statistics.wins)
            putInt("cached_totalScore", profile.statistics.totalScore)
            apply()
        }
    }

    private fun loadProfileFromLocalCache(uid: String): UserProfile? {
        val cachedUid = prefs.getString("cached_uid", null) ?: return null
        if (cachedUid != uid) return null

        return UserProfile(
            uid = cachedUid,
            displayName = prefs.getString("cached_displayName", "Player") ?: "Player",
            playerId = prefs.getString("cached_playerId", "@PLAYER") ?: "@PLAYER",
            normalizedPlayerId = prefs.getString("cached_normalizedPlayerId", "player") ?: "player",
            avatarType = prefs.getString("cached_avatarType", UserProfile.AVATAR_TYPE_PRESET) ?: UserProfile.AVATAR_TYPE_PRESET,
            avatarId = prefs.getString("cached_avatarId", UserProfile.DEFAULT_AVATAR_ID) ?: UserProfile.DEFAULT_AVATAR_ID,
            avatarUrl = prefs.getString("cached_avatarUrl", null),
            accountType = prefs.getString("cached_accountType", UserProfile.ACCOUNT_TYPE_GUEST) ?: UserProfile.ACCOUNT_TYPE_GUEST,
            createdAt = prefs.getLong("cached_createdAt", System.currentTimeMillis()),
            updatedAt = prefs.getLong("cached_updatedAt", System.currentTimeMillis()),
            statistics = UserStatistics(
                gamesPlayed = prefs.getInt("cached_gamesPlayed", 0),
                wins = prefs.getInt("cached_wins", 0),
                totalScore = prefs.getInt("cached_totalScore", 0)
            )
        )
    }

    fun clearLocalCache() {
        prefs.edit().clear().apply()
    }
}
