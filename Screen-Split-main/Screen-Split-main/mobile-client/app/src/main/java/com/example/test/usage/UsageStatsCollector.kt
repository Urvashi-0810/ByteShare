package com.example.test.usage

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import com.example.test.api.AppUsageEntry
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.round

object UsageStatsCollector {

    private val socialPackages =
        setOf(
            "com.instagram.android",
            "com.facebook.katana",
            "com.facebook.orca",
            "com.twitter.android",
            "com.zhiliaoapp.musically",
            "com.snapchat.android",
            "com.reddit.frontpage",
            "com.linkedin.android",
            "org.telegram.messenger",
            "com.whatsapp",
            "com.discord",
        )

    private val streamPackages =
        setOf(
            "com.netflix.mediaclient",
            "com.amazon.avod.thirdpartyclient",
            "com.google.android.youtube",
            "in.startv.hotstar",
            "com.spotify.music",
        )

    private val productivePackages =
        setOf(
            "com.google.android.apps.docs.editors.docs",
            "com.microsoft.office.word",
            "com.notion.android",
            "com.slack",
            "com.google.android.calendar",
        )

    fun utcTodayReportDate(): String {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        val y = cal.get(Calendar.YEAR)
        val m = cal.get(Calendar.MONTH) + 1
        val d = cal.get(Calendar.DAY_OF_MONTH)
        return String.format("%04d-%02d-%02d", y, m, d)
    }

    fun categoryForPackage(packageName: String): String =
        when {
            socialPackages.contains(packageName) -> "social"
            streamPackages.contains(packageName) -> "stream"
            productivePackages.contains(packageName) -> "productive"
            else -> "neutral"
        }

    /**
     * Aggregates foreground time from the start of the current UTC calendar day through now.
     */
    fun collectTodayEntries(context: Context, selfPackage: String): List<AppUsageEntry> {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val begin = cal.timeInMillis
        val end = System.currentTimeMillis()

        val stats =
            usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, begin, end)
                ?: return emptyList()

        val msByPackage = HashMap<String, Long>()
        for (s in stats) {
            if (s.packageName == selfPackage) continue
            msByPackage.merge(s.packageName, s.totalTimeInForeground, Long::plus)
        }

        val pm = context.packageManager
        val entries = ArrayList<AppUsageEntry>(msByPackage.size)
        for ((pkg, ms) in msByPackage) {
            if (ms <= 0L) continue
            val minutes = round(ms / 60000.0 * 10) / 10.0
            if (minutes <= 0) continue
            val label = appLabel(pm, pkg)
            entries.add(
                AppUsageEntry(
                    appName = label,
                    category = categoryForPackage(pkg),
                    minutes = minutes,
                ),
            )
        }

        entries.sortByDescending { it.minutes }
        return entries.take(200)
    }

    private fun appLabel(pm: PackageManager, packageName: String): String =
        try {
            val info = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(info).toString().ifBlank { packageName }
        } catch (_: PackageManager.NameNotFoundException) {
            packageName
        }
}
