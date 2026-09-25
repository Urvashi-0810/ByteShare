package com.example.byteshare.data

enum class ParticipantStatus { PASS, FAIL, LIVE }

data class Participant(
    val avatar: String,
    val name: String,
    val detail: String,
    val status: ParticipantStatus
)

data class Challenge(
    val id: String,
    val title: String,
    val subtitle: String,
    val isGroupPod: Boolean,
    val tag: String,
    val startFrequency: String,
    val emoji: String,
    val rewardBadge: String,
    val podCount: Int = 0,
    val instructions: String,
    val requirement: String,
    val reward: String,
    val howItWorks: String,
    val participants: List<Participant>
)

object ChallengeRepository {
    private val challenges = listOf(
        // Individual Pledges
        Challenge(
            id = "1",
            title = "Screen Sabbath redemption",
            subtitle = "Pledge a 4-hour phone-free window.",
            isGroupPod = false,
            tag = "SABBATH",
            startFrequency = "STARTS DAILY",
            emoji = "🧘",
            rewardBadge = "-0.2 MULTIPLIER",
            instructions = "\"Pledge a 4-hour phone-free window. Shave 0.2 off your multiplier.\"",
            requirement = "4-hour phone-free window (Verified via inactivity)",
            reward = "-0.2 Multiplier",
            howItWorks = "Detection is handled via ByteShare's background inactivity hook. We verify you haven't opened any restricted apps during your focus window. GPS inactivity confirms stationary phone placement.",
            participants = listOf(
                Participant("🐯", "Arjun", "Used Instagram (4m)", ParticipantStatus.FAIL),
                Participant("🦄", "Priya", "123m inactivity recorded", ParticipantStatus.PASS),
                Participant("🐯", "You", "Currently tracking...", ParticipantStatus.LIVE),
                Participant("🦋", "Ishaani", "Last active 2h ago", ParticipantStatus.LIVE)
            )
        ),
        Challenge(
            id = "2",
            title = "The Morning Fast",
            subtitle = "No phone for the first hour after waking up.",
            isGroupPod = false,
            tag = "MORNING",
            startFrequency = "STARTS DAILY",
            emoji = "🌅",
            rewardBadge = "-0.05 MULTIPLIER",
            instructions = "\"Avoid screen time for 60 minutes after your morning alarm.\"",
            requirement = "No social or streaming apps 6am-9am window",
            reward = "-0.05 Multiplier",
            howItWorks = "Automatic alarm detection triggers the 60-minute window. Launching restricted apps during this time fails the quest.",
            participants = listOf(
                Participant("🐼", "Rahul", "Completed fast (60m)", ParticipantStatus.PASS),
                Participant("🐯", "You", "Ready for tomorrow", ParticipantStatus.LIVE)
            )
        ),
        Challenge(
            id = "3",
            title = "Dinner Table Detox",
            subtitle = "Phone-free during dinner. Earn the Present Badge.",
            isGroupPod = false,
            tag = "DETOX",
            startFrequency = "EVENING QUEST",
            emoji = "🍽️",
            rewardBadge = "PRESENT BADGE 🎖️",
            instructions = "\"Put your phone face down during dinner (7 PM - 9 PM).\"",
            requirement = "45 minutes continuous face-down placement",
            reward = "Present Badge 🎖️",
            howItWorks = "Proximity and ambient light sensors confirm device face-down status during scheduled meal windows.",
            participants = listOf(
                Participant("🦄", "Priya", "45m table detox", ParticipantStatus.PASS),
                Participant("🐯", "Arjun", "Picked up phone (12m)", ParticipantStatus.FAIL)
            )
        ),

        // Group Pods
        Challenge(
            id = "4",
            title = "The Collective Detox",
            subtitle = "Pod's combined weekly screen time reduction.",
            isGroupPod = true,
            tag = "POD DETOX",
            startFrequency = "WEEKLY POD",
            emoji = "📉",
            rewardBadge = "SHARED FUND BONUS 💰",
            podCount = 4,
            instructions = "\"Achieve a 15% group-wide reduction in total social media usage this week.\"",
            requirement = "Group total weighted score under 2500 mins",
            reward = "Shared Fund Bonus 💰",
            howItWorks = "Real-time usage aggregation calculates the combined pod screen time daily.",
            participants = listOf(
                Participant("🐼", "Rahul", "On track (-18%)", ParticipantStatus.PASS),
                Participant("🦄", "Priya", "On track (-22%)", ParticipantStatus.PASS),
                Participant("🐯", "You", "Tracking live (-12%)", ParticipantStatus.LIVE),
                Participant("🐯", "Arjun", "High usage (+5%)", ParticipantStatus.FAIL)
            )
        ),
        Challenge(
            id = "5",
            title = "No Scroll Sunday",
            subtitle = "Zero social media across the entire pod on Sunday.",
            isGroupPod = true,
            tag = "SUNDAY QUEST",
            startFrequency = "WEEKLY SUNDAY",
            emoji = "🚫",
            rewardBadge = "IMMUNITY BADGE 🛡️",
            podCount = 4,
            instructions = "\"Zero minutes on Instagram, Twitter, or TikTok for the whole pod on Sunday.\"",
            requirement = "0 mins on Social Media category for 24 hours",
            reward = "Immunity Badge 🛡️",
            howItWorks = "ByteShare monitors usage stats on Sunday. Any social app launch notifies the entire pod.",
            participants = listOf(
                Participant("🐯", "You", "Awaiting Sunday", ParticipantStatus.LIVE),
                Participant("🐼", "Rahul", "Awaiting Sunday", ParticipantStatus.LIVE)
            )
        ),
        Challenge(
            id = "6",
            title = "Streak Synchrony",
            subtitle = "All pod members maintain a 3-day focus streak.",
            isGroupPod = true,
            tag = "STREAK",
            startFrequency = "ALWAYS ACTIVE",
            emoji = "🔗",
            rewardBadge = "+50 SOCIAL XP ⭐️",
            podCount = 4,
            instructions = "\"Synchronize streaks! Every member must meet daily screen time goals for 3 consecutive days.\"",
            requirement = "3-day streak across all 4 pod members",
            reward = "+50 Social XP ⭐️",
            howItWorks = "Streaks increase daily at midnight if individual weighted scores stay below target limits.",
            participants = listOf(
                Participant("🦄", "Priya", "Streak Day 3", ParticipantStatus.PASS),
                Participant("🐼", "Rahul", "Streak Day 3", ParticipantStatus.PASS),
                Participant("🐯", "You", "Streak Day 3", ParticipantStatus.PASS),
                Participant("🐯", "Arjun", "Streak Reset", ParticipantStatus.FAIL)
            )
        ),
        Challenge(
            id = "7",
            title = "Category Cleanse",
            subtitle = "Pick one category (Streaming) and cut time by half.",
            isGroupPod = true,
            tag = "CLEANSE",
            startFrequency = "WEEKLY QUEST",
            emoji = "🧼",
            rewardBadge = "CLEANSE BADGE 🧼",
            podCount = 4,
            instructions = "\"Cut streaming time in half for 48 hours.\"",
            requirement = "Under 60 mins streaming time per member",
            reward = "Cleanse Badge 🧼",
            howItWorks = "Monitors YouTube, Netflix, and Hotstar usage during the active cleanse window.",
            participants = listOf(
                Participant("🦋", "Ishaani", "35m recorded", ParticipantStatus.PASS),
                Participant("🐯", "You", "42m recorded", ParticipantStatus.PASS)
            )
        )
    )

    fun getChallenges(): List<Challenge> = challenges

    fun getChallengeById(id: String): Challenge? = challenges.find { it.id == id }
}