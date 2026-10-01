package com.example.byteshare.data

import android.content.Context
import android.util.Log
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase

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

/**
 * Firebase-backed challenge definitions + per-user progress tracking.
 *
 * Database structure:
 *   challenges/{challengeId} -> ChallengeDef fields
 *   challengeProgress/{uid}/{challengeId} -> { state, startedAt, completedAt, minutesUsed }
 */
object ChallengeRepository {

    private const val TAG = "ChallengeRepo"
    private const val DB_URL = "https://test-e06f1-default-rtdb.firebaseio.com"

    private val db: FirebaseDatabase by lazy { FirebaseDatabase.getInstance(DB_URL) }
    private val defsRef: DatabaseReference by lazy { db.getReference("challenges") }
    private val progressRef: DatabaseReference by lazy { db.getReference("challengeProgress") }

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
}
