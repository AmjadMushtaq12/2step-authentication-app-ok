package com.example.secureauth2fa.data.auth

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.example.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

sealed class GoogleSignInResult {
    data class Success(val idToken: String, val email: String, val displayName: String) : GoogleSignInResult()
    object Cancelled : GoogleSignInResult()
    object NeedAccountSelection : GoogleSignInResult()
    data class Error(val message: String) : GoogleSignInResult()
}

class GoogleAuthManager(private val context: Context) {

    private val TAG = "GoogleAuthManager"
    private val credentialManager: CredentialManager by lazy {
        CredentialManager.create(context)
    }

    private fun getWebClientId(): String {
        return try {
            context.getString(R.string.default_web_client_id)
        } catch (e: Exception) {
            "102938475610-localdev.apps.googleusercontent.com"
        }
    }

    suspend fun requestGoogleSignIn(activity: Activity): GoogleSignInResult {
        val serverClientId = getWebClientId()
        
        try {
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(serverClientId)
                .setAutoSelectEnabled(false)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val response = credentialManager.getCredential(
                context = activity,
                request = request
            )

            val credential = response.credential
            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val idToken = googleIdTokenCredential.idToken
                val email = googleIdTokenCredential.id
                val displayName = googleIdTokenCredential.displayName ?: email.substringBefore("@")

                Log.d(TAG, "Google Sign-In successful via Credential Manager for $email")
                return GoogleSignInResult.Success(
                    idToken = idToken,
                    email = email,
                    displayName = displayName
                )
            } else {
                Log.w(TAG, "Unknown credential type: ${credential.type}")
                return GoogleSignInResult.NeedAccountSelection
            }
        } catch (e: GetCredentialCancellationException) {
            Log.d(TAG, "User cancelled Google credential picker.")
            return GoogleSignInResult.Cancelled
        } catch (e: NoCredentialException) {
            Log.d(TAG, "No Google accounts available via Credential Manager: ${e.message}")
            return GoogleSignInResult.NeedAccountSelection
        } catch (e: GetCredentialException) {
            Log.w(TAG, "Credential Manager GetCredentialException: ${e.message}")
            // On emulator / dev without registered OAuth web client ID, provide seamless fallback account selector
            return GoogleSignInResult.NeedAccountSelection
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error in Google Sign-In: ${e.message}", e)
            return GoogleSignInResult.NeedAccountSelection
        }
    }

    companion object {
        fun findActivity(context: Context): Activity? {
            var currentContext = context
            while (currentContext is ContextWrapper) {
                if (currentContext is Activity) {
                    return currentContext
                }
                currentContext = currentContext.baseContext
            }
            return null
        }
    }
}
