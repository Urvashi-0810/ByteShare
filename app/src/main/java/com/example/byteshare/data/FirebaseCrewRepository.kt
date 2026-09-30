package com.example.byteshare.data

import android.util.Log
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

/**
 * Firebase Realtime Database wrapper for Crew CRUD and real-time listeners.
 *
 * Database structure:
 *   crews/{crewId} -> Crew fields
 *   inviteCodes/{code} -> crewId  (O(1) lookup for join-by-code)
 */
object FirebaseCrewRepository {

    private const val TAG = "FirebaseCrewRepo"
    private const val DB_URL = "https://test-e06f1-default-rtdb.firebaseio.com"

    // Lazy init to avoid crash if Firebase isn't ready at class-load time
    private val db: FirebaseDatabase by lazy {
        FirebaseDatabase.getInstance(DB_URL)
    }
    private val crewsRef: DatabaseReference by lazy { db.getReference("crews") }
    private val inviteCodesRef: DatabaseReference by lazy { db.getReference("inviteCodes") }

    // ---------- Write operations ----------

    fun createCrew(crew: Crew, onComplete: (Boolean) -> Unit = {}) {
        try {
            val crewMap = mapOf(
                "name" to crew.name,
                "emoji" to crew.emoji,
                "memberCount" to crew.memberCount,
                "totalBill" to crew.totalBill,
                "oweAmount" to crew.oweAmount,
                "inviteCode" to crew.inviteCode,
                "createdAt" to System.currentTimeMillis()
            )

            val updates = mapOf(
                "/crews/${crew.id}" to crewMap,
                "/inviteCodes/${crew.inviteCode.uppercase()}" to crew.id
            )

            db.reference.updateChildren(updates)
                .addOnSuccessListener {
                    Log.d(TAG, "Crew '${crew.name}' created with ID ${crew.id}")
                    onComplete(true)
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to create crew", e)
                    onComplete(false)
                }
        } catch (e: Exception) {
            Log.e(TAG, "Exception creating crew", e)
            onComplete(false)
        }
    }

    fun joinCrewByCode(code: String, onResult: (Crew?) -> Unit) {
        try {
            val upperCode = code.uppercase()
            inviteCodesRef.child(upperCode).get()
                .addOnSuccessListener { snapshot ->
                    val crewId = snapshot.getValue(String::class.java)
                    if (crewId != null) {
                        crewsRef.child(crewId).get()
                            .addOnSuccessListener { crewSnap ->
                                val crew = snapshotToCrew(crewSnap)
                                if (crew != null) {
                                    val newCount = crew.memberCount + 1
                                    val newOwe = crew.totalBill / newCount
                                    crewsRef.child(crewId).updateChildren(
                                        mapOf(
                                            "memberCount" to newCount,
                                            "oweAmount" to newOwe
                                        )
                                    )
                                    onResult(crew.copy(memberCount = newCount, oweAmount = newOwe))
                                } else {
                                    onResult(null)
                                }
                            }
                            .addOnFailureListener {
                                Log.e(TAG, "Failed to fetch crew for code $upperCode", it)
                                onResult(null)
                            }
                    } else {
                        Log.w(TAG, "Invite code '$upperCode' not found")
                        onResult(null)
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to look up invite code", e)
                    onResult(null)
                }
        } catch (e: Exception) {
            Log.e(TAG, "Exception joining crew", e)
            onResult(null)
        }
    }

    // ---------- Read / Listen operations ----------

    fun listenForCrews(onUpdate: (List<Crew>) -> Unit): ValueEventListener? {
        return try {
            val listener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val crews = mutableListOf<Crew>()
                    for (child in snapshot.children) {
                        snapshotToCrew(child)?.let { crews.add(it) }
                    }
                    crews.sortByDescending { it.id.toLongOrNull() ?: 0L }
                    onUpdate(crews)
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "Crew listener cancelled", error.toException())
                }
            }
            crewsRef.addValueEventListener(listener)
            listener
        } catch (e: Exception) {
            Log.e(TAG, "Exception attaching crew listener", e)
            onUpdate(emptyList())
            null
        }
    }

    fun removeCrewListener(listener: ValueEventListener) {
        try {
            crewsRef.removeEventListener(listener)
        } catch (e: Exception) {
            Log.e(TAG, "Exception removing crew listener", e)
        }
    }

    fun listenForCrew(crewId: String, onUpdate: (Crew?) -> Unit): ValueEventListener? {
        return try {
            val listener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    onUpdate(snapshotToCrew(snapshot))
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "Single crew listener cancelled", error.toException())
                }
            }
            crewsRef.child(crewId).addValueEventListener(listener)
            listener
        } catch (e: Exception) {
            Log.e(TAG, "Exception attaching single crew listener", e)
            onUpdate(null)
            null
        }
    }

    fun removeCrewListener(crewId: String, listener: ValueEventListener) {
        try {
            crewsRef.child(crewId).removeEventListener(listener)
        } catch (e: Exception) {
            Log.e(TAG, "Exception removing single crew listener", e)
        }
    }

    // ---------- Helpers ----------

    fun seedDefaultCrewsIfEmpty() {
        try {
            crewsRef.get().addOnSuccessListener { snapshot ->
                if (!snapshot.exists() || !snapshot.hasChildren()) {
                    Log.d(TAG, "Seeding default crews into Firebase")
                    val defaults = listOf(
                        Crew("1", "Sda", "🍕", 4, 2000.0, 600.0, "A56KT4"),
                        Crew("2", "Foodies", "🍔", 4, 2000.0, 750.0, "FOOD88"),
                        Crew("3", "Friday night party", "🍺", 4, 2000.0, 750.0, "PARTY9")
                    )
                    for (crew in defaults) {
                        createCrew(crew)
                    }
                }
            }.addOnFailureListener { e ->
                Log.e(TAG, "Failed to check for existing crews", e)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception seeding default crews", e)
        }
    }

    private fun snapshotToCrew(snapshot: DataSnapshot): Crew? {
        return try {
            val id = snapshot.key ?: return null
            val name = snapshot.child("name").getValue(String::class.java) ?: return null
            val emoji = snapshot.child("emoji").getValue(String::class.java) ?: "🎉"
            val memberCount = snapshot.child("memberCount").getValue(Int::class.java) ?: 1
            val totalBill = snapshot.child("totalBill").getValue(Double::class.java) ?: 0.0
            val oweAmount = snapshot.child("oweAmount").getValue(Double::class.java) ?: 0.0
            val inviteCode = snapshot.child("inviteCode").getValue(String::class.java) ?: ""

            Crew(
                id = id,
                name = name,
                emoji = emoji,
                memberCount = memberCount,
                totalBill = totalBill,
                oweAmount = oweAmount,
                inviteCode = inviteCode
            )
        } catch (e: Exception) {
            Log.e(TAG, "Exception parsing crew snapshot", e)
            null
        }
    }
}
