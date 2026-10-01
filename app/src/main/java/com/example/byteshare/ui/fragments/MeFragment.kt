package com.example.byteshare.ui.fragments

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.byteshare.LoginActivity
import com.example.byteshare.R
import com.example.byteshare.data.AuthRepository
import com.example.byteshare.data.FirebaseCrewRepository
import com.example.byteshare.data.UsageStatsCollector

class MeFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_me, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<View>(R.id.card_upgrade_pro)?.setOnClickListener {
            PaywallDialogFragment.show(parentFragmentManager)
        }
        setupProfile(view)
        loadCrewStats(view)
        view.findViewById<View>(R.id.btn_grant_usage_me).setOnClickListener {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
    }

    private fun setupProfile(view: View) {
        val user = AuthRepository.currentUser
        val name = user?.displayName ?: "You"
        view.findViewById<TextView>(R.id.txt_user_name).text = name
        view.findViewById<TextView>(R.id.txt_user_email).text = user?.email ?: ""
        view.findViewById<TextView>(R.id.txt_avatar_initial).text =
            name.firstOrNull()?.uppercase() ?: "?"

        view.findViewById<ImageView>(R.id.btn_sign_out).setOnClickListener {
            AuthRepository.signOut(requireContext()) {
                if (isAdded) {
                    startActivity(Intent(requireContext(), LoginActivity::class.java))
                    requireActivity().finish()
                }
            }
        }
    }

    private fun loadCrewStats(view: View) {
        FirebaseCrewRepository.fetchMyCrewsOnce { crews ->
            if (!isAdded) return@fetchMyCrewsOnce
            val count = crews.size
            view.findViewById<TextView>(R.id.txt_crew_count).text = when (count) {
                0 -> "No crews yet"
                1 -> "Across 1 crew"
                else -> "Across $count crews"
            }
            val totalOwed = crews.sumOf { it.oweAmount }.toInt()
            view.findViewById<TextView>(R.id.txt_total_owed).text = "₹$totalOwed"
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh when returning from the Usage Access settings screen
        view?.let { v ->
            val granted = UsageStatsCollector.hasPermission(requireContext())
            v.findViewById<View>(R.id.usage_permission_card).visibility =
                if (granted) View.GONE else View.VISIBLE
            if (granted) updateUsageUI(v)
        }
    }

    private fun updateUsageUI(view: View) {
        val usageData = UsageStatsCollector.collectRollingSevenDayUsage(requireContext())
        
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
        val divisor = totalMinutes.takeIf { it > 0.0 } ?: 1.0
        updateWeight(view.findViewById(R.id.progress_social), socialTime / divisor)
        updateWeight(view.findViewById(R.id.progress_stream), streamTime / divisor)
        updateWeight(view.findViewById(R.id.progress_neutral), neutralTime / divisor)
        updateWeight(view.findViewById(R.id.progress_productive), productiveTime / divisor)
        
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