package com.example.byteshare.ui.fragments

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.byteshare.R
import com.example.byteshare.data.UsageStatsCollector
import com.example.byteshare.logic.AppUsage

class MeFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_me, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        if (hasUsageStatsPermission(requireContext())) {
            updateUsageUI(view)
        } else {
            // Request permission or show a prompt
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
    }

    private fun hasUsageStatsPermission(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun updateUsageUI(view: View) {
        val usageData = UsageStatsCollector.collectTodayUsage(requireContext())
        
        val socialTime = usageData.filter { it.category == "social" }.sumOf { it.minutes }
        val streamTime = usageData.filter { it.category == "stream" }.sumOf { it.minutes }
        val neutralTime = usageData.filter { it.category == "neutral" }.sumOf { it.minutes }
        val productiveTime = usageData.filter { it.category == "productive" }.sumOf { it.minutes }
        
        val totalMinutes = socialTime + streamTime + neutralTime + productiveTime
        
        // Update Time Texts
        view.findViewById<TextView>(R.id.txt_social_time).text = formatTime(socialTime)
        view.findViewById<TextView>(R.id.txt_stream_time).text = formatTime(streamTime)
        view.findViewById<TextView>(R.id.txt_neutral_time).text = formatTime(neutralTime)
        view.findViewById<TextView>(R.id.txt_productive_time).text = formatTime(productiveTime)
        
        // Update Progress Bar Weights
        if (totalMinutes > 0) {
            updateWeight(view.findViewById(R.id.progress_social), socialTime / totalMinutes)
            updateWeight(view.findViewById(R.id.progress_stream), streamTime / totalMinutes)
            updateWeight(view.findViewById(R.id.progress_neutral), neutralTime / totalMinutes)
            updateWeight(view.findViewById(R.id.progress_productive), productiveTime / totalMinutes)
        }
        
        // Update App List (Bifurcation)
        val appListContainer = view.findViewById<LinearLayout>(R.id.app_list_container)
        appListContainer.removeAllViews()
        
        // Show top 5 apps
        usageData.take(5).forEach { app ->
            val itemView = layoutInflater.inflate(R.layout.item_app_usage, appListContainer, false)
            itemView.findViewById<TextView>(R.id.txt_app_name).text = app.appName
            itemView.findViewById<TextView>(R.id.txt_app_time).text = formatMinutes(app.minutes)
            appListContainer.addView(itemView)
        }
    }

    private fun formatTime(minutes: Double): String {
        val h = (minutes / 60).toInt()
        val m = (minutes % 60).toInt()
        return "${h}h ${m}m"
    }

    private fun formatMinutes(minutes: Double): String {
        return "${minutes.toInt()}m"
    }

    private fun updateWeight(view: View, weight: Double) {
        val params = view.layoutParams as LinearLayout.LayoutParams
        params.weight = weight.toFloat()
        view.layoutParams = params
    }
}