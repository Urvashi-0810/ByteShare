package com.example.byteshare.ui.fragments

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.byteshare.R
import com.example.byteshare.data.UsageStatsCollector
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView

class StatsFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_stats, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<Button>(R.id.btn_grant_usage)?.setOnClickListener {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
    }

    override fun onResume() {
        super.onResume()
        val currentView = view ?: return

        // Re-check on return from settings
        val granted = UsageStatsCollector.hasPermission(requireContext())
        currentView.findViewById<View>(R.id.usage_permission_card)?.visibility =
            if (granted) View.GONE else View.VISIBLE
        if (granted) updateUsageUI(currentView)

        // Load AdMob Banner Ad
        val adView = currentView.findViewById<AdView>(R.id.ad_view_stats)
        val adRequest = AdRequest.Builder().build()
        adView?.loadAd(adRequest)
    }

    private fun updateUsageUI(view: View) {
        // 1. TODAY'S DAILY STATS (TOP)
        val todayData = UsageStatsCollector.collectTodayUsage(requireContext())
        
        val socialTime = todayData.filter { it.category == "social" }.sumOf { it.minutes }
        val streamTime = todayData.filter { it.category == "stream" }.sumOf { it.minutes }
        val neutralTime = todayData.filter { it.category == "neutral" }.sumOf { it.minutes }
        val productiveTime = todayData.filter { it.category == "productive" }.sumOf { it.minutes }
        val totalTodayMinutes = socialTime + streamTime + neutralTime + productiveTime

        val todayWeightedScore = socialTime * 2.0 + streamTime * 1.5 + neutralTime + productiveTime * 0.5
        
        view.findViewById<TextView>(R.id.txt_total_time)?.text = formatDuration(totalTodayMinutes)
        view.findViewById<TextView>(R.id.txt_daily_average)?.text = "4h 00m" // Daily target
        view.findViewById<TextView>(R.id.txt_weighted_score)?.text = "${todayWeightedScore.toInt()}m"
        
        view.findViewById<TextView>(R.id.txt_social_time)?.text = formatDuration(socialTime)
        view.findViewById<TextView>(R.id.txt_stream_time)?.text = formatDuration(streamTime)
        view.findViewById<TextView>(R.id.txt_neutral_time)?.text = formatDuration(neutralTime)
        view.findViewById<TextView>(R.id.txt_productive_time)?.text = formatDuration(productiveTime)

        fun shareOfToday(minutes: Double): String =
            if (totalTodayMinutes > 0.0) "${(minutes / totalTodayMinutes * 100).toInt()}% of usage" else "0% of usage"
            
        view.findViewById<TextView>(R.id.txt_social_share)?.text = shareOfToday(socialTime)
        view.findViewById<TextView>(R.id.txt_stream_share)?.text = shareOfToday(streamTime)
        view.findViewById<TextView>(R.id.txt_neutral_share)?.text = shareOfToday(neutralTime)
        view.findViewById<TextView>(R.id.txt_productive_share)?.text = shareOfToday(productiveTime)

        val divisor = totalTodayMinutes.takeIf { it > 0.0 } ?: 1.0
        updateWeight(view.findViewById(R.id.progress_social), socialTime / divisor)
        updateWeight(view.findViewById(R.id.progress_stream), streamTime / divisor)
        updateWeight(view.findViewById(R.id.progress_neutral), neutralTime / divisor)
        updateWeight(view.findViewById(R.id.progress_productive), productiveTime / divisor)

        // Top Apps Today
        val appsContainer = view.findViewById<LinearLayout>(R.id.top_apps_container)
        if (appsContainer != null) {
            val emptyApps = view.findViewById<TextView>(R.id.txt_apps_empty)
            appsContainer.removeAllViews()
            emptyApps?.visibility = if (todayData.isEmpty()) View.VISIBLE else View.GONE
            todayData.take(5).forEach { app ->
                val row = layoutInflater.inflate(R.layout.item_app_usage, appsContainer, false)
                row.findViewById<TextView>(R.id.txt_app_name).text = app.appName
                row.findViewById<TextView>(R.id.txt_app_time).text = formatDuration(app.minutes)
                appsContainer.addView(row)
            }
        }

        // 2. WEEKLY SUMMARY STATS (BOTTOM)
        val weeklyData = UsageStatsCollector.collectRollingSevenDayUsage(requireContext())
        val weeklySocial = weeklyData.filter { it.category == "social" }.sumOf { it.minutes }
        val weeklyStream = weeklyData.filter { it.category == "stream" }.sumOf { it.minutes }
        val weeklyNeutral = weeklyData.filter { it.category == "neutral" }.sumOf { it.minutes }
        val weeklyProd = weeklyData.filter { it.category == "productive" }.sumOf { it.minutes }
        val totalWeeklyMinutes = weeklySocial + weeklyStream + weeklyNeutral + weeklyProd
        val weeklyWeighted = weeklySocial * 2.0 + weeklyStream * 1.5 + weeklyNeutral + weeklyProd * 0.5

        view.findViewById<TextView>(R.id.txt_weekly_total_time)?.text = formatDuration(totalWeeklyMinutes)
        view.findViewById<TextView>(R.id.txt_weekly_daily_avg)?.text = formatDuration(totalWeeklyMinutes / 7.0)
        view.findViewById<TextView>(R.id.txt_weekly_weighted_score)?.text = "${weeklyWeighted.toInt()}m"
    }

    private fun formatDuration(minutes: Double): String {
        val wholeMinutes = minutes.toInt().coerceAtLeast(0)
        val hours = wholeMinutes / 60
        val remainder = wholeMinutes % 60
        return if (hours == 0) "${remainder}m" else "${hours}h ${remainder}m"
    }

    private fun updateWeight(view: View?, weight: Double) {
        if (view == null) return
        val params = view.layoutParams as LinearLayout.LayoutParams
        params.weight = weight.toFloat()
        view.layoutParams = params
    }
}