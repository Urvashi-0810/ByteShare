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
        view.findViewById<Button>(R.id.btn_grant_usage).setOnClickListener {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-check on return from settings
        view?.let { v ->
            val granted = UsageStatsCollector.hasPermission(requireContext())
            v.findViewById<View>(R.id.usage_permission_card).visibility =
                if (granted) View.GONE else View.VISIBLE
            if (granted) updateUsageUI(v)
        }

        // Load AdMob Banner Ad
        val adView = view.findViewById<AdView>(R.id.ad_view_stats)
        val adRequest = AdRequest.Builder().build()
        adView?.loadAd(adRequest)
    }

    private fun updateUsageUI(view: View) {
        val usageData = UsageStatsCollector.collectTodayUsage(requireContext())
        
        val socialTime = usageData.filter { it.category == "social" }.sumOf { it.minutes }
        val streamTime = usageData.filter { it.category == "stream" }.sumOf { it.minutes }
        val neutralTime = usageData.filter { it.category == "neutral" }.sumOf { it.minutes }
        val productiveTime = usageData.filter { it.category == "productive" }.sumOf { it.minutes }
        
        val totalMinutes = socialTime + streamTime + neutralTime + productiveTime
        
        view.findViewById<TextView>(R.id.txt_social_time).text = "${socialTime.toInt()}m"
        view.findViewById<TextView>(R.id.txt_stream_time).text = "${streamTime.toInt()}m"
        
        if (totalMinutes > 0) {
            updateWeight(view.findViewById(R.id.progress_social), socialTime / totalMinutes)
            updateWeight(view.findViewById(R.id.progress_stream), streamTime / totalMinutes)
            updateWeight(view.findViewById(R.id.progress_neutral), neutralTime / totalMinutes)
            updateWeight(view.findViewById(R.id.progress_productive), productiveTime / totalMinutes)
        }
    }

    private fun updateWeight(view: View, weight: Double) {
        val params = view.layoutParams as LinearLayout.LayoutParams
        params.weight = weight.toFloat()
        view.layoutParams = params
    }
}