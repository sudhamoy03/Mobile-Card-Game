package com.example.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GoogleSignInHelper(private val context: Context) {

    private val credentialManager = CredentialManager.create(context)

    /**
     * Launches Google Sign-In using Android Credential Manager
     * Returns Google ID token string on success
     */
    suspend fun getGoogleIdToken(serverClientId: String?): Result<String> = withContext(Dispatchers.Main) {
        try {
            val clientId = serverClientId?.takeIf { it.isNotBlank() }
                ?: "dummy-client-id.apps.googleusercontent.com"

            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(clientId)
                .setAutoSelectEnabled(false)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val response = credentialManager.getCredential(context, request)
            val credential = response.credential

            if (credential is androidx.credentials.CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                Result.success(googleIdTokenCredential.idToken)
            } else {
                Result.failure(Exception("Unexpected credential type returned"))
            }
        } catch (e: GetCredentialCancellationException) {
            Result.failure(Exception("Sign-in cancelled by user"))
        } catch (e: GetCredentialException) {
            Result.failure(Exception("Google Sign-In failed: ${e.message}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
