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
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.byteshare.R
import com.example.byteshare.data.Crew
import com.example.byteshare.data.CrewRepository
import com.example.byteshare.data.UsageStatsCollector

class CrewDetailFragment : Fragment() {

    private var crewId: String? = null

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

        val crews = CrewRepository.getCrews()
        val crew = crews.find { it.id == crewId } ?: crews.firstOrNull() ?: Crew(
            id = "1", name = "Sda", emoji = "🍕", memberCount = 4, totalBill = 2000.0, oweAmount = 600.0, inviteCode = "A56KT4"
        )

        // Back button
        view.findViewById<ImageView>(R.id.btn_back).setOnClickListener {
            parentFragmentManager.popBackStack()
        }

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
            "http://byteshare.app/join/${crew.inviteCode}"

        // Populate Bill Split Table
        populateBillSplitTable(view, crew)

        // Populate Why You Pay breakdown from live phone stats
        populateWhyYouPaySection(view, crew)
    }

    private fun populateBillSplitTable(view: View, crew: Crew) {
        val container = view.findViewById<LinearLayout>(R.id.member_rows_container)
        container.removeAllViews()

        val mockMembers = listOf(
            MemberRow("🐼", "Rahul", 0.50, (crew.totalBill * 0.50 / 4 * 2).toInt(), isYou = false),
            MemberRow("🦄", "Priya", 0.80, (crew.totalBill * 0.80 / 4 * 2).toInt(), isYou = false),
            MemberRow("🐯", "You", 1.20, crew.oweAmount.toInt(), isYou = true),
            MemberRow("🐯", "Arjun", 1.50, (crew.totalBill * 1.50 / 4 * 2).toInt(), isYou = false)
        )

        for (m in mockMembers) {
            val rowView = layoutInflater.inflate(R.layout.item_member_split, container, false)
            rowView.findViewById<TextView>(R.id.txt_member_avatar).text = m.avatar
            rowView.findViewById<TextView>(R.id.txt_member_name).text = m.name
            rowView.findViewById<TextView>(R.id.txt_member_mult).text = String.format("%.2f", m.mult)
            rowView.findViewById<TextView>(R.id.txt_member_pays).text = "₹${m.pays}"

            if (m.isYou) {
                rowView.findViewById<TextView>(R.id.badge_you).visibility = View.VISIBLE
                rowView.findViewById<LinearLayout>(R.id.row_member_container)
                    .setBackgroundColor(Color.parseColor("#F2FCE8")) // Light green highlight
            }

            container.addView(rowView)
        }

        view.findViewById<TextView>(R.id.txt_split_total).text = "₹${crew.totalBill.toInt()}"
    }

    private fun populateWhyYouPaySection(view: View, crew: Crew) {
        val oweInt = crew.oweAmount.toInt()
        view.findViewById<TextView>(R.id.txt_why_you_pay_title).text = "Why you pay ₹$oweInt"

        // Fetch live stats from phone
        val usageData = UsageStatsCollector.collectTodayUsage(requireContext())
        val socialMins = usageData.filter { it.category == "social" }.sumOf { it.minutes }
        val streamMins = usageData.filter { it.category == "stream" }.sumOf { it.minutes }
        val neutralMins = usageData.filter { it.category == "neutral" }.sumOf { it.minutes }
        val prodMins = usageData.filter { it.category == "productive" }.sumOf { it.minutes }

        val socialHours = if (socialMins > 0) String.format("%.1fh", socialMins / 60.0) else "4.3h"
        val streamHours = if (streamMins > 0) String.format("%.1fh", streamMins / 60.0) else "2.8h"
        val neutralHours = if (neutralMins > 0) String.format("%.1fh", neutralMins / 60.0) else "1.5h"
        val prodHours = if (prodMins > 0) String.format("%.1fh", prodMins / 60.0) else "1.3h"

        view.findViewById<TextView>(R.id.txt_social_hours).text = socialHours
        view.findViewById<TextView>(R.id.txt_stream_hours).text = streamHours
        view.findViewById<TextView>(R.id.txt_neutral_hours).text = neutralHours
        view.findViewById<TextView>(R.id.txt_productive_hours).text = prodHours

        val rawTotalHours = (socialMins + streamMins + neutralMins + prodMins) / 60.0
        val rawDisplay = if (rawTotalHours > 0) String.format("%.1fh", rawTotalHours) else "9.8h"

        val weightedTotal = (socialMins * 2.0 + streamMins * 1.5 + neutralMins * 1.0 + prodMins * 0.5)
        val weightedDisplay = if (weightedTotal > 0) String.format("%.1f", weightedTotal) else "889.5"

        view.findViewById<TextView>(R.id.txt_raw_time_sum).text = rawDisplay
        view.findViewById<TextView>(R.id.txt_weighted_sum).text = weightedDisplay
        view.findViewById<TextView>(R.id.txt_mult_badge).text = "×1.20"
    }

    private fun shareInvite(crewName: String, code: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Join $crewName on ByteShare!")
            putExtra(
                Intent.EXTRA_TEXT,
                "Hey! Join my crew '$crewName' on ByteShare using invite code: $code\nhttp://byteshare.app/join/$code"
            )
        }
        startActivity(Intent.createChooser(intent, "Invite via Gmail / Message"))
    }

    private data class MemberRow(
        val avatar: String,
        val name: String,
        val mult: Double,
        val pays: Int,
        val isYou: Boolean
    )

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