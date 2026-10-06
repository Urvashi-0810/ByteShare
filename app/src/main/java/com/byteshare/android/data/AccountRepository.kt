package com.byteshare.android.data

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.work.WorkManager
import com.byteshare.android.BuildConfig
import com.google.firebase.auth.FirebaseAuth
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Account deletion (Play Store user-data policy).
 *
 * The backend deletes the user's database records and Firebase Auth account with admin
 * privileges, so deletion doesn't depend on a recent sign-in or on per-path client rules.
 */
object AccountRepository {

    private const val TAG = "AccountRepository"
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    /** [onResult] receives null on success, or a user-facing error message. */
    fun deleteAccount(context: Context, onResult: (String?) -> Unit) {
        val appContext = context.applicationContext
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            // Local guest session: nothing stored server-side
            clearLocalData(appContext) { onResult(null) }
            return
        }

        val baseUrl = BuildConfig.PAYMENTS_API_BASE_URL.trimEnd('/')
        if (!baseUrl.startsWith("https://")) {
            onResult("Account deletion is unavailable right now. Please try again later.")
            return
        }

        user.getIdToken(true)
            .addOnSuccessListener { tokenResult ->
                val idToken = tokenResult.token
                if (idToken.isNullOrBlank()) {
                    onResult("Could not verify your sign-in. Please try again.")
                    return@addOnSuccessListener
                }
                executor.execute {
                    val result = runCatching { requestDeletion(baseUrl, idToken) }
                    mainHandler.post {
                        result
                            .onSuccess { clearLocalData(appContext) { onResult(null) } }
                            .onFailure { error ->
                                Log.e(TAG, "Account deletion failed", error)
                                onResult(error.message ?: "Couldn't delete your account. Please try again.")
                            }
                    }
                }
            }
            .addOnFailureListener { error ->
                Log.e(TAG, "Failed to get Firebase ID token", error)
                onResult("Could not verify your sign-in. Please try again.")
            }
    }

    private fun requestDeletion(baseUrl: String, idToken: String) {
        val connection = URL("$baseUrl/account/delete").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 15000
            // Render free instances can take a while to wake up
            connection.readTimeout = 60000
            connection.setRequestProperty("Authorization", "Bearer $idToken")
            connection.setRequestProperty("Accept", "application/json")
            connection.doOutput = true
            connection.outputStream.use { it.write("{}".toByteArray()) }

            val status = connection.responseCode
            if (status !in 200..299) {
                val body = connection.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                val detail = runCatching { JSONObject(body).optString("detail") }.getOrNull()
                throw IllegalStateException(
                    detail?.takeIf { it.isNotBlank() } ?: "Couldn't delete your account (HTTP $status)."
                )
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun clearLocalData(context: Context, onDone: () -> Unit) {
        WorkManager.getInstance(context).cancelAllWork()
        context.getSharedPreferences("ByteSharePrefs", Context.MODE_PRIVATE).edit().clear().apply()
        // signOut also clears the stored guest uid and Credential Manager state
        AuthRepository.signOut(context, onDone)
    }
}
