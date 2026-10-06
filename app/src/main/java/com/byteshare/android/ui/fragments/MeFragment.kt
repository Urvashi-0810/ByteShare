package com.byteshare.android.ui.fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.byteshare.android.LoginActivity
import com.byteshare.android.R
import com.byteshare.android.data.AccountRepository
import com.byteshare.android.data.AuthRepository
import com.byteshare.android.data.FirebaseCrewRepository
import com.byteshare.android.data.UsageStatsCollector
import com.byteshare.android.data.XpRepository
import com.byteshare.android.logic.AdManager
import com.byteshare.android.ui.PermissionDisclosures
import com.google.firebase.database.ValueEventListener

class MeFragment : Fragment() {

    private var xpListener: ValueEventListener? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_me, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<View>(R.id.card_upgrade_pro)?.setOnClickListener {
            PaywallDialogFragment.show(parentFragmentManager)
        }
        setupProfile(view)
        setupXpAndStreak(view)
        loadCrewStats(view)
        view.findViewById<View>(R.id.btn_grant_usage_me)?.setOnClickListener {
            PermissionDisclosures.showUsageAccess(requireContext())
        }
        view.findViewById<View>(R.id.btn_privacy_policy)?.setOnClickListener {
            PermissionDisclosures.openPrivacyPolicy(requireContext())
        }
        view.findViewById<View>(R.id.btn_delete_account)?.setOnClickListener {
            confirmDeleteAccount()
        }
    }

    private fun confirmDeleteAccount() {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete your account?")
            .setMessage(
                "This permanently deletes your profile, screen time history, XP, streak, challenge " +
                    "progress and crew memberships. It can't be undone.\n\n" +
                    "Payment records are kept as required by law. If you have ByteShare Pro, cancel it " +
                    "in Google Play → Subscriptions; deleting your account doesn't cancel it."
            )
            .setPositiveButton("Delete") { _, _ -> deleteAccount() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteAccount() {
        val activity = requireActivity()
        val progress = AlertDialog.Builder(activity)
            .setMessage("Deleting your account…")
            .setCancelable(false)
            .show()
        AccountRepository.deleteAccount(activity) { error ->
            progress.dismiss()
            if (error != null) {
                Toast.makeText(activity, error, Toast.LENGTH_LONG).show()
                return@deleteAccount
            }
            Toast.makeText(activity, "Your account has been deleted.", Toast.LENGTH_LONG).show()
            activity.startActivity(
                Intent(activity, LoginActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            )
            activity.finish()
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

    private fun setupXpAndStreak(view: View) {
        val badgeStreak = view.findViewById<TextView>(R.id.badge_user_streak)
        val badgeXp = view.findViewById<TextView>(R.id.badge_user_xp)
        val btnEarnXp = view.findViewById<Button>(R.id.btn_earn_xp_ad)

        // Seed UI from Firebase, then attach real-time listener
        XpRepository.fetchOnce { xp, streak, level ->
            if (isAdded) {
                badgeStreak?.text = "🔥 $streak Day Streak"
                badgeXp?.text = "⭐️ $xp XP (Level $level)"
            }
        }

        // Listen for real-time Firebase XP updates
        xpListener = XpRepository.listenForXp { xp, streak, level ->
            if (isAdded) {
                badgeStreak?.text = "🔥 $streak Day Streak"
                badgeXp?.text = "⭐️ $xp XP (Level $level)"
            }
        }

        // Rewarded Video Ad button to earn +20 XP
        btnEarnXp?.setOnClickListener {
            AdManager.showRewardedAd(requireActivity()) { _ ->
                XpRepository.addXp(requireContext(), 20, "Profile Rewarded Ad Watch") { newXp ->
                    val newLevel = XpRepository.getLevel(newXp)
                    badgeXp?.text = "⭐️ $newXp XP (Level $newLevel)"
                    Toast.makeText(requireContext(), "🎉 Earned +20 Social XP! Total: $newXp XP", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Clean up listener via repository helper
        xpListener?.let { XpRepository.removeListener(it) }
        xpListener = null
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
        // Record daily activity to maintain streak
        XpRepository.recordDailyActivity()
        view?.let { v ->
            val granted = UsageStatsCollector.hasPermission(requireContext())
            v.findViewById<View>(R.id.usage_permission_card)?.visibility =
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
        
        view.findViewById<TextView>(R.id.txt_social_time).text = formatTime(socialTime)
        view.findViewById<TextView>(R.id.txt_stream_time).text = formatTime(streamTime)
        view.findViewById<TextView>(R.id.txt_neutral_time).text = formatTime(neutralTime)
        view.findViewById<TextView>(R.id.txt_productive_time).text = formatTime(productiveTime)
        
        val divisor = totalMinutes.takeIf { it > 0.0 } ?: 1.0
        updateWeight(view.findViewById(R.id.progress_social), socialTime / divisor)
        updateWeight(view.findViewById(R.id.progress_stream), streamTime / divisor)
        updateWeight(view.findViewById(R.id.progress_neutral), neutralTime / divisor)
        updateWeight(view.findViewById(R.id.progress_productive), productiveTime / divisor)
        
        val appListContainer = view.findViewById<LinearLayout>(R.id.app_list_container) ?: return
        appListContainer.removeAllViews()
        
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

    private fun updateWeight(view: View?, weight: Double) {
        if (view == null) return
        val params = view.layoutParams as LinearLayout.LayoutParams
        params.weight = weight.toFloat()
        view.layoutParams = params
    }
}