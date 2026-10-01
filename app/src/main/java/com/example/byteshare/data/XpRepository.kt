package com.example.byteshare.data

import android.content.Context
import android.util.Log
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

object XpRepository {

    private const val TAG = "XpRepository"
    private const val DB_URL = "https://test-e06f1-default-rtdb.firebaseio.com"

    private val db: FirebaseDatabase by lazy { FirebaseDatabase.getInstance(DB_URL) }

    private var localXp = 450
    private var localStreak = 5

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

    /**
     * Add XP (from Ad watch, Rank, or Challenge completion) and sync to Firebase
     */
    fun addXp(context: Context, amount: Int, reason: String, onComplete: (newXp: Int) -> Unit = {}) {
        localXp += amount
        val uid = AuthRepository.currentUserId
        if (uid != null) {
            try {
                db.getReference("users").child(uid).child("xp").setValue(localXp)
                db.getReference("users").child(uid).child("streak").setValue(localStreak)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to sync XP to Firebase", e)
            }
        }
        Log.d(TAG, "Earned +$amount XP ($reason)! Total: $localXp XP (Level ${getLevel()})")
        onComplete(localXp)
    }

    /**
     * Listen for real-time XP & Streak changes from Firebase
     */
    fun listenForXp(onUpdate: (xp: Int, streak: Int, level: Int) -> Unit): ValueEventListener? {
        val uid = AuthRepository.currentUserId ?: return null
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val dbXp = snapshot.child("xp").getValue(Int::class.java) ?: localXp
                val dbStreak = snapshot.child("streak").getValue(Int::class.java) ?: localStreak
                localXp = dbXp
                localStreak = dbStreak
                onUpdate(localXp, localStreak, getLevel(localXp))
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "XP listener cancelled", error.toException())
            }
        }
        db.getReference("users").child(uid).addValueEventListener(listener)
        return listener
    }
}