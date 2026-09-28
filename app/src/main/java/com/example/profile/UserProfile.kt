package com.example.profile

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.IgnoreExtraProperties

@IgnoreExtraProperties
data class UserStatistics(
    val gamesPlayed: Int = 0,
    val wins: Int = 0,
    val totalScore: Int = 0,
    val tricksWon: Int = 0
) {
    val winRate: Float
        get() = if (gamesPlayed > 0) (wins.toFloat() / gamesPlayed) * 100f else 0f

    fun toMap(): Map<String, Any> = mapOf(
        "gamesPlayed" to gamesPlayed,
        "wins" to wins,
        "totalScore" to totalScore,
        "tricksWon" to tricksWon
    )

    companion object {
        fun fromMap(map: Map<String, Any?>?): UserStatistics {
            if (map == null) return UserStatistics()
            return UserStatistics(
                gamesPlayed = (map["gamesPlayed"] as? Number)?.toInt() ?: 0,
                wins = (map["wins"] as? Number)?.toInt() ?: 0,
                totalScore = (map["totalScore"] as? Number)?.toInt() ?: 0,
                tricksWon = (map["tricksWon"] as? Number)?.toInt() ?: 0
            )
        }
    }
}

@IgnoreExtraProperties
data class UserProfile(
    val uid: String = "",
    val displayName: String = "",
    val playerId: String = "",
    val normalizedPlayerId: String = "",
    val avatarType: String = AVATAR_TYPE_PRESET, // "preset" or "custom"
    val avatarId: String = DEFAULT_AVATAR_ID,
    val avatarUrl: String? = null,
    val accountType: String = ACCOUNT_TYPE_GUEST, // "GUEST" or "GOOGLE"
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val statistics: UserStatistics = UserStatistics()
) {
    companion object {
        const val AVATAR_TYPE_PRESET = "preset"
        const val AVATAR_TYPE_CUSTOM = "custom"
        const val DEFAULT_AVATAR_ID = "preset_01"

        const val ACCOUNT_TYPE_GUEST = "guest"
        const val ACCOUNT_TYPE_GOOGLE = "google"

        fun fromSnapshot(snapshot: DocumentSnapshot): UserProfile? {
            if (!snapshot.exists()) return null
            val data = snapshot.data ?: return null

            @Suppress("UNCHECKED_CAST")
            val statsMap = data["statistics"] as? Map<String, Any?>
            val rawAccountType = data["accountType"] as? String ?: ACCOUNT_TYPE_GUEST
            val normalizedAccountType = if (rawAccountType.equals("google", ignoreCase = true)) {
                ACCOUNT_TYPE_GOOGLE
            } else {
                ACCOUNT_TYPE_GUEST
            }

            return UserProfile(
                uid = snapshot.id,
                displayName = data["displayName"] as? String ?: "Player",
                playerId = data["playerId"] as? String ?: "@PLAYER",
                normalizedPlayerId = data["normalizedPlayerId"] as? String ?: "player",
                avatarType = data["avatarType"] as? String ?: AVATAR_TYPE_PRESET,
                avatarId = data["avatarId"] as? String ?: DEFAULT_AVATAR_ID,
                avatarUrl = data["avatarUrl"] as? String,
                accountType = normalizedAccountType,
                createdAt = (data["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                updatedAt = (data["updatedAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                statistics = UserStatistics.fromMap(statsMap)
            )
        }
    }

    fun toMap(): Map<String, Any?> = mapOf(
        "uid" to uid,
        "displayName" to displayName,
        "playerId" to playerId,
        "normalizedPlayerId" to normalizedPlayerId,
        "avatarType" to avatarType,
        "avatarId" to avatarId,
        "avatarUrl" to avatarUrl,
        "accountType" to accountType,
        "createdAt" to createdAt,
        "updatedAt" to updatedAt,
        "statistics" to statistics.toMap()
    )
}
