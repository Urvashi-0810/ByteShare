package com.byteshare.android.data

import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CredentialManagerCallback
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import java.util.concurrent.Executors

/**
 * Google Sign-In via Credential Manager + Firebase Auth.
 * The database rules require a real Firebase user, so a failed sign-in is reported to the
 * caller instead of continuing with an identity that can't read or write anything.
 */
object AuthRepository {

    private const val TAG = "AuthRepository"

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val executor = Executors.newSingleThreadExecutor()

    val currentUser: FirebaseUser? get() = auth.currentUser
    val currentUserId: String? get() = auth.currentUser?.uid

    fun isSignedIn(): Boolean = auth.currentUser != null

    /** [onResult] gets the user, or null plus a message to show (null message = user cancelled). */
    fun signInWithGoogle(
        context: Context,
        onResult: (FirebaseUser?, String?) -> Unit
    ) {
        val resId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
        if (resId == 0) {
            Log.e(TAG, "default_web_client_id missing: enable Google sign-in and re-download google-services.json")
            onResult(null, "Google sign-in isn't set up for this build.")
            return
        }
        val webClientId = context.getString(resId)

        val googleIdOption = GetGoogleIdOption.Builder()
            .setServerClientId(webClientId)
            .setFilterByAuthorizedAccounts(false)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        CredentialManager.create(context).getCredentialAsync(
            context,
            request,
            null,
            executor,
            object : CredentialManagerCallback<GetCredentialResponse, GetCredentialException> {
                override fun onResult(result: GetCredentialResponse) {
                    handleCredential(result, context, onResult)
                }

                override fun onError(e: GetCredentialException) {
                    Log.w(TAG, "Credential Manager failed: ${e.type}", e)
                    val message = when (e) {
                        is GetCredentialCancellationException -> null
                        is NoCredentialException -> "No Google account found on this device."
                        else -> "Google sign-in failed: ${e.localizedMessage}"
                    }
                    postToMain(context) { onResult(null, message) }
                }
            }
        )
    }

    private fun handleCredential(
        response: GetCredentialResponse,
        context: Context,
        onResult: (FirebaseUser?, String?) -> Unit
    ) {
        val credential = response.credential
        if (credential !is CustomCredential ||
            credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            Log.e(TAG, "Unexpected credential type: ${credential.type}")
            postToMain(context) { onResult(null, "Google sign-in failed. Please try again.") }
            return
        }
        try {
            val googleCred = GoogleIdTokenCredential.createFrom(credential.data)
            val firebaseCred = GoogleAuthProvider.getCredential(googleCred.idToken, null)
            auth.signInWithCredential(firebaseCred)
                .addOnSuccessListener { result ->
                    postToMain(context) { onResult(result.user, null) }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Firebase sign-in with Google failed", e)
                    postToMain(context) { onResult(null, "Google sign-in failed: ${e.localizedMessage}") }
                }
        } catch (e: Exception) {
            Log.e(TAG, "Invalid Google ID token", e)
            postToMain(context) { onResult(null, "Google sign-in failed. Please try again.") }
        }
    }

    private fun postToMain(context: Context, block: () -> Unit) {
        android.os.Handler(context.mainLooper).post(block)
    }

    fun signOut(context: Context, onComplete: () -> Unit = {}) {
        auth.signOut()

        CredentialManager.create(context).clearCredentialStateAsync(
            androidx.credentials.ClearCredentialStateRequest(),
            null,
            executor,
            object : CredentialManagerCallback<Void?, androidx.credentials.exceptions.ClearCredentialException> {
                override fun onResult(result: Void?) {
                    postToMain(context) { onComplete() }
                }

                override fun onError(e: androidx.credentials.exceptions.ClearCredentialException) {
                    Log.w(TAG, "clearCredentialState failed", e)
                    postToMain(context) { onComplete() }
                }
            }
        )
    }
}
