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
        val usageData = UsageStatsCollector.collectRollingSevenDayUsage(requireContext())
        
        val socialTime = usageData.filter { it.category == "social" }.sumOf { it.minutes }
        val streamTime = usageData.filter { it.category == "stream" }.sumOf { it.minutes }
        val neutralTime = usageData.filter { it.category == "neutral" }.sumOf { it.minutes }
        val productiveTime = usageData.filter { it.category == "productive" }.sumOf { it.minutes }
        val totalMinutes = socialTime + streamTime + neutralTime + productiveTime

        val weightedScore = socialTime * 2.0 + streamTime * 1.5 + neutralTime + productiveTime * 0.5
        view.findViewById<TextView>(R.id.txt_total_time)?.text = formatDuration(totalMinutes)
        view.findViewById<TextView>(R.id.txt_daily_average)?.text = formatDuration(totalMinutes / 7.0)
        view.findViewById<TextView>(R.id.txt_weighted_score)?.text = "${weightedScore.toInt()}m"
        view.findViewById<TextView>(R.id.txt_social_time)?.text = formatDuration(socialTime)
        view.findViewById<TextView>(R.id.txt_stream_time)?.text = formatDuration(streamTime)
        view.findViewById<TextView>(R.id.txt_neutral_time)?.text = formatDuration(neutralTime)
        view.findViewById<TextView>(R.id.txt_productive_time)?.text = formatDuration(productiveTime)

        fun shareOfTotal(minutes: Double): String =
            if (totalMinutes > 0.0) "${(minutes / totalMinutes * 100).toInt()}% of usage" else "0% of usage"
        view.findViewById<TextView>(R.id.txt_social_share)?.text = shareOfTotal(socialTime)
        view.findViewById<TextView>(R.id.txt_stream_share)?.text = shareOfTotal(streamTime)
        view.findViewById<TextView>(R.id.txt_neutral_share)?.text = shareOfTotal(neutralTime)
        view.findViewById<TextView>(R.id.txt_productive_share)?.text = shareOfTotal(productiveTime)

        val divisor = totalMinutes.takeIf { it > 0.0 } ?: 1.0
        updateWeight(view.findViewById(R.id.progress_social), socialTime / divisor)
        updateWeight(view.findViewById(R.id.progress_stream), streamTime / divisor)
        updateWeight(view.findViewById(R.id.progress_neutral), neutralTime / divisor)
        updateWeight(view.findViewById(R.id.progress_productive), productiveTime / divisor)

        val appsContainer = view.findViewById<LinearLayout>(R.id.top_apps_container) ?: return
        val emptyApps = view.findViewById<TextView>(R.id.txt_apps_empty)
        appsContainer.removeAllViews()
        emptyApps?.visibility = if (usageData.isEmpty()) View.VISIBLE else View.GONE
        usageData.take(5).forEach { app ->
            val row = layoutInflater.inflate(R.layout.item_app_usage, appsContainer, false)
            row.findViewById<TextView>(R.id.txt_app_name).text = app.appName
            row.findViewById<TextView>(R.id.txt_app_time).text = formatDuration(app.minutes)
            appsContainer.addView(row)
        }
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