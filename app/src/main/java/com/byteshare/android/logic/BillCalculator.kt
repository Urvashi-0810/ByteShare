package com.byteshare.android.logic

object BillCalculator {

    /**
     * Calculates the bill share for each member based on their rank.
     * The multipliers for a group of 4 are:
     * Rank 1 (Lowest): 0.5x
     * Rank 2: 0.8x
     * Rank 3: 1.2x
     * Rank 4 (Highest): 1.5x
     * 
     * Total multiplier sum = 4.0
     */
    fun calculateShares(totalBill: Double, weightedScores: List<Double>): List<Double> {
        val memberCount = weightedScores.size
        if (memberCount == 0) return emptyList()

        // Get indices sorted by weighted score (ascending - lowest score is Rank 1)
        val sortedIndices = weightedScores.indices.sortedBy { weightedScores[it] }
        
        val multipliers = when (memberCount) {
            4 -> listOf(0.5, 0.8, 1.2, 1.5)
            // Default to equal split for other sizes for now, or scale logic
            else -> List(memberCount) { 1.0 }
        }

        val result = MutableList(memberCount) { 0.0 }
        val baseShare = totalBill / memberCount

        for (rank in sortedIndices.indices) {
            val originalIndex = sortedIndices[rank]
            val multiplier = multipliers.getOrElse(rank) { 1.0 }
            result[originalIndex] = baseShare * multiplier
        }

        return result
    }
}
