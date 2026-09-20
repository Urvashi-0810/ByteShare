package com.example.byteshare.logic

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

    /**
     * App weighting logic:
     * Social Media: 2.0x
     * Streaming: 1.5x
     * Neutral: 1.0x
     * Productivity: 0.5x
     */
    fun calculateWeightedScore(appUsages: Map<String, Long>): Double {
        var totalWeightedTime = 0.0
        
        appUsages.forEach { (packageName, timeInMinutes) ->
            val weight = getWeightForApp(packageName)
            totalWeightedTime += timeInMinutes * weight
        }
        
        return totalWeightedTime
    }

    private fun getWeightForApp(packageName: String): Double {
        return when {
            isSocialMedia(packageName) -> 2.0
            isStreaming(packageName) -> 1.5
            isProductivity(packageName) -> 0.5
            else -> 1.0 // Neutral
        }
    }

    private fun isSocialMedia(pkg: String) = pkg.contains("instagram") || pkg.contains("twitter") || pkg.contains("facebook") || pkg.contains("snapchat") || pkg.contains("tiktok")
    private fun isStreaming(pkg: String) = pkg.contains("youtube") || pkg.contains("netflix") || pkg.contains("primevideo") || pkg.contains("disney")
    private fun isProductivity(pkg: String) = pkg.contains("notion") || pkg.contains("calendar") || pkg.contains("duolingo") || pkg.contains("trello") || pkg.contains("slack")
}