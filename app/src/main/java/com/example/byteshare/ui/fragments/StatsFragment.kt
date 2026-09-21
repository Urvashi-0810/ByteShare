package com.example.byteshare.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.byteshare.R
import com.example.byteshare.data.UsageStatsCollector

class StatsFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_stats, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        updateUsageUI(view)
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