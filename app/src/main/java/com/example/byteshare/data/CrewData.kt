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
    val members: Map<String, Boolean> = emptyMap()
)