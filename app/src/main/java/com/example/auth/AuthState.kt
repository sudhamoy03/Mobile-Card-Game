package com.example.auth

import com.example.profile.UserProfile

sealed class AuthState {
    object InitialLoading : AuthState()
    object Unauthenticated : AuthState()
    data class NeedsProfile(
        val user: AuthUser,
        val defaultName: String
    ) : AuthState()
    data class Authenticated(val profile: UserProfile, val isGuest: Boolean) : AuthState()
    data class Error(val message: String) : AuthState()
}
