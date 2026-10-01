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
 * Google Sign-In via Credential Manager + Firebase Auth with Guest Session Fallback.
 */
object AuthRepository {

    private const val TAG = "AuthRepository"
    private const val PREF_NAME = "byteshare_auth_prefs"
    private const val KEY_GUEST_UID = "guest_user_uid"

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val executor = Executors.newSingleThreadExecutor()

    private var localGuestUid: String? = null

    val currentUser: FirebaseUser? get() = auth.currentUser
    val currentUserId: String? get() = auth.currentUser?.uid ?: localGuestUid

    fun isSignedIn(): Boolean = auth.currentUser != null || !localGuestUid.isNullOrBlank()

    /**
     * Guest / Anonymous Sign-In with local session fallback if Firebase Auth is disabled in console.
     */
    fun signInAnonymously(
        context: Context,
        onResult: (FirebaseUser?, String?) -> Unit
    ) {
        auth.signInAnonymously()
            .addOnSuccessListener { result ->
                Log.d(TAG, "Anonymous sign-in succeeded: ${result.user?.uid}")
                localGuestUid = result.user?.uid
                saveGuestUid(context, localGuestUid)
                postToMain(context) { onResult(result.user, null) }
            }
            .addOnFailureListener { firebaseErr ->
                Log.w(TAG, "Firebase Auth failed (${firebaseErr.message}). Enabling local guest mode.", firebaseErr)
                // Fallback to local guest mode so user is never blocked by Firebase Console settings!
                if (localGuestUid == null) {
                    localGuestUid = getOrCreateGuestUid(context)
                }
                postToMain(context) { onResult(auth.currentUser, null) }
            }
    }

    /**
     * Launches Google Sign-In, and if device has no saved credentials or Firebase Auth is restricted,
     * seamlessly falls back to Guest Session.
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
                        localGuestUid = result.user?.uid
                        postToMain(context) { onResult(result.user, null) }
                    }
                    .addOnFailureListener { e ->
                        Log.w(TAG, "Firebase sign-in failed, falling back to local guest mode", e)
                        signInAnonymously(context, onResult)
                    }
            } catch (e: Exception) {
                Log.w(TAG, "Invalid Google ID token, falling back to guest mode", e)
                signInAnonymously(context, onResult)
            }
        } else {
            signInAnonymously(context, onResult)
        }
    }

    private fun getOrCreateGuestUid(context: Context): String {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        var uid = prefs.getString(KEY_GUEST_UID, null)
        if (uid.isNullOrBlank()) {
            uid = "guest_${System.currentTimeMillis()}"
            prefs.edit().putString(KEY_GUEST_UID, uid).apply()
        }
        return uid
    }

    private fun saveGuestUid(context: Context, uid: String?) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_GUEST_UID, uid).apply()
    }

    private fun postToMain(context: Context, block: () -> Unit) {
        android.os.Handler(context.mainLooper).post(block)
    }

    fun signOut(context: Context, onComplete: () -> Unit = {}) {
        auth.signOut()
        localGuestUid = null
        saveGuestUid(context, null)

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