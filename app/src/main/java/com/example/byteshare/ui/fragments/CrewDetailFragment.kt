package com.example.byteshare.ui.fragments

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.byteshare.R
import com.example.byteshare.data.AuthRepository
import com.example.byteshare.data.Crew
import com.example.byteshare.data.FirebaseCrewRepository
import com.example.byteshare.data.FriendEntry
import com.example.byteshare.data.UsageStatsCollector
import com.example.byteshare.data.UserRepository
import com.example.byteshare.logic.FameEngine
import com.google.firebase.database.ValueEventListener

class CrewDetailFragment : Fragment() {

    private var crewId: String? = null
    private var crewListener: ValueEventListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        crewId = arguments?.getString(ARG_CREW_ID)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_crew_detail, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Back button
        view.findViewById<ImageView>(R.id.btn_back).setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        val id = crewId ?: return

        // Attach a real-time listener on this specific crew
        crewListener = FirebaseCrewRepository.listenForCrew(id) { crew ->
            if (isAdded && crew != null) {
                bindCrewData(view, crew)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Clean up Firebase listener
        val id = crewId
        if (id != null && crewListener != null) {
            FirebaseCrewRepository.removeCrewListener(id, crewListener!!)
        }
        crewListener = null
    }

    private fun bindCrewData(view: View, crew: Crew) {
        // Header info
        view.findViewById<TextView>(R.id.txt_detail_emoji).text = crew.emoji
        view.findViewById<TextView>(R.id.txt_detail_name).text = crew.name
        view.findViewById<TextView>(R.id.txt_detail_subtitle).text =
            "${crew.memberCount} members · code ${crew.inviteCode}"

        // Share button
        val shareAction = View.OnClickListener {
            shareInvite(crew.name, crew.inviteCode)
        }
        view.findViewById<ImageView>(R.id.btn_share).setOnClickListener(shareAction)
        view.findViewById<Button>(R.id.btn_send_invite).setOnClickListener(shareAction)

        // Bill card
        val billInt = crew.totalBill.toInt()
        val spreadInt = (crew.totalBill / crew.memberCount).toInt()
        view.findViewById<TextView>(R.id.txt_detail_total_bill).text = "₹$billInt"
        view.findViewById<TextView>(R.id.txt_stat_members).text = crew.memberCount.toString()
        view.findViewById<TextView>(R.id.txt_stat_covered).text = "₹$billInt"
        view.findViewById<TextView>(R.id.txt_stat_spread).text = "₹$spreadInt"

        // Invite URL
        view.findViewById<TextView>(R.id.txt_invite_url).text =
            "Invite code: ${crew.inviteCode}"

        // Populate Bill Split Table
        populateBillSplitTable(view, crew)

        // Populate Why You Pay breakdown from live phone stats
        populateWhyYouPaySection(view, crew)
    }

    private fun populateBillSplitTable(view: View, crew: Crew) {
        val container = view.findViewById<LinearLayout>(R.id.member_rows_container)
        container.removeAllViews()
        view.findViewById<TextView>(R.id.txt_split_total).text = "₹${crew.totalBill.toInt()}"

        val memberUids = crew.members.keys.toList()
        if (memberUids.isEmpty()) return

        val myUid = AuthRepository.currentUserId
        val entries = mutableListOf<FriendEntry>()
        var pending = memberUids.size

        for (uid in memberUids) {
            UserRepository.fetchFriendEntry(uid) { entry ->
                entries.add(entry ?: FriendEntry(uid, "Member", 0.0, 0.0))
                if (--pending == 0 && isAdded) {
                    renderMemberRows(view, crew, entries, myUid)
                }
            }
        }
    }

    private fun renderMemberRows(
        view: View,
        crew: Crew,
        entries: List<FriendEntry>,
        myUid: String?
    ) {
        val container = view.findViewById<LinearLayout>(R.id.member_rows_container)
        container.removeAllViews()

        // Lowest weighted usage = rank 1 = smallest bill multiplier
        val sorted = entries.sortedBy { it.weightedMinutes }
        val mults = FameEngine.multipliersFor(sorted.size)
        val perHead = crew.totalBill / sorted.size

        sorted.forEachIndexed { index, entry ->
            val isYou = entry.uid == myUid
            val mult = mults[index]
            val pays = (perHead * mult).toInt()
            val name = if (isYou) "You" else entry.name

            val rowView = layoutInflater.inflate(R.layout.item_member_split, container, false)
            rowView.findViewById<TextView>(R.id.txt_member_avatar).text =
                name.firstOrNull()?.uppercase() ?: "?"
            rowView.findViewById<TextView>(R.id.txt_member_name).text = name
            rowView.findViewById<TextView>(R.id.txt_member_mult).text = String.format("%.2f", mult)
            rowView.findViewById<TextView>(R.id.txt_member_pays).text = "₹$pays"

            if (isYou) {
                rowView.findViewById<TextView>(R.id.badge_you).visibility = View.VISIBLE
                rowView.findViewById<LinearLayout>(R.id.row_member_container)
                    .setBackgroundColor(Color.parseColor("#F2FCE8")) // Light green highlight
                view.findViewById<TextView>(R.id.txt_mult_badge).text =
                    String.format("×%.2f", mult)
                view.findViewById<TextView>(R.id.txt_why_you_pay_title).text = "Why you pay ₹$pays"
            }

            container.addView(rowView)
        }
    }

    private fun populateWhyYouPaySection(view: View, crew: Crew) {
        view.findViewById<TextView>(R.id.txt_why_you_pay_title).text =
            "Why you pay ₹${crew.oweAmount.toInt()}"

        // Live stats from this phone; renderMemberRows overrides title/badge with real rank data
        val usageData = UsageStatsCollector.collectTodayUsage(requireContext())
        val socialMins = usageData.filter { it.category == "social" }.sumOf { it.minutes }
        val streamMins = usageData.filter { it.category == "stream" }.sumOf { it.minutes }
        val neutralMins = usageData.filter { it.category == "neutral" }.sumOf { it.minutes }
        val prodMins = usageData.filter { it.category == "productive" }.sumOf { it.minutes }

        fun hours(m: Double) = String.format("%.1fh", m / 60.0)
        view.findViewById<TextView>(R.id.txt_social_hours).text = hours(socialMins)
        view.findViewById<TextView>(R.id.txt_stream_hours).text = hours(streamMins)
        view.findViewById<TextView>(R.id.txt_neutral_hours).text = hours(neutralMins)
        view.findViewById<TextView>(R.id.txt_productive_hours).text = hours(prodMins)

        view.findViewById<TextView>(R.id.txt_raw_time_sum).text =
            hours(socialMins + streamMins + neutralMins + prodMins)
        view.findViewById<TextView>(R.id.txt_weighted_sum).text = String.format(
            "%.1f", socialMins * 2.0 + streamMins * 1.5 + neutralMins * 1.0 + prodMins * 0.5
        )
    }

    private fun shareInvite(crewName: String, code: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Join $crewName on ByteShare!")
            putExtra(
                Intent.EXTRA_TEXT,
                "Hey! Join my crew '$crewName' on ByteShare.\nOpen the app \u2192 Crews \u2192 Join a crew \u2192 enter code: $code"
            )
        }
        startActivity(Intent.createChooser(intent, "Invite via Gmail / Message"))
    }

    companion object {
        private const val ARG_CREW_ID = "arg_crew_id"

        fun newInstance(crewId: String): CrewDetailFragment {
            return CrewDetailFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_CREW_ID, crewId)
                }
            }
        }
    }
}