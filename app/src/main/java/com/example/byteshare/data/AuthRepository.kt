package com.example.byteshare.data

import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CredentialManagerCallback
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import java.util.concurrent.Executors

/**
 * Google Sign-In via Credential Manager + Firebase Auth with Guest Fallback.
 */
object AuthRepository {

    private const val TAG = "AuthRepository"

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val executor = Executors.newSingleThreadExecutor()

    val currentUser: FirebaseUser? get() = auth.currentUser
    val currentUserId: String? get() = auth.currentUser?.uid

    fun isSignedIn(): Boolean = auth.currentUser != null

    /**
     * Guest / Anonymous Sign-In for 1-tap instant entry.
     */
    fun signInAnonymously(
        context: Context,
        onResult: (FirebaseUser?, String?) -> Unit
    ) {
        auth.signInAnonymously()
            .addOnSuccessListener { result ->
                Log.d(TAG, "Anonymous sign-in succeeded: ${result.user?.uid}")
                postToMain(context) { onResult(result.user, null) }
            }
            .addOnFailureListener { firebaseErr ->
                Log.e(TAG, "Anonymous sign-in failed", firebaseErr)
                postToMain(context) { onResult(null, firebaseErr.message ?: "Sign-in failed") }
            }
    }

    /**
     * Launches Google Sign-In, and if device has no saved credentials,
     * seamlessly falls back to Firebase Anonymous authentication.
     */
    fun signInWithGoogle(
        context: Context,
        onResult: (FirebaseUser?, String?) -> Unit
    ) {
        val resId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
        if (resId == 0) {
            signInAnonymously(context, onResult)
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
                    Log.w(TAG, "Credential Manager failed: ${e.message}. Falling back to Guest Sign-In", e)
                    // If device has no credentials, fallback immediately to Anonymous Sign-In
                    signInAnonymously(context, onResult)
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
        if (credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            try {
                val googleCred = GoogleIdTokenCredential.createFrom(credential.data)
                val firebaseCred = GoogleAuthProvider.getCredential(googleCred.idToken, null)
                auth.signInWithCredential(firebaseCred)
                    .addOnSuccessListener { result ->
                        postToMain(context) { onResult(result.user, null) }
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Firebase sign-in failed, falling back to guest", e)
                        signInAnonymously(context, onResult)
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Invalid Google ID token, falling back to guest", e)
                signInAnonymously(context, onResult)
            }
        } else {
            signInAnonymously(context, onResult)
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