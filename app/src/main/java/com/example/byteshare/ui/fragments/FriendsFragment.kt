package com.example.byteshare.ui.fragments

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.example.byteshare.R
import com.example.byteshare.data.FriendEntry
import com.example.byteshare.data.FriendsRepository
import com.example.byteshare.data.UserRepository

class FriendsFragment : Fragment() {

    private val requestContacts = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        view?.let { bindPermissionCard(it) }
        if (granted) view?.let { loadLeaderboard(it) }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_friends, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<Button>(R.id.btn_allow_contacts).setOnClickListener {
            requestContacts.launch(Manifest.permission.READ_CONTACTS)
        }
        view.findViewById<ImageView>(R.id.btn_invite_friend).setOnClickListener {
            shareAppInvite()
        }

        bindPermissionCard(view)
        loadLeaderboard(view)
    }

    private fun bindPermissionCard(view: View) {
        val granted = FriendsRepository.hasContactsPermission(requireContext())
        view.findViewById<View>(R.id.contacts_permission_card).visibility =
            if (granted) View.GONE else View.VISIBLE
    }

    private fun loadLeaderboard(view: View) {
        FriendsRepository.discoverFriends(requireContext()) { friends ->
            if (isAdded) renderLeaderboard(view, friends)
        }
    }

    private fun renderLeaderboard(view: View, friends: List<FriendEntry>) {
        val container = view.findViewById<LinearLayout>(R.id.leaderboard_container)
        val empty = view.findViewById<TextView>(R.id.txt_leaderboard_empty)
        container.removeAllViews()

        // Include self so the user sees their own rank
        val self = com.example.byteshare.data.AuthRepository.currentUser
        val selfEntry = if (self != null) {
            val usage = com.example.byteshare.data.UsageStatsCollector
                .collectTodayUsage(requireContext())
            val weighted = usage.sumOf {
                it.minutes * when (it.category) {
                    "social" -> 2.0; "stream" -> 1.5; "productive" -> 0.5; else -> 1.0
                }
            }
            FriendEntry(
                uid = self.uid,
                name = "You",
                rawMinutes = usage.sumOf { it.minutes },
                weightedMinutes = weighted
            )
        } else null

        val ranked = (friends + listOfNotNull(selfEntry))
            .distinctBy { it.uid }
            .sortedByDescending { it.weightedMinutes }

        empty.visibility = if (ranked.size <= 1) View.VISIBLE else View.GONE

        val rankColors = intArrayOf(R.color.rank_1, R.color.rank_2, R.color.rank_3)
        ranked.forEachIndexed { index, entry ->
            val item = layoutInflater.inflate(R.layout.item_leaderboard, container, false)
            item.findViewById<TextView>(R.id.txt_rank_number).text = (index + 1).toString()
            item.findViewById<TextView>(R.id.txt_friend_name).text = entry.name
            item.findViewById<TextView>(R.id.txt_friend_raw).text =
                "${entry.rawMinutes.toInt()}m raw usage"
            item.findViewById<TextView>(R.id.txt_friend_weighted).text =
                "${entry.weightedMinutes.toInt()}m"
            val rankLabel = item.findViewById<TextView>(R.id.txt_friend_rank)
            rankLabel.text = "RANK ${index + 1}"
            rankLabel.setTextColor(
                requireContext().getColor(rankColors.getOrElse(index) { R.color.gray_ink })
            )
            container.addView(item)
        }
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
}