package com.example.byteshare.data

import android.content.Context
import android.util.Log
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Profile + daily usage sync so friends can see each other on the leaderboard.
 *
 * Database structure:
 *   users/{uid} -> { displayName, email, photoUrl, updatedAt }
 *   emailIndex/{encodedEmail} -> uid   (contact discovery by email)
 *   usageDaily/{uid}/{yyyy-MM-dd} -> { raw, social, stream, neutral, productive }
 */
object UserRepository {

    private const val TAG = "UserRepository"
    private const val DB_URL = "https://test-e06f1-default-rtdb.firebaseio.com"

    private val db: FirebaseDatabase by lazy { FirebaseDatabase.getInstance(DB_URL) }
    private val usersRef: DatabaseReference by lazy { db.getReference("users") }
    private val usageRef: DatabaseReference by lazy { db.getReference("usageDaily") }

    // Firebase keys can't contain '.'
    fun encodeEmail(email: String): String = email.lowercase().trim().replace(".", ",")

    fun todayKey(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    /** Uploads own profile + today's usage totals. Call on app open. */
    fun syncProfileAndUsage(context: Context) {
        val user = AuthRepository.currentUser ?: return
        val uid = user.uid

        try {
            val profile = mapOf(
                "displayName" to (user.displayName ?: "Anonymous"),
                "email" to (user.email ?: ""),
                "photoUrl" to (user.photoUrl?.toString() ?: ""),
                "updatedAt" to System.currentTimeMillis()
            )

            val updates = mutableMapOf<String, Any>("/users/$uid" to profile)
            user.email?.let { updates["/emailIndex/${encodeEmail(it)}"] = uid }

            if (UsageStatsCollector.hasPermission(context)) {
                val usage = UsageStatsCollector.collectTodayUsage(context)
                val totals = mapOf(
                    "raw" to usage.sumOf { it.minutes },
                    "social" to usage.filter { it.category == "social" }.sumOf { it.minutes },
                    "stream" to usage.filter { it.category == "stream" }.sumOf { it.minutes },
                    "neutral" to usage.filter { it.category == "neutral" }.sumOf { it.minutes },
                    "productive" to usage.filter { it.category == "productive" }.sumOf { it.minutes },
                    "updatedAt" to System.currentTimeMillis()
                )
                updates["/usageDaily/$uid/${todayKey()}"] = totals
            }

            db.reference.updateChildren(updates)
                .addOnFailureListener { e -> Log.e(TAG, "Profile/usage sync failed", e) }
        } catch (e: Exception) {
            Log.e(TAG, "Exception syncing profile", e)
        }
    }

    fun lookupUidByEmail(email: String, onResult: (String?) -> Unit) {
        db.getReference("emailIndex").child(encodeEmail(email)).get()
            .addOnSuccessListener { onResult(it.getValue(String::class.java)) }
            .addOnFailureListener { onResult(null) }
    }

    /** Fetches profile + today's usage for one user. */
    fun fetchFriendEntry(uid: String, onResult: (FriendEntry?) -> Unit) {
        usersRef.child(uid).get()
            .addOnSuccessListener { profileSnap ->
                val name = profileSnap.child("displayName").getValue(String::class.java)
                if (name == null) {
                    onResult(null)
                    return@addOnSuccessListener
                }
                usageRef.child(uid).child(todayKey()).get()
                    .addOnSuccessListener { usageSnap ->
                        fun mins(key: String) =
                            usageSnap.child(key).getValue(Double::class.java) ?: 0.0
                        val weighted = mins("social") * 2.0 + mins("stream") * 1.5 +
                                mins("neutral") * 1.0 + mins("productive") * 0.5
                        onResult(
                            FriendEntry(
                                uid = uid,
                                name = name,
                                rawMinutes = mins("raw"),
                                weightedMinutes = weighted
                            )
                        )
                    }
                    .addOnFailureListener {
                        onResult(FriendEntry(uid = uid, name = name, rawMinutes = 0.0, weightedMinutes = 0.0))
                    }
            }
            .addOnFailureListener {
                Log.e(TAG, "Failed to fetch profile for $uid", it)
                onResult(null)
            }
    }
}

data class FriendEntry(
    val uid: String,
    val name: String,
    val rawMinutes: Double,
    val weightedMinutes: Double
)
