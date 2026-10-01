package com.example.byteshare.ui.fragments

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import com.example.byteshare.R
import com.example.byteshare.data.AuthRepository
import com.example.byteshare.data.FriendEntry
import com.example.byteshare.data.FriendsRepository
import com.example.byteshare.data.NudgeResult
import com.example.byteshare.data.ReceivedNudge
import com.example.byteshare.data.UserRepository
import com.example.byteshare.logic.AdManager
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import com.google.firebase.database.ValueEventListener

class FriendsFragment : Fragment() {

    private val nudgesSentToday = mutableMapOf<String, Int>()
    private var rankedEntries: List<FriendEntry> = emptyList()
    private var nudgeListener: ValueEventListener? = null
    private var nudgeListenerUid: String? = null
    private var nudgeListenerDay: String? = null

    private val requestContacts = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        val currentView = view ?: return@registerForActivityResult
        bindPermissionCard(currentView)
        refreshLeaderboard(currentView)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_friends, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Contact Permission & Invite Buttons
        view.findViewById<Button>(R.id.btn_allow_contacts)?.setOnClickListener {
            requestContacts.launch(Manifest.permission.READ_CONTACTS)
        }
        view.findViewById<ImageView>(R.id.btn_invite_friend)?.setOnClickListener {
            shareAppInvite()
        }

        bindPermissionCard(view)

        view.findViewById<EditText>(R.id.edit_search_friends)?.doAfterTextChanged {
            renderRows(view)
        }

        // Load AdMob Native Ad
        val adContainer = view.findViewById<FrameLayout>(R.id.native_ad_container)
        if (adContainer != null) {
            AdManager.loadNativeAd(requireContext()) { nativeAd ->
                if (!isAdded) return@loadNativeAd
                val adView = layoutInflater.inflate(R.layout.item_native_ad, adContainer, false) as NativeAdView
                populateNativeAdView(nativeAd, adView)
                adContainer.removeAllViews()
                adContainer.addView(adView)
            }
        }

