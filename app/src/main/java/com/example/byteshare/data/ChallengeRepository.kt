package com.example.byteshare.data

import android.content.Context
import android.util.Log
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import java.util.concurrent.atomic.AtomicInteger

enum class ChallengeState { NOT_STARTED, ACTIVE, COMPLETED, FAILED }

data class ChallengeDef(
    val id: String,
    val title: String,
    val subtitle: String,
    val isGroupPod: Boolean,
    val tag: String,
    val startFrequency: String,
    val emoji: String,
    val rewardBadge: String,
    val instructions: String,
    val requirement: String,
    val reward: String,
    val howItWorks: String,
    // Verification params for the tracker
    val type: String,            // "category_limit" | "phone_free" | "streak"
    val targetCategory: String,  // category to restrict ("social", "stream", "" = all)
    val limitMinutes: Double,    // max allowed minutes in the window
    val durationHours: Int,      // tracking window length from start
    val multiplierReduction: Double
)

data class ChallengeProgress(
    val state: ChallengeState,
    val startedAt: Long,
    val completedAt: Long,
    val minutesUsed: Double
)

data class ChallengeParticipant(
    val uid: String,
    val name: String,
    val progress: ChallengeProgress
)

/**
 * Firebase-backed challenge definitions + per-user progress tracking.
 *
 * Database structure:
 *   challenges/{challengeId} -> ChallengeDef fields
 *   challengeProgress/{uid}/{challengeId} -> personal challenge progress
 *   groupChallengeProgress/{crewId}/{challengeId}/{uid} -> opted-in crew progress
 */
object ChallengeRepository {

    private const val TAG = "ChallengeRepo"
    private const val DB_URL = "https://test-e06f1-default-rtdb.firebaseio.com"

    private val db: FirebaseDatabase by lazy { FirebaseDatabase.getInstance(DB_URL) }
    private val defsRef: DatabaseReference by lazy { db.getReference("challenges") }
    private val progressRef: DatabaseReference by lazy { db.getReference("challengeProgress") }
    private val groupProgressRef: DatabaseReference by lazy { db.getReference("groupChallengeProgress") }

    fun fetchChallenges(onResult: (List<ChallengeDef>) -> Unit) {
        defsRef.get()
            .addOnSuccessListener { snapshot ->
                val defs = snapshot.children.mapNotNull { snapshotToDef(it) }
                onResult(defs.sortedBy { it.id })
            }
            .addOnFailureListener {
                Log.e(TAG, "Failed to fetch challenges", it)
                onResult(emptyList())
            }
    }

    fun fetchChallenge(id: String, onResult: (ChallengeDef?) -> Unit) {
        defsRef.child(id).get()
            .addOnSuccessListener { onResult(snapshotToDef(it)) }
            .addOnFailureListener { onResult(null) }
    }

    fun fetchMyProgress(onResult: (Map<String, ChallengeProgress>) -> Unit) {
        val uid = AuthRepository.currentUserId ?: return onResult(emptyMap())
        progressRef.child(uid).get()
            .addOnSuccessListener { snapshot ->
                val map = snapshot.children.mapNotNull { child ->
                    val id = child.key ?: return@mapNotNull null
                    id to snapshotToProgress(child)
                }.toMap()
                onResult(map)
            }
            .addOnFailureListener { onResult(emptyMap()) }
    }

    fun startChallenge(challengeId: String, onComplete: (Boolean) -> Unit) {
        val uid = AuthRepository.currentUserId ?: return onComplete(false)
        val progress = mapOf(
            "state" to ChallengeState.ACTIVE.name,
            "startedAt" to System.currentTimeMillis(),
            "completedAt" to 0L,
            "minutesUsed" to 0.0
        )
        progressRef.child(uid).child(challengeId).setValue(progress)
            .addOnSuccessListener { onComplete(true) }
            .addOnFailureListener {
                Log.e(TAG, "Failed to start challenge $challengeId", it)
                onComplete(false)
            }
    }

