package com.example.byteshare.data

import android.content.Context
import android.util.Log
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.MutableData
import com.google.firebase.database.Transaction
import com.google.firebase.database.ValueEventListener
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Firebase-backed XP, streak, and level tracking.
 *
 * Database structure:
 *   xp/{uid} -> { totalXp, streak, lastActiveDay, updatedAt }
 *
 * - XP is incremented atomically via a Firebase transaction.
 * - Streak is auto-computed on each activity based on lastActiveDay.
 * - A real-time listener keeps the local cache and UI in sync.
 */
object XpRepository {

    private const val TAG = "XpRepository"
    private const val DB_URL = "https://test-e06f1-default-rtdb.firebaseio.com"

    private val db: FirebaseDatabase by lazy { FirebaseDatabase.getInstance(DB_URL) }
    private val xpRef: DatabaseReference by lazy { db.getReference("xp") }

    // Local cache — seeded from Firebase on first listen/fetch
    private var localXp = 0
    private var localStreak = 0
    private var initialFetchDone = false

    fun getLevel(xp: Int = localXp): Int = (xp / 200) + 1

    fun getLevelTitle(level: Int = getLevel()): String = when (level) {
        1 -> "Novice Scroller"
        2 -> "Focus Apprentice"
        3 -> "Focus Master"
        4 -> "Mindful Monk"
        else -> "Digital Zen Grandmaster"
    }

    fun getXp(): Int = localXp
    fun getStreak(): Int = localStreak

    private fun todayKey(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date())

    private fun yesterdayKey(): String {
        val cal = java.util.Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        cal.add(java.util.Calendar.DAY_OF_YEAR, -1)
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(cal.time)
    }

    /**
     * Fetches XP once from Firebase to seed the local cache.
     * Call on app startup or sign-in.
     */
    fun fetchOnce(onResult: (xp: Int, streak: Int, level: Int) -> Unit = { _, _, _ -> }) {
        val uid = AuthRepository.currentUserId ?: return onResult(localXp, localStreak, getLevel())
        xpRef.child(uid).get()
            .addOnSuccessListener { snapshot ->
                localXp = snapshot.child("totalXp").getValue(Int::class.java) ?: 0
                localStreak = snapshot.child("streak").getValue(Int::class.java) ?: 0
                initialFetchDone = true
                onResult(localXp, localStreak, getLevel())
            }
            .addOnFailureListener { error ->
                Log.e(TAG, "Failed to fetch XP", error)
                onResult(localXp, localStreak, getLevel())
            }
    }

    /**
     * Add XP atomically via Firebase transaction.
     * Also updates the streak based on lastActiveDay.
     */
    fun addXp(context: Context, amount: Int, reason: String, onComplete: (newXp: Int) -> Unit = {}) {
        val uid = AuthRepository.currentUserId
        if (uid == null) {
            localXp += amount
            onComplete(localXp)
            return
        }

        val today = todayKey()
        val yesterday = yesterdayKey()

        xpRef.child(uid).runTransaction(object : Transaction.Handler {
            override fun doTransaction(currentData: MutableData): Transaction.Result {
                val currentXp = currentData.child("totalXp").getValue(Int::class.java) ?: 0
                val currentStreak = currentData.child("streak").getValue(Int::class.java) ?: 0
                val lastActive = currentData.child("lastActiveDay").getValue(String::class.java) ?: ""

                val newXp = currentXp + amount
                val newStreak = when (lastActive) {
                    today -> currentStreak           // Already active today, keep streak
                    yesterday -> currentStreak + 1   // Consecutive day, bump streak
                    else -> 1                        // Streak broken, reset to 1
                }

                currentData.child("totalXp").value = newXp
                currentData.child("streak").value = newStreak
                currentData.child("lastActiveDay").value = today
                currentData.child("updatedAt").value = System.currentTimeMillis()
                return Transaction.success(currentData)
            }

            override fun onComplete(
                error: DatabaseError?,
                committed: Boolean,
                currentData: DataSnapshot?
            ) {
                if (error != null) {
                    Log.e(TAG, "XP transaction failed: ${error.message}")
                    // Fallback to local increment
                    localXp += amount
                    onComplete(localXp)
                    return
                }
                if (committed && currentData != null) {
                    localXp = currentData.child("totalXp").getValue(Int::class.java) ?: localXp
                    localStreak = currentData.child("streak").getValue(Int::class.java) ?: localStreak
                }
                Log.d(TAG, "Earned +$amount XP ($reason)! Total: $localXp XP (Level ${getLevel()})")
                onComplete(localXp)
            }
        }, false)
    }

    /**
     * Record a daily activity (app open, usage sync) to maintain the streak
     * without granting XP. Safe to call multiple times per day.
     */
    fun recordDailyActivity() {
        val uid = AuthRepository.currentUserId ?: return
        val today = todayKey()
        val yesterday = yesterdayKey()

        xpRef.child(uid).runTransaction(object : Transaction.Handler {
            override fun doTransaction(currentData: MutableData): Transaction.Result {
                val currentStreak = currentData.child("streak").getValue(Int::class.java) ?: 0
                val lastActive = currentData.child("lastActiveDay").getValue(String::class.java) ?: ""

                if (lastActive == today) return Transaction.success(currentData) // Already done today

                val newStreak = when (lastActive) {
                    yesterday -> currentStreak + 1
                    else -> 1
                }

                currentData.child("streak").value = newStreak
                currentData.child("lastActiveDay").value = today
                currentData.child("updatedAt").value = System.currentTimeMillis()
                // Ensure totalXp field exists even if 0
                if (currentData.child("totalXp").getValue(Int::class.java) == null) {
                    currentData.child("totalXp").value = 0
                }
                return Transaction.success(currentData)
            }

            override fun onComplete(error: DatabaseError?, committed: Boolean, currentData: DataSnapshot?) {
                if (error != null) {
                    Log.e(TAG, "Streak update failed: ${error.message}")
                    return
                }
                if (committed && currentData != null) {
                    localXp = currentData.child("totalXp").getValue(Int::class.java) ?: localXp
                    localStreak = currentData.child("streak").getValue(Int::class.java) ?: localStreak
                }
            }
        }, false)
    }

    /**
     * Listen for real-time XP & Streak changes from Firebase.
     * Returns the listener so callers can clean it up.
     */
    fun listenForXp(onUpdate: (xp: Int, streak: Int, level: Int) -> Unit): ValueEventListener? {
        val uid = AuthRepository.currentUserId ?: return null
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                localXp = snapshot.child("totalXp").getValue(Int::class.java) ?: 0
                localStreak = snapshot.child("streak").getValue(Int::class.java) ?: 0
                initialFetchDone = true
                onUpdate(localXp, localStreak, getLevel(localXp))
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "XP listener cancelled", error.toException())
            }
        }
        xpRef.child(uid).addValueEventListener(listener)
        return listener
    }

    /**
     * Remove a previously attached XP listener.
     */
    fun removeListener(listener: ValueEventListener) {
        val uid = AuthRepository.currentUserId ?: return
        try {
            xpRef.child(uid).removeEventListener(listener)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to remove XP listener", e)
        }
    }
}