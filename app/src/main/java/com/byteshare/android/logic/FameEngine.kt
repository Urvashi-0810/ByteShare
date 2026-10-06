package com.byteshare.android.logic

import kotlin.math.floor
import kotlin.math.roundToLong

data class AppUsage(
    val appName: String,
    val category: String, // "social", "stream", "neutral", "productive"
    val minutes: Double
)

data class MemberUsage(
    val userId: String,
    val apps: List<AppUsage>,
    val redemptionMultiplier: Double = 1.0
)

data class RankedMember(
    val userId: String,
    val weighted: Double,
    val raw: Double,
    val rank: Int,
    val multiplier: Double,
    val shareMinorUnits: Long
)

object FameEngine {

    private val weights = mapOf(
        "social" to 2.0,
        "stream" to 1.5,
        "neutral" to 1.0,
        "productive" to 0.5
    )

    fun calculateWeightedMinutes(usage: MemberUsage): Double {
        val rawWeighted = usage.apps.sumOf { app ->
            val weight = weights[app.category] ?: 1.0
            app.minutes * weight
        }
        return rawWeighted * usage.redemptionMultiplier
    }

    fun calculateRawMinutes(usage: MemberUsage): Double {
        return usage.apps.sumOf { it.minutes }
    }

    fun multipliersFor(n: Int): List<Double> {
        if (n <= 0) return emptyList()
        if (n == 1) return listOf(1.0)
        
        val anchors = listOf(0.5, 0.8, 1.2, 1.5)
        return List(n) { rankIndex ->
            val scaledRank = rankIndex * 3.0 / (n - 1)
            val segment = scaledRank.toInt().coerceAtMost(2)
            val position = scaledRank - segment
            anchors[segment] + (anchors[segment + 1] - anchors[segment]) * position
        }
    }

    /** Applies per-member reductions and redistributes them within the multiplier bounds. */
    fun applyMultiplierReductions(
        baseline: List<Double>,
        requestedReductions: List<Double>,
        minimum: Double = 0.5,
        maximum: Double = 1.5
    ): List<Double> {
        require(baseline.size == requestedReductions.size)
        if (baseline.isEmpty()) return emptyList()

        val values = baseline.toMutableList()
        for (recipientIndex in baseline.indices) {
            val requested = requestedReductions[recipientIndex].coerceAtLeast(0.0)
            val reducible = (values[recipientIndex] - minimum).coerceAtLeast(0.0)
            var reduction = minOf(requested, reducible)
            if (reduction <= 0.0 || values.size == 1) continue

            val recipients = values.indices.filter { it != recipientIndex }
            val available = recipients.sumOf { (maximum - values[it]).coerceAtLeast(0.0) }
            reduction = minOf(reduction, available)
            if (reduction <= 0.0) continue

            values[recipientIndex] -= reduction
            var undistributed = reduction
            var active = recipients.filter { values[it] < maximum }
            while (undistributed > 1e-9 && active.isNotEmpty()) {
                val capacity = active.sumOf { maximum - values[it] }
                if (capacity <= 0.0) break
                val distribution = undistributed.coerceAtMost(capacity)
                var distributed = 0.0
                active.forEachIndexed { index, indexToAdjust ->
                    val amount = if (index == active.lastIndex) {
                        distribution - distributed
                    } else {
                        distribution * (maximum - values[indexToAdjust]) / capacity
                    }
                    val applied = amount.coerceAtMost(maximum - values[indexToAdjust])
                    values[indexToAdjust] += applied
                    distributed += applied
                }
                undistributed -= distributed
                active = active.filter { values[it] < maximum - 1e-9 }
            }
        }
        return values
    }

    /** Returns whole minor currency units (paise) whose sum exactly matches the bill. */
    fun allocateSharesMinorUnits(totalBill: Double, multipliers: List<Double>): List<Long> {
        if (multipliers.isEmpty()) return emptyList()
        require(totalBill >= 0.0)
        require(multipliers.all { it >= 0.0 })
        val totalMinor = (totalBill * 100.0).roundToLong()
        val multiplierTotal = multipliers.sum()
        if (multiplierTotal <= 0.0) return List(multipliers.size) { 0L }

        val exactShares = multipliers.map { totalMinor * it / multiplierTotal }
        val shares = exactShares.map { floor(it).toLong() }.toMutableList()
        var remaining = totalMinor - shares.sum()
        val remainderOrder = exactShares.indices.sortedByDescending { exactShares[it] - floor(exactShares[it]) }
        var index = 0
        while (remaining > 0L) {
            shares[remainderOrder[index % remainderOrder.size]]++
            remaining--
            index++
        }
        return shares
    }

    fun rankGroup(usages: List<MemberUsage>, totalBill: Double): List<RankedMember> {
        val enriched = usages.map { u ->
            object {
                val userId = u.userId
                val weighted = calculateWeightedMinutes(u)
                val raw = calculateRawMinutes(u)
            }
        }
        
        val sorted = enriched.sortedBy { it.weighted }
        val mults = multipliersFor(sorted.size)
        val shares = allocateSharesMinorUnits(totalBill, mults)
        
        val rankedMap = sorted.mapIndexed { i, m ->
            RankedMember(
                userId = m.userId,
                weighted = m.weighted,
                raw = m.raw,
                rank = i + 1,
                multiplier = mults[i],
                shareMinorUnits = shares[i]
            )
        }.associateBy { it.userId }
        
        // Maintain original order
        return usages.map { rankedMap[it.userId]!! }
    }
}