    fun abandonChallenge(challengeId: String, onComplete: (Boolean) -> Unit) {
        val uid = AuthRepository.currentUserId ?: return onComplete(false)
        progressRef.child(uid).child(challengeId).removeValue()
            .addOnSuccessListener { onComplete(true) }
            .addOnFailureListener { onComplete(false) }
    }

    /**
     * Evaluates every ACTIVE challenge against live usage stats:
     *  - window elapsed + under limit  -> COMPLETED
     *  - over limit (anytime)          -> FAILED
     *  - otherwise stays ACTIVE with minutesUsed updated.
     * Returns refreshed progress map.
     */
    fun evaluateActiveChallenges(
        context: Context,
        defs: List<ChallengeDef>,
        onResult: (Map<String, ChallengeProgress>) -> Unit
    ) {
        val uid = AuthRepository.currentUserId ?: return onResult(emptyMap())
        fetchMyProgress { progressMap ->
            if (!UsageStatsCollector.hasPermission(context)) {
                onResult(progressMap)
                return@fetchMyProgress
            }
            val updates = mutableMapOf<String, Any>()
            val now = System.currentTimeMillis()
            val result = progressMap.toMutableMap()

            for ((id, progress) in progressMap) {
                if (progress.state != ChallengeState.ACTIVE) continue
                val def = defs.find { it.id == id } ?: continue

                val windowEnd = progress.startedAt + def.durationHours * 3_600_000L
                val usage = UsageStatsCollector.collectUsageForWindow(
                    context, progress.startedAt, minOf(now, windowEnd)
                )
                val minutesUsed = usage.filter {
                    def.type == "phone_free" || def.targetCategory.isEmpty() ||
                            it.category == def.targetCategory
                }.sumOf { it.minutes }

                val newState = when {
                    minutesUsed > def.limitMinutes -> ChallengeState.FAILED
                    now >= windowEnd -> ChallengeState.COMPLETED
                    else -> ChallengeState.ACTIVE
                }

                updates["/$uid/$id/minutesUsed"] = minutesUsed
                if (newState != ChallengeState.ACTIVE) {
                    updates["/$uid/$id/state"] = newState.name
                    updates["/$uid/$id/completedAt"] = now
                }
                result[id] = progress.copy(state = newState, minutesUsed = minutesUsed)
            }

            if (updates.isNotEmpty()) {
                progressRef.updateChildren(updates)
                    .addOnSuccessListener {
                        for ((id, progress) in progressMap) {
                            if (progress.state == ChallengeState.ACTIVE &&
                                result[id]?.state == ChallengeState.COMPLETED) {
                                val reduction = defs.find { it.id == id }?.multiplierReduction ?: 0.0
                                if (reduction > 0.0) {
                                    FirebaseCrewRepository.applyChallengeMultiplierReduction(id, reduction)
                                }
                            }
                        }
                    }
                    .addOnFailureListener { Log.e(TAG, "Progress update failed", it) }
            }
            onResult(result)
        }
    }

    // ---------- Snapshot parsing ----------

    private fun snapshotToDef(s: DataSnapshot): ChallengeDef? {
        val id = s.key ?: return null
        val title = s.child("title").getValue(String::class.java) ?: return null
        fun str(key: String) = s.child(key).getValue(String::class.java) ?: ""
        return ChallengeDef(
            id = id,
            title = title,
            subtitle = str("subtitle"),
            isGroupPod = s.child("isGroupPod").getValue(Boolean::class.java) ?: false,
            tag = str("tag"),
            startFrequency = str("startFrequency"),
            emoji = str("emoji"),
            rewardBadge = str("rewardBadge"),
            instructions = str("instructions"),
            requirement = str("requirement"),
            reward = str("reward"),
            howItWorks = str("howItWorks"),
            type = str("type"),
            targetCategory = str("targetCategory"),
            limitMinutes = s.child("limitMinutes").getValue(Double::class.java) ?: 0.0,
            durationHours = s.child("durationHours").getValue(Int::class.java) ?: 24,
            multiplierReduction = s.child("multiplierReduction").getValue(Double::class.java) ?: 0.0
        )
    }

