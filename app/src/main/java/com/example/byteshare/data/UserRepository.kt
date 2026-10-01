package com.example.byteshare.data

import android.content.Context
import android.util.Log
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.MutableData
import com.google.firebase.database.Transaction
import com.google.firebase.database.ValueEventListener
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Profile + daily usage sync so friends can see each other on the leaderboard.
 *
 * Database structure:
 *   users/{uid} -> { displayName, email, photoUrl, updatedAt }
 *   emailIndex/{encodedEmail} -> uid   (contact discovery by email)
 *   usageDaily/{uid}/{yyyy-MM-dd} -> { raw, social, stream, neutral, productive }
 *   usageRolling/{uid} -> seven local calendar days of usage totals and window bounds
 */
object UserRepository {

    private const val TAG = "UserRepository"
    private const val DB_URL = "https://test-e06f1-default-rtdb.firebaseio.com"
    private const val MAX_SCORE_AGE_MS = 24L * 60 * 60 * 1000

    private val db: FirebaseDatabase by lazy { FirebaseDatabase.getInstance(DB_URL) }
    private val usersRef: DatabaseReference by lazy { db.getReference("users") }
    private val usageRef: DatabaseReference by lazy { db.getReference("usageDaily") }
    private val rollingUsageRef: DatabaseReference by lazy { db.getReference("usageRolling") }

    // Firebase keys can't contain '.'
    fun encodeEmail(email: String): String = email.lowercase().trim().replace(".", ",")

    fun todayKey(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date())

