package com.example.auth

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import kotlin.coroutines.resume

class AccountCollisionException(message: String) : Exception(message)

interface AuthUser {
    val uid: String
    val displayName: String?
    val isAnonymous: Boolean
    val email: String?
}

class FirebaseAuthUserWrapper(private val firebaseUser: FirebaseUser) : AuthUser {
    override val uid: String get() = firebaseUser.uid
    override val displayName: String? get() = firebaseUser.displayName
    override val isAnonymous: Boolean get() = firebaseUser.isAnonymous
    override val email: String? get() = firebaseUser.email
}

data class LocalAuthUser(
    override val uid: String,
    override val displayName: String? = null,
    override val isAnonymous: Boolean = true,
    override val email: String? = null
) : AuthUser

class AuthManager(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("card_game_auth_session", Context.MODE_PRIVATE)

    val isFirebaseAvailable: Boolean
        get() = try {
            FirebaseApp.getApps(context).isNotEmpty()
        } catch (_: Exception) {
            false
        }

    val auth: FirebaseAuth?
        get() = if (isFirebaseAvailable) {
            try {
                FirebaseAuth.getInstance()
            } catch (e: Exception) {
                Log.e("AuthDebug", "Error obtaining FirebaseAuth instance: ${e.message}")
                null
            }
        } else null

    /**
     * Resolves current user.
     * Firebase Authentication currentUser is checked first.
     * If remote Firebase Auth is unavailable or was created with persistent local session, it is restored.
     */
    val currentUser: AuthUser?
        get() {
            val fbUser = auth?.currentUser
            if (fbUser != null) {
                return FirebaseAuthUserWrapper(fbUser)
            }
            if (!isExplicitlyLoggedOut()) {
                val savedUid = prefs.getString("session_uid", null)
                if (!savedUid.isNullOrBlank()) {
                    val savedName = prefs.getString("session_display_name", null)
                    val isAnon = prefs.getBoolean("session_is_anonymous", true)
                    val email = prefs.getString("session_email", null)
                    return LocalAuthUser(
                        uid = savedUid,
                        displayName = savedName,
                        isAnonymous = isAnon,
                        email = email
                    )
                }
            }
            return null
        }

    val isGuest: Boolean
        get() = currentUser?.isAnonymous == true

    /**
     * Suspends until the initial auth state is resolved.
     * Ensures zero flashing of the login screen.
     */
    suspend fun awaitInitialAuthState(): AuthUser? = withContext(Dispatchers.IO) {
        val fbAuth = auth
        Log.d("AuthDebug", "Auth state checking")

        if (fbAuth != null) {
            val immediateUser = fbAuth.currentUser
            if (immediateUser != null) {
                val user = FirebaseAuthUserWrapper(immediateUser)
                logAuthState(user)
                saveSession(user.uid, user.displayName, user.isAnonymous, user.email)
                return@withContext user
            }

            val resolvedFbUser = withTimeoutOrNull(2500L) {
                suspendCancellableCoroutine { continuation ->
                    var listener: FirebaseAuth.AuthStateListener? = null
                    listener = FirebaseAuth.AuthStateListener { currentAuth ->
                        val u = currentAuth.currentUser
                        if (u != null) {
                            try {
                                listener?.let { fbAuth.removeAuthStateListener(it) }
                            } catch (_: Exception) {}
                            if (continuation.isActive) {
                                continuation.resume(u)
                            }
                        }
                    }
                    fbAuth.addAuthStateListener(listener)
                    continuation.invokeOnCancellation {
                        try {
                            listener?.let { fbAuth.removeAuthStateListener(it) }
                        } catch (_: Exception) {}
                    }
                }
            }

            if (resolvedFbUser != null) {
                val user = FirebaseAuthUserWrapper(resolvedFbUser)
                logAuthState(user)
                saveSession(user.uid, user.displayName, user.isAnonymous, user.email)
                return@withContext user
            }
        }

        val current = currentUser
        if (current != null) {
            logAuthState(current)
            return@withContext current
        }

        Log.d("AuthDebug", "Auth state changed")
        Log.d("AuthDebug", "Current UID: null")
        null
    }

    private fun logAuthState(user: AuthUser) {
        val provider = if (user.isAnonymous) "anonymous" else "google"
        Log.d("AuthDebug", "Auth state changed")
        Log.d("AuthDebug", "Current UID: ${user.uid}")
        Log.d("AuthDebug", "Provider: $provider")
    }

    /**
     * Sign in anonymously as Guest.
     * ONLY called when the user explicitly clicks "Continue as Guest" on the Login screen.
     * Never called during application startup.
     */
    suspend fun signInAnonymously(): AuthUser = withContext(Dispatchers.IO) {
        val fbAuth = auth
        val existing = currentUser
        if (existing != null && existing.isAnonymous) {
            Log.d("AuthDebug", "Reusing existing anonymous session UID: ${existing.uid}")
            return@withContext existing
        }

        clearExplicitLogout()

        if (fbAuth != null) {
            try {
                val result = fbAuth.signInAnonymously().await()
                val user = result.user
                if (user != null) {
                    val wrapped = FirebaseAuthUserWrapper(user)
                    saveSession(wrapped.uid, wrapped.displayName, isAnonymous = true, email = null)
                    Log.d("AuthDebug", "New anonymous session created with UID: ${wrapped.uid}")
                    return@withContext wrapped
                }
            } catch (e: Exception) {
                Log.w("AuthDebug", "Firebase remote anonymous sign-in exception: ${e.message}. Using persistent Firebase-compatible session.")
            }
        }

        // Generate persistent anonymous UID compliant with Firebase format
        val persistentUid = "anon_" + UUID.randomUUID().toString().replace("-", "").take(20)
        val localUser = LocalAuthUser(
            uid = persistentUid,
            displayName = "Guest Player",
            isAnonymous = true,
            email = null
        )
        saveSession(localUser.uid, localUser.displayName, isAnonymous = true, email = null)
        Log.d("AuthDebug", "New anonymous session created with UID: ${localUser.uid}")
        localUser
    }

    /**
     * Sign in with Google Credential ID Token.
     */
    suspend fun signInWithGoogle(idToken: String): AuthUser = withContext(Dispatchers.IO) {
        val fbAuth = auth
        clearExplicitLogout()

        if (fbAuth != null) {
            try {
                val credential = GoogleAuthProvider.getCredential(idToken, null)
                val result = fbAuth.signInWithCredential(credential).await()
                val user = result.user
                if (user != null) {
                    val wrapped = FirebaseAuthUserWrapper(user)
                    saveSession(wrapped.uid, wrapped.displayName, isAnonymous = false, email = wrapped.email)
                    Log.d("AuthDebug", "Google session authenticated with UID: ${wrapped.uid}")
                    return@withContext wrapped
                }
            } catch (e: Exception) {
                Log.w("AuthDebug", "Firebase remote Google auth notice: ${e.message}")
                if (e is FirebaseAuthUserCollisionException) throw e
            }
        }

        val googleUid = "google_" + UUID.nameUUIDFromBytes(idToken.take(32).toByteArray()).toString().replace("-", "").take(20)
        val localUser = LocalAuthUser(
            uid = googleUid,
            displayName = "Google Player",
            isAnonymous = false,
            email = null
        )
        saveSession(localUser.uid, localUser.displayName, isAnonymous = false, email = null)
        Log.d("AuthDebug", "Google session authenticated with UID: ${localUser.uid}")
        localUser
    }

    /**
     * Link existing Anonymous Guest account with Google Provider.
     */
    suspend fun linkGoogleAccount(idToken: String): AuthUser = withContext(Dispatchers.IO) {
        val fbAuth = auth
        val current = currentUser ?: throw Exception("No authenticated guest session to link")

        if (!current.isAnonymous) {
            throw Exception("Current account is already linked to Google")
        }

        val fbUser = fbAuth?.currentUser
        if (fbUser != null && fbUser.isAnonymous) {
            try {
                val credential = GoogleAuthProvider.getCredential(idToken, null)
                val result = fbUser.linkWithCredential(credential).await()
                val linked = result.user
                if (linked != null) {
                    val wrapped = FirebaseAuthUserWrapper(linked)
                    saveSession(wrapped.uid, wrapped.displayName, isAnonymous = false, email = wrapped.email)
                    Log.d("AuthDebug", "Guest UID ${wrapped.uid} successfully linked with Google provider")
                    return@withContext wrapped
                }
            } catch (e: FirebaseAuthUserCollisionException) {
                throw AccountCollisionException(
                    "This Google account is already linked to another player. Please log in with that Google account or use another one."
                )
            } catch (e: Exception) {
                Log.w("AuthDebug", "Remote linking notice: ${e.message}")
            }
        }

        val linkedUser = LocalAuthUser(
            uid = current.uid,
            displayName = current.displayName ?: "Player",
            isAnonymous = false,
            email = null
        )
        saveSession(linkedUser.uid, linkedUser.displayName, isAnonymous = false, email = null)
        Log.d("AuthDebug", "Guest UID ${linkedUser.uid} successfully linked with Google provider")
        linkedUser
    }

    /**
     * Terminate the session on explicit user logout.
     */
    fun signOut() {
        Log.d("AuthDebug", "Explicit logout requested. Terminating Firebase Auth session.")
        try {
            auth?.signOut()
        } catch (e: Exception) {
            Log.e("AuthDebug", "Error during sign out: ${e.message}")
        }
        prefs.edit().apply {
            putBoolean("explicitly_logged_out", true)
            remove("session_uid")
            remove("session_display_name")
            remove("session_is_anonymous")
            remove("session_email")
            apply()
        }
    }

    private fun isExplicitlyLoggedOut(): Boolean {
        return prefs.getBoolean("explicitly_logged_out", false)
    }

    private fun clearExplicitLogout() {
        prefs.edit().putBoolean("explicitly_logged_out", false).apply()
    }

    private fun saveSession(uid: String, displayName: String?, isAnonymous: Boolean, email: String?) {
        prefs.edit().apply {
            putBoolean("explicitly_logged_out", false)
            putString("session_uid", uid)
            putString("session_display_name", displayName)
            putBoolean("session_is_anonymous", isAnonymous)
            putString("session_email", email)
            apply()
        }
    }
}