        // Firebase Nudge Listener
        AuthRepository.currentUserId?.let { uid ->
            val dayKey = UserRepository.todayKey()
            nudgeListenerUid = uid
            nudgeListenerDay = dayKey
            nudgeListener = UserRepository.listenForReceivedNudges(uid, dayKey) { nudges ->
                if (isAdded && this.view === view) renderNudgeInbox(view, nudges)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val currentView = view ?: return
        bindPermissionCard(currentView)
        refreshLeaderboard(currentView)
    }

    override fun onDestroyView() {
        val uid = nudgeListenerUid
        val dayKey = nudgeListenerDay
        val listener = nudgeListener
        if (uid != null && dayKey != null && listener != null) {
            UserRepository.removeReceivedNudgeListener(uid, dayKey, listener)
        }
        nudgeListener = null
        nudgeListenerUid = null
        nudgeListenerDay = null
        super.onDestroyView()
    }

    private fun bindPermissionCard(view: View) {
        val granted = FriendsRepository.hasContactsPermission(requireContext())
        view.findViewById<View>(R.id.contacts_permission_card)?.visibility =
            if (granted) View.GONE else View.VISIBLE
    }

    private fun loadLeaderboard(view: View) {
        FriendsRepository.discoverFriends(requireContext()) { friends ->
            if (!isAdded) return@discoverFriends
            val selfUid = AuthRepository.currentUserId
            if (selfUid == null) {
                renderLeaderboard(view, friends, null)
            } else {
                UserRepository.fetchFriendEntry(selfUid) { self ->
                    if (isAdded) renderLeaderboard(view, friends, self?.copy(name = "You"))
                }
            }
        }
    }

    private fun refreshLeaderboard(view: View) {
        UserRepository.syncProfileAndUsage(requireContext()) { success ->
            if (!isAdded || this.view !== view) return@syncProfileAndUsage
            if (!success) {
                Toast.makeText(
                    requireContext(),
                    "Usage sync failed. Check your connection and Firebase rules.",
                    Toast.LENGTH_LONG
                ).show()
            }
            loadLeaderboard(view)
        }
    }

    private fun renderLeaderboard(view: View, friends: List<FriendEntry>, selfEntry: FriendEntry?) {
        rankedEntries = (friends + listOfNotNull(selfEntry))
            .distinctBy { it.uid }
            .sortedBy { it.weightedMinutes } // Lower weighted minutes wins top rank
        renderRows(view)
    }

    private fun renderRows(view: View) {
        val container = view.findViewById<LinearLayout>(R.id.leaderboard_container) ?: return
        val empty = view.findViewById<TextView>(R.id.txt_leaderboard_empty)
        container.removeAllViews()

        val query = view.findViewById<EditText>(R.id.edit_search_friends)?.text?.toString()?.trim().orEmpty()
        // Keep each entry's overall rank even when the list is filtered.
        val visible = rankedEntries.withIndex()
            .filter { query.isEmpty() || it.value.name.contains(query, ignoreCase = true) }

        empty?.text = if (rankedEntries.isEmpty()) {
            "No friends yet. Invite someone or join a crew!"
        } else {
            "No friends match \"$query\""
        }
        empty?.visibility = if (visible.isEmpty()) View.VISIBLE else View.GONE

        val rankColors = intArrayOf(R.color.rank_1, R.color.rank_2, R.color.rank_3)
        val currentUid = AuthRepository.currentUserId

        visible.forEach { (index, entry) ->
            val item = layoutInflater.inflate(R.layout.item_leaderboard, container, false)
            val avatar = AVATARS[Math.floorMod(entry.uid.hashCode(), AVATARS.size)]

            item.findViewById<TextView>(R.id.txt_rank_number).text = (index + 1).toString()
            item.findViewById<TextView>(R.id.txt_friend_name).text = entry.name

            // Scores are based on today's usage snapshot
            item.findViewById<TextView>(R.id.txt_friend_today_time).text = if (entry.usageAvailable) {
                "${formatMinutes(entry.rawMinutes)} today"
            } else {
                "Data unavailable"
            }
            item.findViewById<TextView>(R.id.txt_friend_weighted).text = formatMinutes(entry.weightedMinutes)

            val rankLabel = item.findViewById<TextView>(R.id.txt_friend_rank)
            rankLabel.text = "RANK ${index + 1}"
            rankLabel.setTextColor(
                requireContext().getColor(rankColors.getOrElse(index) { R.color.gray_ink })
            )

            // Nudge button logic
            val nudgeButton = item.findViewById<Button>(R.id.btn_nudge_friend)
            if (currentUid == entry.uid || currentUid == null) {
                nudgeButton?.visibility = View.GONE
            } else {
                val quotaKey = "${UserRepository.todayKey()}:${entry.uid}"
                val sentCount = nudgesSentToday[quotaKey] ?: 0
                nudgeButton?.text = if (sentCount == 0) "SEND NUDGE" else "NUDGE $sentCount/2"
                nudgeButton?.isEnabled = sentCount < 2
                nudgeButton?.setOnClickListener { showNudgeOptions(entry, nudgeButton, quotaKey) }
            }

            item.setOnClickListener {
                FriendProfileDialogFragment.show(parentFragmentManager, entry, avatar)
            }

            container.addView(item)
        }
    }

    private fun renderNudgeInbox(view: View, nudges: List<ReceivedNudge>) {
        val card = view.findViewById<View>(R.id.nudge_inbox_card) ?: return
        val messages = view.findViewById<LinearLayout>(R.id.nudge_inbox_messages) ?: return
        messages.removeAllViews()
        nudges.forEach { nudge ->
            val message = TextView(requireContext()).apply {
                text = "${nudge.senderName}: ${nudge.message}"
                setTextColor(requireContext().getColor(R.color.primary_ink))
                textSize = 14f
                setPadding(0, 4, 0, 4)
            }
            messages.addView(message)
        }
        card.visibility = if (nudges.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun showNudgeOptions(entry: FriendEntry, button: Button, quotaKey: String) {
        AlertDialog.Builder(requireContext())
            .setTitle("Nudge ${entry.name}")
            .setItems(NUDGE_MESSAGES) { _, which ->
                val senderUid = AuthRepository.currentUserId ?: return@setItems
                button.isEnabled = false
                val senderName = AuthRepository.currentUser?.displayName ?: "A friend"
                UserRepository.sendNudge(senderUid, entry.uid, senderName, NUDGE_MESSAGES[which]) { result ->
                    if (!isAdded || view == null) return@sendNudge
                    when (result) {
                        NudgeResult.SENT -> {
                            val sentCount = (nudgesSentToday[quotaKey] ?: 0) + 1
                            nudgesSentToday[quotaKey] = sentCount
                            button.text = "NUDGE $sentCount/2"
                            button.isEnabled = sentCount < 2
                            Toast.makeText(
                                requireContext(),
                                "Nudge sent to ${entry.name} ($sentCount/2 today)",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        NudgeResult.LIMIT_REACHED -> {
                            button.text = "MAX SENT TODAY"
                            button.isEnabled = false
                            Toast.makeText(requireContext(), "Two nudges sent to ${entry.name} today", Toast.LENGTH_SHORT).show()
                        }
                        NudgeResult.FAILED -> {
                            button.isEnabled = true
                            Toast.makeText(requireContext(), "Couldn't send nudge. Try again.", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun formatMinutes(minutes: Double): String {
        val total = minutes.toInt()
        val hours = total / 60
        val remainder = total % 60
        return if (hours > 0) "${hours}h ${remainder}m" else "${remainder}m"
    }

    private fun populateNativeAdView(nativeAd: NativeAd, adView: NativeAdView) {
        adView.headlineView = adView.findViewById(R.id.ad_headline)
        adView.bodyView = adView.findViewById(R.id.ad_body)
        adView.callToActionView = adView.findViewById(R.id.ad_call_to_action)
        adView.iconView = adView.findViewById(R.id.ad_app_icon)

        (adView.headlineView as? TextView)?.text = nativeAd.headline
        (adView.bodyView as? TextView)?.text = nativeAd.body
        (adView.callToActionView as? Button)?.text = nativeAd.callToAction ?: "Install Now"

        if (nativeAd.icon != null) {
            (adView.iconView as? ImageView)?.setImageDrawable(nativeAd.icon?.drawable)
            adView.iconView?.visibility = View.VISIBLE
        } else {
            adView.iconView?.visibility = View.GONE
        }

        adView.setNativeAd(nativeAd)
    }

    private fun shareAppInvite() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Join me on ByteShare!")
            putExtra(
                Intent.EXTRA_TEXT,
                "Your screen time pays the bill. Get ByteShare and join my crew — I'll send you an invite code!"
            )
        }
        startActivity(Intent.createChooser(intent, "Invite a friend"))
    }

    companion object {
        private val AVATARS = listOf("🐼", "🦄", "🐯", "🦊", "🐶", "🦋")
        private val NUDGE_MESSAGES = arrayOf(
            "That was your 5-minute break, right?",
            "Even the algorithm thinks you've had enough.",
            "Your thumb just hit overtime.",
            "Your future self called. It wants those hours back."
        )
    }
}