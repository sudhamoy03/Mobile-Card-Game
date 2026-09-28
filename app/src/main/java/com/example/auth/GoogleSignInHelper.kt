package com.example.auth

import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialCustomException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GoogleSignInHelper(private val context: Context) {

    companion object {
        private const val TAG = "GoogleSignInHelper"
    }

    private val credentialManager = CredentialManager.create(context)

    /**
     * Resolves the Web OAuth Client ID from Firebase-generated resources (default_web_client_id)
     * generated from app/google-services.json by the Google Services Gradle plugin.
     * Never returns a fake or hardcoded client ID.
     */
    fun getServerClientId(ctx: Context = context): String? {
        try {
            val resId = ctx.resources.getIdentifier("default_web_client_id", "string", ctx.packageName)
            if (resId != 0) {
                val clientId = ctx.getString(resId).trim()
                if (clientId.isNotEmpty()) {
                    Log.d(TAG, "Resolved serverClientId (default_web_client_id) from resources: $clientId")
                    return clientId
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not resolve default_web_client_id from resources: ${e.message}")
        }
        return null
    }

    /**
     * Launches Google Sign-In using Android Credential Manager.
     *
     * Handles NoCredentialException:
     * - First attempts to retrieve pre-authorized accounts (filterByAuthorizedAccounts = true).
     * - If NoCredentialException is thrown (no previously authorized credential exists),
     *   automatically retries with filterByAuthorizedAccounts = false to display the
     *   system Google account selection sheet.
     * - Compatible with Android 15+ by using the Activity context for window attachment.
     */
    suspend fun getGoogleIdToken(
        activityContext: Context? = null,
        serverClientId: String? = null
    ): Result<String> = withContext(Dispatchers.Main) {
        val targetContext = activityContext ?: context
        val clientId = serverClientId?.takeIf { it.isNotBlank() } ?: getServerClientId(targetContext)

        if (clientId.isNullOrBlank()) {
            val errorMsg = "Google Sign-In failed: Web client ID (default_web_client_id) not found in Firebase configuration. " +
                "Please verify that app/google-services.json is present and contains an oauth_client of type 3."
            Log.e(TAG, "[GoogleSignIn Error] $errorMsg")
            return@withContext Result.failure(IllegalStateException(errorMsg))
        }

        Log.d(TAG, "Initiating Google Sign-In with serverClientId=$clientId, context=${targetContext.javaClass.simpleName}")

        // Step 1: Attempt with filterByAuthorizedAccounts = true (seamless if already authorized)
        try {
            val initialRequest = createGetCredentialRequest(clientId, filterByAuthorizedAccounts = true)
            Log.d(TAG, "Requesting credential with filterByAuthorizedAccounts = true...")
            val response = credentialManager.getCredential(targetContext, initialRequest)
            return@withContext extractIdTokenFromResponse(response)
        } catch (e: GetCredentialCancellationException) {
            Log.w(TAG, "[GoogleSignIn Cancelled] User cancelled Google Sign-In: ${e.message}")
            return@withContext Result.failure(Exception("Sign-in cancelled by user"))
        } catch (e: NoCredentialException) {
            Log.i(TAG, "[GoogleSignIn Notice] No pre-authorized credentials found (NoCredentialException: ${e.message}). " +
                "Falling back to full account selection (filterByAuthorizedAccounts = false)...")
            return@withContext requestFullAccountSelection(targetContext, clientId)
        } catch (e: GetCredentialCustomException) {
            val isNoCred = e.type.contains("NoCredential", ignoreCase = true) ||
                e.message?.contains("No credentials", ignoreCase = true) == true
            if (isNoCred) {
                Log.i(TAG, "[GoogleSignIn Notice] Custom exception indicates no credentials (${e.type}: ${e.message}). " +
                    "Retrying with filterByAuthorizedAccounts = false...")
                return@withContext requestFullAccountSelection(targetContext, clientId)
            }
            Log.e(TAG, "[GoogleSignIn Error] CredentialManager custom exception: type=${e.type}, message=${e.message}", e)
            return@withContext Result.failure(Exception("Google Sign-In failed: ${e.message ?: e.type}"))
        } catch (e: GetCredentialException) {
            if (e.message?.contains("No credentials", ignoreCase = true) == true) {
                Log.i(TAG, "[GoogleSignIn Notice] Credential exception indicated no credentials (${e.message}). " +
                    "Retrying with filterByAuthorizedAccounts = false...")
                return@withContext requestFullAccountSelection(targetContext, clientId)
            }
            Log.e(TAG, "[GoogleSignIn Error] CredentialManager error: class=${e.javaClass.simpleName}, message=${e.message}", e)
            return@withContext Result.failure(Exception("Google Sign-In failed: ${e.message}"))
        } catch (e: Exception) {
            Log.e(TAG, "[GoogleSignIn Error] Unexpected error during Google Sign-In: ${e.message}", e)
            return@withContext Result.failure(e)
        }
    }

    /**
     * Fallback/Full account selection: requests CredentialManager with filterByAuthorizedAccounts = false.
     * Shows standard Google Account chooser dialog on Android 14/15+.
     */
    private suspend fun requestFullAccountSelection(
        targetContext: Context,
        clientId: String
    ): Result<String> {
        val fullRequest = createGetCredentialRequest(clientId, filterByAuthorizedAccounts = false)
        return try {
            Log.d(TAG, "Requesting CredentialManager.getCredential with filterByAuthorizedAccounts = false (Account Chooser UI)...")
            val response = credentialManager.getCredential(targetContext, fullRequest)
            extractIdTokenFromResponse(response)
        } catch (e: GetCredentialCancellationException) {
            Log.w(TAG, "[GoogleSignIn Cancelled] User cancelled account selection: ${e.message}")
            Result.failure(Exception("Sign-in cancelled by user"))
        } catch (e: GetCredentialException) {
            Log.e(TAG, "[GoogleSignIn Error] Full account selection failed: class=${e.javaClass.simpleName}, message=${e.message}", e)
            Result.failure(Exception("Google Sign-In failed: ${e.message}"))
        } catch (e: Exception) {
            Log.e(TAG, "[GoogleSignIn Error] Full account selection unexpected failure: ${e.message}", e)
            Result.failure(e)
        }
    }

    private fun createGetCredentialRequest(
        clientId: String,
        filterByAuthorizedAccounts: Boolean
    ): GetCredentialRequest {
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(filterByAuthorizedAccounts)
            .setServerClientId(clientId)
            .setAutoSelectEnabled(filterByAuthorizedAccounts)
            .build()

        return GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()
    }

    private fun extractIdTokenFromResponse(
        response: androidx.credentials.GetCredentialResponse
    ): Result<String> {
        val credential = response.credential
        return if (credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            try {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val idToken = googleIdTokenCredential.idToken
                Log.d(TAG, "Successfully extracted Google ID token (length: ${idToken.length})")
                Result.success(idToken)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse GoogleIdTokenCredential: ${e.message}", e)
                Result.failure(Exception("Failed to parse Google ID token: ${e.message}"))
            }
        } else {
            val unexpectedType = credential.javaClass.simpleName
            Log.e(TAG, "Unexpected credential type returned: $unexpectedType, type=${credential.type}")
            Result.failure(Exception("Unexpected credential type: $unexpectedType"))
        }
    }
}
