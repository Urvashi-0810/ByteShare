package com.byteshare.android.data

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Process
import com.byteshare.android.logic.AppUsage
import java.util.Calendar

object UsageStatsCollector {

    // Exact Play Store package names. Anything not listed counts as neutral (1.0x).
    // Social Media: 2.0x (Instagram Reels counts here, since it lives inside Instagram)
    private val socialPackages = setOf(
        // Meta
        "com.instagram.android", "com.instagram.barcelona", "com.facebook.katana",
        "com.facebook.lite", "com.facebook.orca", "com.facebook.mlite",
        "com.whatsapp", "com.whatsapp.w4b",
        // Short video / feeds
        "com.zhiliaoapp.musically", "com.ss.android.ugc.trill", "com.snapchat.android",
        "com.twitter.android", "com.reddit.frontpage", "com.pinterest", "com.tumblr",
        "com.bereal.ft", "com.quora.android", "com.ninegag.android.app", "video.like",
        "xyz.blueskyweb.app", "org.joinmastodon.android", "com.linkedin.android",
        // Indian short video / social
        "in.mohalla.sharechat", "in.mohalla.video", "com.eterno.shortvideos",
        // Messaging
        "com.discord", "org.telegram.messenger", "org.thunderdog.challegram",
        "org.thoughtcrime.securesms", "com.viber.voip", "jp.naver.line.android",
        "com.tencent.mm", "com.kakao.talk", "com.vkontakte.android",
        // Dating
        "com.tinder", "com.bumble.app", "co.hinge.app"
    )

    // Streaming: 1.5x
    private val streamPackages = setOf(
        // Video
        "com.google.android.youtube", "com.google.android.apps.youtube.kids",
        "com.netflix.mediaclient", "com.amazon.avod.thirdpartyclient",
        "com.disney.disneyplus", "in.startv.hotstar", "com.hulu.plus", "com.wbd.stream",
        "com.hbo.hbonow", "com.cbs.app", "com.peacocktv.peacockandroid", "tv.twitch.android.app",
        "com.crunchyroll.crunchyroid", "com.plexapp.android", "tv.pluto.android", "com.tubitv",
        "com.vimeo.android.videoapp", "com.dailymotion.dailymotion", "com.mxtech.videoplayer.ad",
        // Indian OTT
        "com.jio.media.ondemand", "com.graymatrix.did", "com.sonyliv", "com.tv.v18.viola",
        // Music
        "com.spotify.music", "com.google.android.apps.youtube.music", "com.amazon.mp3",
        "com.apple.android.music", "com.soundcloud.android", "deezer.android.app",
        "com.pandora.android", "com.jio.media.jiobeats", "com.gaana", "com.bsbportal.music"
    )

    // Productivity: 0.5x
    private val productivePackages = setOf(
        // Google Workspace
        "com.google.android.apps.docs.editors.docs", "com.google.android.apps.docs.editors.sheets",
        "com.google.android.apps.docs.editors.slides", "com.google.android.apps.docs",
        "com.google.android.keep", "com.google.android.calendar", "com.google.android.gm",
        "com.google.android.apps.tasks", "com.google.android.apps.classroom",
        "com.google.android.apps.tachyon", "com.google.android.apps.translate",
        // Microsoft 365
        "com.microsoft.office.word", "com.microsoft.office.excel",
        "com.microsoft.office.powerpoint", "com.microsoft.office.onenote",
        "com.microsoft.office.outlook", "com.microsoft.teams", "com.microsoft.skydrive",
        "com.microsoft.office.officehubrow", "com.microsoft.todos",
        // Work & notes
        "notion.id", "com.Slack", "com.trello", "com.todoist", "com.ticktick.task",
        "com.evernote", "com.asana.app", "com.atlassian.android.jira.core", "co.mona.android",
        "md.obsidian", "us.zoom.videomeetings", "com.dropbox.android", "com.adobe.reader",
        "com.adobe.scan.android", "com.intsig.camscanner", "com.canva.editor",
        "com.github.android", "com.grammarly.android",
        "com.samsung.android.app.notes", "com.samsung.android.calendar",
        // AI assistants
        "com.openai.chatgpt", "com.anthropic.claude", "com.google.android.apps.bard",
        "com.microsoft.copilot", "ai.perplexity.app.android",
        // Learning & reading
        "com.duolingo", "org.khanacademy.android", "org.coursera.android", "com.udemy.android",
        "com.linkedin.android.learning", "com.quizlet.quizletandroid", "com.microblink.photomath",
        "org.brilliant.android", "com.memrise.android.memrisecompanionapp", "com.ichi2.anki",
        "com.byjus.thelearningapp", "com.unacademyapp", "xyz.penpencil.physicswala",
        "com.amazon.kindle", "com.google.android.apps.books", "com.audible.application",
        "com.blinkslabs.blinkist.android", "com.medium.reader",
        // Focus & wellbeing
        "cc.forestapp", "com.getsomeheadspace.android", "com.calm.android"
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