    private fun snapshotToProgress(s: DataSnapshot): ChallengeProgress {
        val stateName = s.child("state").getValue(String::class.java) ?: "NOT_STARTED"
        return ChallengeProgress(
            state = runCatching { ChallengeState.valueOf(stateName) }
                .getOrDefault(ChallengeState.NOT_STARTED),
            startedAt = s.child("startedAt").getValue(Long::class.java) ?: 0L,
            completedAt = s.child("completedAt").getValue(Long::class.java) ?: 0L,
            minutesUsed = s.child("minutesUsed").getValue(Double::class.java) ?: 0.0
        )
    }

    fun startGroupChallenge(crewId: String, challengeId: String, onComplete: (Boolean) -> Unit) {
        val uid = AuthRepository.currentUserId ?: return onComplete(false)
        FirebaseCrewRepository.fetchMyCrewsOnce { crews ->
            if (crews.none { it.id == crewId && it.members.containsKey(uid) }) {
                onComplete(false)
                return@fetchMyCrewsOnce
            }
            val progress = mapOf(
                "state" to ChallengeState.ACTIVE.name,
                "startedAt" to System.currentTimeMillis(),
                "completedAt" to 0L,
                "minutesUsed" to 0.0
            )
            groupProgressRef.child(crewId).child(challengeId).child(uid).setValue(progress)
                .addOnSuccessListener { onComplete(true) }
                .addOnFailureListener { error ->
                    Log.e(TAG, "Failed to join group challenge $challengeId", error)
                    onComplete(false)
                }
        }
    }

    fun abandonGroupChallenge(crewId: String, challengeId: String, onComplete: (Boolean) -> Unit) {
        val uid = AuthRepository.currentUserId ?: return onComplete(false)
        groupProgressRef.child(crewId).child(challengeId).child(uid).removeValue()
            .addOnSuccessListener { onComplete(true) }
            .addOnFailureListener { onComplete(false) }
    }

    fun evaluateGroupChallenge(
        context: Context,
        crewId: String,
        def: ChallengeDef,
        onResult: (List<ChallengeParticipant>) -> Unit
    ) {
        val uid = AuthRepository.currentUserId ?: return onResult(emptyList())
        groupProgressRef.child(crewId).child(def.id).get()
            .addOnSuccessListener { snapshot ->
                val progressByUid = snapshot.children.mapNotNull { child ->
                    val participantUid = child.key ?: return@mapNotNull null
                    participantUid to snapshotToProgress(child)
                }.toMap().toMutableMap()
                val current = progressByUid[uid]
                if (current == null || current.state != ChallengeState.ACTIVE ||
                    !UsageStatsCollector.hasPermission(context)
                ) {
                    fetchParticipantProfiles(progressByUid, onResult)
                    return@addOnSuccessListener
                }

                val now = System.currentTimeMillis()
                val windowEnd = current.startedAt + def.durationHours * 3_600_000L
                val usage = UsageStatsCollector.collectUsageForWindow(
                    context, current.startedAt, minOf(now, windowEnd)
                )
                val minutesUsed = usage.filter {
                    def.type == "phone_free" || def.targetCategory.isEmpty() ||
                            it.category == def.targetCategory
                }.sumOf { it.minutes }
                val newState = when {
                    minutesUsed > def.limitMinutes -> ChallengeState.FAILED
                    now >= windowEnd -> ChallengeState.COMPLETED
                    else -> ChallengeState.ACTIVE
                }
                val updated = current.copy(
                    state = newState,
                    completedAt = if (newState == ChallengeState.ACTIVE) 0L else now,
                    minutesUsed = minutesUsed
                )
                val updates = mapOf(
                    "state" to newState.name,
                    "completedAt" to updated.completedAt,
                    "minutesUsed" to minutesUsed
                )
                groupProgressRef.child(crewId).child(def.id).child(uid).updateChildren(updates)
                    .addOnSuccessListener {
                        progressByUid[uid] = updated
                        if (current.state == ChallengeState.ACTIVE &&
                            newState == ChallengeState.COMPLETED && def.multiplierReduction > 0.0
                        ) {
                            FirebaseCrewRepository.applyChallengeMultiplierReductionToCrew(
                                crewId, def.id, def.multiplierReduction
                            ) {
                                fetchParticipantProfiles(progressByUid, onResult)
                            }
                        } else {
                            fetchParticipantProfiles(progressByUid, onResult)
                        }
                    }
                    .addOnFailureListener { error ->
                        Log.e(TAG, "Failed to update group challenge progress", error)
                        fetchParticipantProfiles(progressByUid, onResult)
                    }
            }
            .addOnFailureListener { error ->
                Log.e(TAG, "Failed to load group challenge participants", error)
                onResult(emptyList())
            }
    }

