package com.example.byteshare.data

import android.app.usage.UsageStatsManager
import android.content.Context
import com.example.byteshare.logic.AppUsage
import java.util.Calendar
import java.util.TimeZone

object UsageStatsCollector {

    private val socialPackages = setOf(
        "com.instagram.android", "com.facebook.katana", "com.twitter.android",
        "com.zhiliaoapp.musically", "com.snapchat.android", "com.reddit.frontpage",
        "com.whatsapp", "com.discord"
    )

    private val streamPackages = setOf(
        "com.netflix.mediaclient", "com.amazon.avod.thirdpartyclient",
        "com.google.android.youtube", "com.spotify.music"
    )

    private val productivePackages = setOf(
        "com.google.android.apps.docs.editors.docs", "com.notion.android",
        "com.slack", "com.google.android.calendar", "com.duolingo"
    )

    fun getCategory(packageName: String): String = when {
        socialPackages.contains(packageName) -> "social"
        streamPackages.contains(packageName) -> "stream"
        productivePackages.contains(packageName) -> "productive"
        else -> "neutral"
    }

    fun collectTodayUsage(context: Context): List<AppUsage> {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        
        val begin = cal.timeInMillis
        val end = System.currentTimeMillis()

        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, begin, end)
            ?: return emptyList()

        val pm = context.packageManager
        val aggregated = stats.filter { it.totalTimeInForeground > 0 }
            .groupBy { it.packageName }
            .map { (pkg, list) ->
                val totalMs = list.sumOf { it.totalTimeInForeground }
                val appName = try {
                    pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
                } catch (e: Exception) { pkg }
                
                AppUsage(
                    appName = appName,
                    category = getCategory(pkg),
                    minutes = (totalMs / 60000.0 * 10).toInt() / 10.0
                )
            }.filter { it.minutes > 0 }

        return aggregated.sortedByDescending { it.minutes }
    }
}