    /** Uploads own profile + today's usage totals. Call on app open. */
    fun syncProfileAndUsage(context: Context, onComplete: (Boolean) -> Unit = {}) {
        val user = AuthRepository.currentUser ?: run {
            onComplete(false)
            return
        }
        val uid = user.uid

        try {
            // Per-field paths so xp/streak stored under users/{uid} aren't overwritten.
            val updates = mutableMapOf<String, Any>(
                "/users/$uid/displayName" to (user.displayName ?: "Anonymous"),
                "/users/$uid/email" to (user.email ?: ""),
                "/users/$uid/photoUrl" to (user.photoUrl?.toString() ?: ""),
                "/users/$uid/updatedAt" to System.currentTimeMillis()
            )
            user.email?.let { updates["/emailIndex/${encodeEmail(it)}"] = uid }

            if (UsageStatsCollector.hasPermission(context)) {
                val usage = UsageStatsCollector.collectTodayUsage(context)
                val windowEnd = System.currentTimeMillis()
                val rollingUsage = UsageStatsCollector.collectRollingSevenDayUsage(context, windowEnd)
                val totals = mapOf(
                    "raw" to usage.sumOf { it.minutes },
                    "social" to usage.filter { it.category == "social" }.sumOf { it.minutes },
                    "stream" to usage.filter { it.category == "stream" }.sumOf { it.minutes },
                    "neutral" to usage.filter { it.category == "neutral" }.sumOf { it.minutes },
                    "productive" to usage.filter { it.category == "productive" }.sumOf { it.minutes },
                    "updatedAt" to System.currentTimeMillis()
                )
                updates["/usageDaily/$uid/${todayKey()}"] = totals

                updates["/usageRolling/$uid"] = mapOf(
                    "windowStart" to UsageStatsCollector.rollingSevenDayStart(windowEnd),
                    "windowEnd" to windowEnd,
                    "raw" to rollingUsage.sumOf { it.minutes },
                    "social" to rollingUsage.filter { it.category == "social" }.sumOf { it.minutes },
                    "stream" to rollingUsage.filter { it.category == "stream" }.sumOf { it.minutes },
                    "neutral" to rollingUsage.filter { it.category == "neutral" }.sumOf { it.minutes },
                    "productive" to rollingUsage.filter { it.category == "productive" }.sumOf { it.minutes },
                    "updatedAt" to windowEnd
                )
            }

            db.reference.updateChildren(updates)
                .addOnSuccessListener { onComplete(true) }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Profile/usage sync failed", e)
                    onComplete(false)
                }
        } catch (e: Exception) {
            Log.e(TAG, "Exception syncing profile", e)
            onComplete(false)
        }
    }

    fun lookupUidByEmail(email: String, onResult: (String?) -> Unit) {
        db.getReference("emailIndex").child(encodeEmail(email)).get()
            .addOnSuccessListener { onResult(it.getValue(String::class.java)) }
            .addOnFailureListener { onResult(null) }
    }

    /** Fetches profile + that user's latest usage snapshot (daily or rolling 7-day). */
    fun fetchFriendEntry(uid: String, useRolling: Boolean = false, onResult: (FriendEntry?) -> Unit) {
        usersRef.child(uid).get()
            .addOnSuccessListener { profileSnap ->
                val name = profileSnap.child("displayName").getValue(String::class.java)
                if (name == null) {
                    onResult(null)
                    return@addOnSuccessListener
                }
                val email = profileSnap.child("email").getValue(String::class.java).orEmpty()
                val streak = profileSnap.child("streak").getValue(Int::class.java) ?: 0
                
                val ref = if (useRolling) rollingUsageRef.child(uid) else usageRef.child(uid).child(todayKey())
                ref.get()
                    .addOnSuccessListener { usageSnap ->
                        fun mins(key: String) =
                            usageSnap.child(key).getValue(Double::class.java) ?: 0.0
                        val weighted = mins("social") * 2.0 + mins("stream") * 1.5 +
                                mins("neutral") * 1.0 + mins("productive") * 0.5
                        val updatedAt = usageSnap.child("updatedAt")
                            .getValue(Long::class.java) ?: 0L
                        val scoreAge = System.currentTimeMillis() - updatedAt
                        onResult(
                            FriendEntry(
                                uid = uid,
                                name = name,
                                email = email,
                                streak = streak,
                                rawMinutes = mins("raw"),
                                weightedMinutes = weighted,
                                socialMinutes = mins("social"),
                                streamMinutes = mins("stream"),
                                neutralMinutes = mins("neutral"),
                                productiveMinutes = mins("productive"),
                                usageAvailable = usageSnap.exists() && updatedAt > 0L &&
                                    scoreAge in 0..MAX_SCORE_AGE_MS,
                                usageWindowEnd = updatedAt
                            )
                        )
                    }
                    .addOnFailureListener {
                        onResult(
                            FriendEntry(
                                uid = uid,
                                name = name,
                                email = email,
                                streak = streak,
                                rawMinutes = 0.0,
                                weightedMinutes = 0.0,
                                usageAvailable = false,
                                usageWindowEnd = 0L
                            )
                        )
                    }
            }
            .addOnFailureListener {
                Log.e(TAG, "Failed to fetch profile for $uid", it)
                onResult(null)
            }
    }

    fun sendNudge(
        fromUid: String,
        toUid: String,
        fromName: String,
        message: String,
        onResult: (NudgeResult) -> Unit
    ) {
        val dailyRef = db.getReference("nudgesReceived")
            .child(toUid)
            .child(todayKey())
            .child(fromUid)
        val messageId = dailyRef.child("messages").push().key
        if (messageId == null) {
            onResult(NudgeResult.FAILED)
            return
        }

        dailyRef.runTransaction(object : Transaction.Handler {
            override fun doTransaction(currentData: MutableData): Transaction.Result {
                val sentCount = currentData.child("count").getValue(Long::class.java)?.toInt() ?: 0
                if (sentCount >= 2) return Transaction.abort()

                currentData.child("count").value = sentCount + 1
                currentData.child("senderUid").value = fromUid
                currentData.child("senderName").value = fromName
                currentData.child("messages").child(messageId).child("text").value = message
                currentData.child("messages").child(messageId).child("sentAt").value =
                    System.currentTimeMillis()
                return Transaction.success(currentData)
            }

            override fun onComplete(
                error: com.google.firebase.database.DatabaseError?,
                committed: Boolean,
                currentData: com.google.firebase.database.DataSnapshot?
            ) {
                onResult(
                    when {
                        error != null -> NudgeResult.FAILED
                        committed -> NudgeResult.SENT
                        else -> NudgeResult.LIMIT_REACHED
                    }
                )
            }
        }, false)
    }

    fun listenForReceivedNudges(
        uid: String,
        dayKey: String,
        onResult: (List<ReceivedNudge>) -> Unit
    ): ValueEventListener {
        val inboxRef = db.getReference("nudgesReceived").child(uid).child(dayKey)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val nudges = snapshot.children.flatMap { senderSnapshot ->
                    val senderName = senderSnapshot.child("senderName")
                        .getValue(String::class.java) ?: "A friend"
                    senderSnapshot.child("messages").children.mapNotNull { messageSnapshot ->
                        val text = messageSnapshot.child("text").getValue(String::class.java)
                            ?: return@mapNotNull null
                        val sentAt = messageSnapshot.child("sentAt").getValue(Long::class.java) ?: 0L
                        ReceivedNudge(senderName, text, sentAt)
                    }
                }.sortedByDescending { it.sentAt }.take(3)
                onResult(nudges)
            }

            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                Log.e(TAG, "Failed to load received nudges", error.toException())
            }
        }
        inboxRef.addValueEventListener(listener)
        return listener
    }

    fun removeReceivedNudgeListener(uid: String, dayKey: String, listener: ValueEventListener) {
        db.getReference("nudgesReceived").child(uid).child(dayKey)
            .removeEventListener(listener)
    }
}

enum class NudgeResult { SENT, LIMIT_REACHED, FAILED }

data class ReceivedNudge(val senderName: String, val message: String, val sentAt: Long)

data class FriendEntry(
    val uid: String,
    val name: String,
    val rawMinutes: Double,
    val weightedMinutes: Double,
    val socialMinutes: Double = 0.0,
    val streamMinutes: Double = 0.0,
    val neutralMinutes: Double = 0.0,
    val productiveMinutes: Double = 0.0,
    val usageAvailable: Boolean = true,
    val usageWindowEnd: Long = 0L,
    val email: String = "",
    val streak: Int = 0
)
