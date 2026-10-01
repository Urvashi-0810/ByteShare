package com.example.byteshare.data

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Process
import com.example.byteshare.logic.AppUsage
import java.util.Calendar

object UsageStatsCollector {

    private val socialPackages = setOf(
        "com.instagram.android", "com.facebook.katana", "com.twitter.android",
        "com.zhiliaoapp.musically", "com.snapchat.android", "com.reddit.frontpage",
        "com.whatsapp", "com.discord", "com.facebook.orca", "com.linkedin.android",
        "org.telegram.messenger"
    )

    private val streamPackages = setOf(
        "com.netflix.mediaclient", "com.amazon.avod.thirdpartyclient",
        "com.google.android.youtube", "com.spotify.music", "in.startv.hotstar",
        "com.disney.plus", "com.hulu"
    )

    private val productivePackages = setOf(
        "com.google.android.apps.docs.editors.docs", "com.notion.android",
        "com.slack", "com.google.android.calendar", "com.duolingo",
        "com.microsoft.office.word", "com.trello"
    )

    fun getCategory(packageName: String): String = when {
        socialPackages.contains(packageName) -> "social"
        streamPackages.contains(packageName) -> "stream"
        productivePackages.contains(packageName) -> "productive"
        else -> "neutral"
    }

    fun hasPermission(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun collectTodayUsage(context: Context): List<AppUsage> {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return collectUsage(context, cal.timeInMillis, System.currentTimeMillis())
    }

    fun collectRollingSevenDayUsage(context: Context, endTime: Long = System.currentTimeMillis()): List<AppUsage> {
        val startTime = rollingSevenDayStart(endTime)
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val dailyStats = usm.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            startTime,
            endTime
        ) ?: return emptyList()
        val totals = dailyStats.groupBy { it.packageName }
            .mapValues { (_, stats) -> stats.sumOf { it.totalTimeInForeground } }
        return toAppUsage(context, totals)
    }

    fun rollingSevenDayStart(endTime: Long): Long = Calendar.getInstance().run {
        timeInMillis = endTime
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        add(Calendar.DAY_OF_YEAR, -6)
        timeInMillis
    }

    fun collectUsageForWindow(context: Context, startTime: Long, endTime: Long): List<AppUsage> {
        if (endTime <= startTime) return emptyList()
        return collectUsage(context, startTime, endTime)
    }

    private fun collectUsage(context: Context, begin: Long, end: Long): List<AppUsage> {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

        // Event-based tracking: sum RESUMED->PAUSED intervals per app.
        val events = usm.queryEvents(begin, end) ?: return emptyList()
        val foregroundMs = mutableMapOf<String, Long>()
        val resumedAt = mutableMapOf<String, Long>()
        val event = UsageEvents.Event()

        // MOVE_TO_FOREGROUND/BACKGROUND share values with ACTIVITY_RESUMED/PAUSED; work on API 24+
        @Suppress("DEPRECATION")
        val fgType = UsageEvents.Event.MOVE_TO_FOREGROUND
        @Suppress("DEPRECATION")
        val bgType = UsageEvents.Event.MOVE_TO_BACKGROUND
        val stoppedType = 23 // UsageEvents.Event.ACTIVITY_STOPPED (API 29+)

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            when (event.eventType) {
                fgType -> resumedAt[event.packageName] = event.timeStamp.coerceAtLeast(begin)
                bgType, stoppedType -> {
                    val start = resumedAt.remove(event.packageName) ?: continue
                    foregroundMs.merge(event.packageName, event.timeStamp - start, Long::plus)
                }
            }
        }
        // Apps still in the foreground at query time
        for ((pkg, start) in resumedAt) {
            foregroundMs.merge(pkg, end - start, Long::plus)
        }

        return toAppUsage(context, foregroundMs)
    }

    private fun toAppUsage(context: Context, foregroundMs: Map<String, Long>): List<AppUsage> {
        val pm = context.packageManager
        return foregroundMs
            .filter { (pkg, ms) -> ms > 0 && pkg != context.packageName }
            .map { (pkg, totalMs) ->
                val appName = try {
                    pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
                } catch (e: Exception) { pkg }
                AppUsage(
                    appName = appName,
                    category = getCategory(pkg),
                    minutes = (totalMs / 60000.0 * 10).toInt() / 10.0
                )
            }
            .filter { it.minutes > 0 }
            .sortedByDescending { it.minutes }
    }
}