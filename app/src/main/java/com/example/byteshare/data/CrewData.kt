package com.example.byteshare.data

data class Crew(
    val id: String,
    val name: String,
    val emoji: String,
    val memberCount: Int,
    val totalBill: Double,
    val oweAmount: Double,
    val inviteCode: String
)

object CrewRepository {
    private val crews = mutableListOf(
        Crew("1", "Sda", "🍕", 4, 2000.0, 600.0, "A56KT4"),
        Crew("2", "Foodies", "🍔", 4, 2000.0, 750.0, "FOOD88"),
        Crew("3", "Friday night party", "🍺", 4, 2000.0, 750.0, "PARTY9")
    )

    fun getCrews(): List<Crew> = crews.toList()

    fun addCrew(crew: Crew) {
        crews.add(0, crew)
    }

    fun joinCrewByCode(code: String): Crew? {
        val existing = crews.find { it.inviteCode.equals(code, ignoreCase = true) }
        if (existing != null) return existing
        
        // If code not found, create a joined crew instance for demo
        val newJoined = Crew(
            id = System.currentTimeMillis().toString(),
            name = "Joined Crew ($code)",
            emoji = "🎉",
            memberCount = 4,
            totalBill = 2000.0,
            oweAmount = 500.0,
            inviteCode = code.uppercase()
        )
        crews.add(0, newJoined)
        return newJoined
    }
}