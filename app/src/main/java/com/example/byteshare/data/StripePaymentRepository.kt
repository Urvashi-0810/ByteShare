package com.example.byteshare.data

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.byteshare.BuildConfig
import com.google.firebase.auth.FirebaseAuth
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

data class PaymentIntentInfo(
    val clientSecret: String,
    val publishableKey: String,
    val amountMinor: Long,
    val multiplier: Double,
    val rank: Int
)

object StripePaymentRepository {

    private const val TAG = "StripePaymentRepository"
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    fun createCrewPaymentIntent(
        crewId: String,
        onResult: (PaymentIntentInfo?, String?) -> Unit
    ) {
        val baseUrl = BuildConfig.PAYMENTS_API_BASE_URL.trimEnd('/')
        if (!baseUrl.startsWith("https://")) {
            onResult(null, "Secure payment service URL is not configured.")
            return
        }

        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            onResult(null, "Sign in again before paying.")
            return
        }

        user.getIdToken(false)
            .addOnSuccessListener { tokenResult ->
                val idToken = tokenResult.token
                if (idToken.isNullOrBlank()) {
                    onResult(null, "Could not verify your sign-in. Please try again.")
                    return@addOnSuccessListener
                }
                executor.execute {
                    val result = runCatching {
                        requestPaymentIntent(baseUrl, idToken, crewId)
                    }
                    mainHandler.post {
                        result.onSuccess { onResult(it, null) }
                            .onFailure { error ->
                                Log.e(TAG, "PaymentIntent request failed", error)
                                onResult(null, error.message ?: "Couldn't prepare secure checkout.")
                            }
                    }
                }
            }
            .addOnFailureListener { error ->
                Log.e(TAG, "Failed to get Firebase ID token", error)
                onResult(null, "Could not verify your sign-in. Please try again.")
            }
    }

    private fun requestPaymentIntent(
        baseUrl: String,
        idToken: String,
        crewId: String
    ): PaymentIntentInfo {
        val connection = (URL("$baseUrl/payments/create-intent").openConnection() as HttpURLConnection)
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 15000
            connection.readTimeout = 20000
            connection.doOutput = true
            connection.setRequestProperty("Authorization", "Bearer $idToken")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "application/json")
            connection.outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
                writer.write(JSONObject().put("crewId", crewId).toString())
            }

            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            val json = runCatching { JSONObject(body) }.getOrNull()
            if (status !in 200..299) {
                throw IllegalStateException(
                    json?.optString("detail")?.takeIf { it.isNotBlank() }
                        ?: "Secure checkout couldn't be prepared (HTTP $status)."
                )
            }

            val response = json ?: throw IllegalStateException("Payment service returned invalid data.")
            return PaymentIntentInfo(
                clientSecret = response.getString("clientSecret"),
                publishableKey = response.getString("publishableKey"),
                amountMinor = response.getLong("amountMinor"),
                multiplier = response.getDouble("multiplier"),
                rank = response.getInt("rank")
            )
        } finally {
            connection.disconnect()
        }
    }
}
