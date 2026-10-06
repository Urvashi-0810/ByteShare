package com.byteshare.android.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import com.byteshare.android.R
import com.byteshare.android.data.FriendEntry
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/** Profile sheet for a leaderboard entry. All stats come from the user's daily usage in Firebase. */
class FriendProfileDialogFragment : BottomSheetDialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.dialog_friend_profile, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val args = requireArguments()
        val usageAvailable = args.getBoolean(ARG_USAGE_AVAILABLE)
        val dailyRaw = args.getDouble(ARG_RAW_MINS)

        view.findViewById<TextView>(R.id.txt_profile_name).text = args.getString(ARG_NAME)
        view.findViewById<TextView>(R.id.txt_profile_avatar).text = args.getString(ARG_AVATAR)

        val email = args.getString(ARG_EMAIL).orEmpty()
        view.findViewById<TextView>(R.id.txt_profile_email).apply {
            text = email
            visibility = if (email.isBlank()) View.GONE else View.VISIBLE
        }

        val streak = args.getInt(ARG_STREAK)
        view.findViewById<TextView>(R.id.badge_profile_streak).apply {
            text = "🔥 $streak Day Streak"
            visibility = if (streak > 0) View.VISIBLE else View.GONE
        }

        val categories = listOf(
            "Social" to args.getDouble(ARG_SOCIAL_MINS),
            "Streaming" to args.getDouble(ARG_STREAM_MINS),
            "Neutral" to args.getDouble(ARG_NEUTRAL_MINS),
            "Productivity" to args.getDouble(ARG_PRODUCTIVE_MINS)
        )
        val top = categories.maxByOrNull { it.second }?.takeIf { it.second > 0.0 }

        fun stat(minutes: Double) = if (usageAvailable) formatMinutes(minutes) else "—"

        view.findViewById<TextView>(R.id.txt_profile_avg_daily).text = stat(dailyRaw)
        view.findViewById<TextView>(R.id.txt_profile_weighted).text =
            stat(args.getDouble(ARG_WEIGHTED_MINS))
        view.findViewById<TextView>(R.id.txt_profile_weekly_total).text = stat(dailyRaw)
        view.findViewById<TextView>(R.id.txt_profile_top_category).text =
            if (usageAvailable && top != null) top.first else "No data"
        view.findViewById<TextView>(R.id.txt_profile_top_category_time).text =
            if (top != null) stat(top.second) else "—"

        view.findViewById<ImageView>(R.id.btn_close_profile)?.setOnClickListener { dismiss() }
    }

    private fun formatMinutes(minutes: Double): String {
        val total = minutes.toInt()
        val hours = total / 60
        val remainder = total % 60
        return if (hours > 0) "${hours}h ${remainder}m" else "${remainder}m"
    }

    companion object {
        private const val ARG_NAME = "arg_name"
        private const val ARG_AVATAR = "arg_avatar"
        private const val ARG_EMAIL = "arg_email"
        private const val ARG_STREAK = "arg_streak"
        private const val ARG_RAW_MINS = "arg_raw_mins"
        private const val ARG_WEIGHTED_MINS = "arg_weighted_mins"
        private const val ARG_SOCIAL_MINS = "arg_social_mins"
        private const val ARG_STREAM_MINS = "arg_stream_mins"
        private const val ARG_NEUTRAL_MINS = "arg_neutral_mins"
        private const val ARG_PRODUCTIVE_MINS = "arg_productive_mins"
        private const val ARG_USAGE_AVAILABLE = "arg_usage_available"

        fun show(
            fragmentManager: androidx.fragment.app.FragmentManager,
            entry: FriendEntry,
            avatar: String
        ) {
            val dialog = FriendProfileDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_NAME, entry.name)
                    putString(ARG_AVATAR, avatar)
                    putString(ARG_EMAIL, entry.email)
                    putInt(ARG_STREAK, entry.streak)
                    putDouble(ARG_RAW_MINS, entry.rawMinutes)
                    putDouble(ARG_WEIGHTED_MINS, entry.weightedMinutes)
                    putDouble(ARG_SOCIAL_MINS, entry.socialMinutes)
                    putDouble(ARG_STREAM_MINS, entry.streamMinutes)
                    putDouble(ARG_NEUTRAL_MINS, entry.neutralMinutes)
                    putDouble(ARG_PRODUCTIVE_MINS, entry.productiveMinutes)
                    putBoolean(ARG_USAGE_AVAILABLE, entry.usageAvailable)
                }
            }
            dialog.show(fragmentManager, "FriendProfileDialogFragment")
        }
    }
}
