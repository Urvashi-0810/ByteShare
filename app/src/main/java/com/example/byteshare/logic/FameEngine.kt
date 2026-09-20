package com.example.byteshare.logic

import kotlin.math.roundToInt

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
    val share: Int
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

    /**
     * Distribute multipliers between 0.5x and 1.5x such that the sum equals n.
     */
    fun multipliersFor(n: Int): List<Double> {
        if (n <= 0) return emptyList()
        if (n == 1) return listOf(1.0)
        
        // Generic linear distribution logic matching rank.ts
        val base = List(n) { i ->
            0.5 + (i.toDouble() * (1.5 - 0.5)) / (n - 1)
        }
        
        val sum = base.sum()
        val scale = n.toDouble() / sum
        
        return base.map { (it * scale * 1000).roundToInt() / 1000.0 }
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
        val perHead = totalBill / sorted.size
        
        val rankedMap = sorted.mapIndexed { i, m ->
            RankedMember(
                userId = m.userId,
                weighted = m.weighted,
                raw = m.raw,
                rank = i + 1,
                multiplier = mults[i],
                share = (perHead * mults[i]).roundToInt()
            )
        }.associateBy { it.userId }
        
        // Maintain original order
        return usages.map { rankedMap[it.userId]!! }
    }
}