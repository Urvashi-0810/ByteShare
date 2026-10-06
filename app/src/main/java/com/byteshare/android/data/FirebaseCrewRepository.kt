package com.byteshare.android.data

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
 *   crews/{crewId} -> Crew fields (incl. createdBy + members/{uid}: true)
 *   inviteCodes/{code} -> crewId  (O(1) lookup for join-by-code)
 *   userCrews/{uid}/{crewId} -> true  (per-user crew index)
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
    private val userCrewsRef: DatabaseReference by lazy { db.getReference("userCrews") }

    // ---------- Write operations ----------

    fun createCrew(crew: Crew, onComplete: (Boolean) -> Unit = {}) {
        try {
            val uid = AuthRepository.currentUserId
            if (uid == null) {
                Log.e(TAG, "Cannot create crew: not signed in")
                onComplete(false)
                return
            }

            // memberCount/oweAmount are derived from members at read time
            val crewMap = mapOf(
                "name" to crew.name,
                "emoji" to crew.emoji,
                "totalBill" to crew.totalBill,
                "inviteCode" to crew.inviteCode,
                "createdAt" to System.currentTimeMillis(),
                "createdBy" to uid,
                "members" to mapOf(uid to true)
            )

            val updates = mapOf(
                "/crews/${crew.id}" to crewMap,
                "/inviteCodes/${crew.inviteCode.uppercase()}" to crew.id,
                "/userCrews/$uid/${crew.id}" to true
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
            val uid = AuthRepository.currentUserId
            if (uid == null) {
                Log.e(TAG, "Cannot join crew: not signed in")
                onResult(null)
                return
            }
            val upperCode = code.uppercase()
            inviteCodesRef.child(upperCode).get()
                .addOnSuccessListener { snapshot ->
                    val crewId = snapshot.getValue(String::class.java)
                    if (crewId != null) {
                        // Crews are only readable by members, so join first. The rules accept the
                        // membership only when joinProof matches the crew's invite code.
                        val updates = mapOf(
                            "/crews/$crewId/joinProof/$uid" to upperCode,
                            "/crews/$crewId/members/$uid" to true,
                            "/userCrews/$uid/$crewId" to true
                        )
                        db.reference.updateChildren(updates)
                            .addOnSuccessListener {
                                crewsRef.child(crewId).get()
                                    .addOnSuccessListener { crewSnap -> onResult(snapshotToCrew(crewSnap)) }
                                    .addOnFailureListener {
                                        Log.e(TAG, "Failed to fetch crew for code $upperCode", it)
                                        onResult(null)
                                    }
                            }
                            .addOnFailureListener { e ->
                                Log.e(TAG, "Join write failed for crew $crewId", e)
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

    /**
     * Listens on userCrews/{uid} so the user only sees crews they created or joined.
     */
    fun listenForCrews(onUpdate: (List<Crew>) -> Unit): ValueEventListener? {
        return try {
            val uid = AuthRepository.currentUserId
            if (uid == null) {
                Log.e(TAG, "Cannot listen for crews: not signed in")
                onUpdate(emptyList())
                return null
            }
            val listener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val crewIds = snapshot.children.mapNotNull { it.key }
                    if (crewIds.isEmpty()) {
                        onUpdate(emptyList())
                        return
                    }
                    val crews = mutableListOf<Crew>()
                    var pending = crewIds.size
                    for (crewId in crewIds) {
                        crewsRef.child(crewId).get()
                            .addOnSuccessListener { crewSnap ->
                                snapshotToCrew(crewSnap)?.let { crews.add(it) }
                                if (--pending == 0) {
                                    crews.sortByDescending { it.id.toLongOrNull() ?: 0L }
                                    onUpdate(crews.toList())
                                }
                            }
                            .addOnFailureListener {
                                Log.e(TAG, "Failed to fetch crew $crewId", it)
                                if (--pending == 0) {
                                    crews.sortByDescending { it.id.toLongOrNull() ?: 0L }
                                    onUpdate(crews.toList())
                                }
                            }
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "Crew listener cancelled", error.toException())
                }
            }
            userCrewsRef.child(uid).addValueEventListener(listener)
            listener
        } catch (e: Exception) {
            Log.e(TAG, "Exception attaching crew listener", e)
            onUpdate(emptyList())
            null
        }
    }

    fun removeCrewListener(listener: ValueEventListener) {
        try {
            AuthRepository.currentUserId?.let { uid ->
                userCrewsRef.child(uid).removeEventListener(listener)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception removing crew listener", e)
        }
    }

    /** One-shot fetch of the current user's crews (no listener). */
    fun fetchMyCrewsOnce(onResult: (List<Crew>) -> Unit) {
        val uid = AuthRepository.currentUserId
        if (uid == null) {
            onResult(emptyList())
            return
        }
        userCrewsRef.child(uid).get()
            .addOnSuccessListener { snapshot ->
                val crewIds = snapshot.children.mapNotNull { it.key }
                if (crewIds.isEmpty()) {
                    onResult(emptyList())
                    return@addOnSuccessListener
                }
                val crews = mutableListOf<Crew>()
                var pending = crewIds.size
                for (crewId in crewIds) {
                    crewsRef.child(crewId).get()
                        .addOnSuccessListener { crewSnap ->
                            snapshotToCrew(crewSnap)?.let { crews.add(it) }
                            if (--pending == 0) onResult(crews.toList())
                        }
                        .addOnFailureListener {
                            if (--pending == 0) onResult(crews.toList())
                        }
                }
            }
            .addOnFailureListener {
                Log.e(TAG, "Failed to fetch my crews", it)
                onResult(emptyList())
            }
    }

    fun applyChallengeMultiplierReduction(
        challengeId: String,
        reduction: Double,
        onComplete: (Boolean) -> Unit = {}
    ) {
        val uid = AuthRepository.currentUserId
        if (uid == null || reduction <= 0.0) {
            onComplete(false)
            return
        }
        fetchMyCrewsOnce { crews ->
            val updates = mutableMapOf<String, Any>()
            crews.filter { it.members.containsKey(uid) }.forEach { crew ->
                updates["/crews/${crew.id}/multiplierReductions/$uid/$challengeId"] = reduction
            }
            if (updates.isEmpty()) {
                onComplete(true)
            } else {
                db.reference.updateChildren(updates)
                    .addOnSuccessListener { onComplete(true) }
                    .addOnFailureListener { error ->
                        Log.e(TAG, "Failed to store challenge reduction", error)
                        onComplete(false)
                    }
            }
        }
    }

    fun applyChallengeMultiplierReductionToCrew(
        crewId: String,
        challengeId: String,
        reduction: Double,
        onComplete: (Boolean) -> Unit = {}
    ) {
        val uid = AuthRepository.currentUserId
        if (uid == null || reduction <= 0.0) {
            onComplete(false)
            return
        }
        crewsRef.child(crewId).child("members").child(uid).get()
            .addOnSuccessListener { memberSnapshot ->
                if (memberSnapshot.getValue(Boolean::class.java) != true) {
                    onComplete(false)
                    return@addOnSuccessListener
                }
                crewsRef.child(crewId).child("multiplierReductions").child(uid)
                    .child(challengeId).setValue(reduction)
                    .addOnSuccessListener { onComplete(true) }
                    .addOnFailureListener { error ->
                        Log.e(TAG, "Failed to store group challenge reduction", error)
                        onComplete(false)
                    }
            }
            .addOnFailureListener { error ->
                Log.e(TAG, "Failed to verify group membership for challenge reward", error)
                onComplete(false)
            }
    }

    fun saveBaselineMultipliersIfChanged(crewId: String, multipliers: Map<String, Double>) {
        val ref = crewsRef.child(crewId).child("baselineMultipliers")
        ref.get().addOnSuccessListener { snapshot ->
            val current = snapshot.children.mapNotNull { child ->
                val memberUid = child.key ?: return@mapNotNull null
                val value = child.getValue(Double::class.java) ?: return@mapNotNull null
                memberUid to value
            }.toMap()
            val unchanged = current.keys == multipliers.keys && multipliers.all { (memberUid, value) ->
                kotlin.math.abs((current[memberUid] ?: Double.NaN) - value) < 0.0005
            }
            if (!unchanged) ref.setValue(multipliers)
        }.addOnFailureListener { error ->
            Log.e(TAG, "Failed to check crew multiplier snapshot", error)
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
                    onUpdate(null)
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

    private fun snapshotToCrew(snapshot: DataSnapshot): Crew? {
        return try {
            val id = snapshot.key ?: return null
            val name = snapshot.child("name").getValue(String::class.java) ?: return null
            val emoji = snapshot.child("emoji").getValue(String::class.java) ?: "🎉"
            val totalBill = snapshot.child("totalBill").getValue(Double::class.java) ?: 0.0
            val inviteCode = snapshot.child("inviteCode").getValue(String::class.java) ?: ""
            val createdBy = snapshot.child("createdBy").getValue(String::class.java) ?: ""
            val members = snapshot.child("members").children
                .mapNotNull { it.key }
                .associateWith { true }
            val multiplierReductions = snapshot.child("multiplierReductions").children
                .mapNotNull { child ->
                    val uid = child.key ?: return@mapNotNull null
                    val reduction = child.children.sumOf {
                        it.getValue(Double::class.java) ?: 0.0
                    }
                    uid to reduction
                }.toMap()
            val baselineMultipliers = snapshot.child("baselineMultipliers").children
                .mapNotNull { child ->
                    val uid = child.key ?: return@mapNotNull null
                    val multiplier = child.getValue(Double::class.java) ?: return@mapNotNull null
                    uid to multiplier
                }.toMap()
            val paidMembers = snapshot.child("paidMembers").children
                .mapNotNull { child ->
                    val uid = child.key ?: return@mapNotNull null
                    val hasPaid = child.getValue(Boolean::class.java) ?: return@mapNotNull null
                    uid to hasPaid
                }.toMap()

            // Derive count/owe from members: avoids stale counters and join races
            val memberCount = members.size.coerceAtLeast(1)

            Crew(
                id = id,
                name = name,
                emoji = emoji,
                memberCount = memberCount,
                totalBill = totalBill,
                oweAmount = totalBill / memberCount,
                inviteCode = inviteCode,
                createdBy = createdBy,
                members = members,
                multiplierReductions = multiplierReductions,
                baselineMultipliers = baselineMultipliers,
                paidMembers = paidMembers
            )
        } catch (e: Exception) {
            Log.e(TAG, "Exception parsing crew snapshot", e)
            null
        }
    }
}