    /**
     * Fan-out read across all the user's crews to find their group challenge
     * participation.  Returns two maps keyed by challengeId:
     *  - progress: the user's "best" state (COMPLETED > ACTIVE > FAILED)
     *  - participantCounts: number of opted-in members per challenge
     */
    fun fetchMyGroupProgressAcrossCrews(
        onResult: (progress: Map<String, ChallengeProgress>, participantCounts: Map<String, Int>) -> Unit
    ) {
        val uid = AuthRepository.currentUserId ?: return onResult(emptyMap(), emptyMap())
        FirebaseCrewRepository.fetchMyCrewsOnce { crews ->
            if (crews.isEmpty()) {
                onResult(emptyMap(), emptyMap())
                return@fetchMyCrewsOnce
            }
            val progressResult = mutableMapOf<String, ChallengeProgress>()
            val countResult = mutableMapOf<String, Int>()
            val pending = AtomicInteger(crews.size)
            for (crew in crews) {
                groupProgressRef.child(crew.id).get()
                    .addOnSuccessListener { crewSnapshot ->
                        synchronized(progressResult) {
                            for (challengeSnap in crewSnapshot.children) {
                                val challengeId = challengeSnap.key ?: continue
                                val totalParticipants = challengeSnap.childrenCount.toInt()
                                countResult[challengeId] = maxOf(
                                    countResult[challengeId] ?: 0, totalParticipants
                                )
                                val userSnap = challengeSnap.child(uid)
                                if (!userSnap.exists()) continue
                                val progress = snapshotToProgress(userSnap)
                                val existing = progressResult[challengeId]
                                if (existing == null ||
                                    statePriority(progress.state) > statePriority(existing.state)
                                ) {
                                    progressResult[challengeId] = progress
                                }
                            }
                        }
                        if (pending.decrementAndGet() == 0) onResult(progressResult, countResult)
                    }
                    .addOnFailureListener {
                        if (pending.decrementAndGet() == 0) onResult(progressResult, countResult)
                    }
            }
        }
    }

    /** Priority order for picking the "best" state across crews. */
    private fun statePriority(state: ChallengeState): Int = when (state) {
        ChallengeState.COMPLETED -> 3
        ChallengeState.ACTIVE -> 2
        ChallengeState.FAILED -> 1
        ChallengeState.NOT_STARTED -> 0
    }

    private fun fetchParticipantProfiles(
        progressByUid: Map<String, ChallengeProgress>,
        onResult: (List<ChallengeParticipant>) -> Unit
    ) {
        if (progressByUid.isEmpty()) {
            onResult(emptyList())
            return
        }
        val participants = mutableListOf<ChallengeParticipant>()
        val pending = AtomicInteger(progressByUid.size)
        progressByUid.forEach { (uid, progress) ->
            UserRepository.fetchFriendEntry(uid) { entry ->
                synchronized(participants) {
                    participants.add(ChallengeParticipant(uid, entry?.name ?: "Member", progress))
                }
                if (pending.decrementAndGet() == 0) {
                    onResult(participants.sortedBy { it.name.lowercase() })
                }
            }
        }
    }
}
