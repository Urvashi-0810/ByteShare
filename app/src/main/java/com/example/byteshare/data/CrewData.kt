package com.example.byteshare.data

data class Crew(
    val id: String,
    val name: String,
    val emoji: String,
    val memberCount: Int,
    val totalBill: Double,
    val oweAmount: Double,
    val inviteCode: String,
    val createdBy: String = "",
    val members: Map<String, Boolean> = emptyMap(),
    val multiplierReductions: Map<String, Double> = emptyMap(),
    val baselineMultipliers: Map<String, Double> = emptyMap(),
    val paidMembers: Map<String, Boolean> = emptyMap()
)