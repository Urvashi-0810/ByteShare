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
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.byteshare.R
import com.example.byteshare.data.AuthRepository
import com.example.byteshare.data.Crew
import com.example.byteshare.data.FirebaseCrewRepository
import com.example.byteshare.data.FriendEntry
import com.example.byteshare.data.UserRepository
import com.example.byteshare.logic.FameEngine
import com.google.firebase.database.ValueEventListener
import java.util.Locale
import kotlin.math.roundToLong

class CrewDetailFragment : Fragment() {

    private var crewId: String? = null
    private var crewListener: ValueEventListener? = null
    private var payableAmountMinor: Long? = null
    private var payableMultiplier: Double? = null
    private var memberLoadGeneration = 0

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

        val id = crewId ?: run {
            showCrewLoadError(view, "CREW ID MISSING", "Return to Crews and open the crew again.")
            return
        }

        // Attach a real-time listener on this specific crew
        crewListener = FirebaseCrewRepository.listenForCrew(id) { crew ->
            if (isAdded && this.view === view) {
                if (crew != null) bindCrewData(view, crew)
                else showCrewLoadError(
                    view,
                    "CREW DATA UNAVAILABLE",
                    "Couldn't load crew data. Check your connection and try again."
                )
            }
        }
    }

    override fun onDestroyView() {
        memberLoadGeneration++
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

        // Payment waits for all member scores so the displayed and charged shares match.
        view.findViewById<Button>(R.id.btn_pay_stripe)?.apply {
            isEnabled = false
            text = "Loading split…"
            setOnClickListener {
                payableAmountMinor?.let { amount ->
                    PayBillDialogFragment.show(
                        parentFragmentManager,
                        crew.name,
                        crew.id,
                        amount,
                        payableMultiplier ?: return@let
                    )
                }
            }
        }
    }

    private fun populateBillSplitTable(view: View, crew: Crew) {
        val container = view.findViewById<LinearLayout>(R.id.member_rows_container)
        container.removeAllViews()
        view.findViewById<TextView>(R.id.txt_split_total).text = "₹${crew.totalBill.toInt()}"

        val memberUids = crew.members.keys.toList()
        if (memberUids.isEmpty()) {
            showCrewLoadError(
                view,
                "CREW MEMBERS NOT FOUND",
                "No member records are available to calculate this split."
            )
            return
        }

        val myUid = AuthRepository.currentUserId
        val entries = mutableListOf<FriendEntry>()
        var pending = memberUids.size
        val requestGeneration = ++memberLoadGeneration

        for (uid in memberUids) {
            UserRepository.fetchFriendEntry(uid) { entry ->
                entries.add(entry ?: FriendEntry(
                    uid = uid,
                    name = "Member",
                    rawMinutes = 0.0,
                    weightedMinutes = 0.0,
                    usageAvailable = false
                ))
                if (--pending == 0 && isAdded && this.view === view &&
                    requestGeneration == memberLoadGeneration
                ) {
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

        if (entries.any { !it.usageAvailable }) {
            payableAmountMinor = null
            payableMultiplier = null
            showCrewLoadError(
                view,
                "WAITING FOR SCORES",
                "Every member needs a current seven-day usage sync before we can calculate the split."
            )
            return
        }

        // Lowest rolling weighted usage = rank 1 = smallest bill multiplier
        val sorted = entries.sortedWith(compareBy<FriendEntry> { it.weightedMinutes }.thenBy { it.uid })
        val baseline = FameEngine.multipliersFor(sorted.size)
        FirebaseCrewRepository.saveBaselineMultipliersIfChanged(
            crew.id,
            sorted.mapIndexed { index, entry -> entry.uid to baseline[index] }.toMap()
        )
        val reductions = sorted.map { crew.multiplierReductions[it.uid] ?: 0.0 }
        val mults = FameEngine.applyMultiplierReductions(baseline, reductions)
        val sharesMinor = FameEngine.allocateSharesMinorUnits(crew.totalBill, mults)
        val baseShareMinor = (crew.totalBill * 100.0 / sorted.size).roundToLong()
        payableAmountMinor = null
        payableMultiplier = null

        sorted.forEachIndexed { index, entry ->
            val isYou = entry.uid == myUid
            val mult = mults[index]
            val paysMinor = sharesMinor[index]
            val name = if (isYou) "You" else entry.name

            val rowView = layoutInflater.inflate(R.layout.item_member_split, container, false)
            rowView.findViewById<TextView>(R.id.txt_member_avatar).text =
                name.firstOrNull()?.uppercase() ?: "?"
            rowView.findViewById<TextView>(R.id.txt_member_name).text = name
            rowView.findViewById<TextView>(R.id.txt_member_mult).text = String.format("%.2f", mult)
            rowView.findViewById<TextView>(R.id.txt_member_pays).text = formatMoney(paysMinor)

            if (isYou) {
                populateWhyYouPaySection(view, entry)
                rowView.findViewById<TextView>(R.id.badge_you).visibility = View.VISIBLE
                rowView.findViewById<LinearLayout>(R.id.row_member_container)
                    .setBackgroundColor(Color.parseColor("#F2FCE8")) // Light green highlight
                view.findViewById<TextView>(R.id.txt_mult_badge).text =
                    String.format("×%.2f", mult)
                view.findViewById<TextView>(R.id.txt_why_you_pay_title).text =
                    "Why you pay ${formatMoney(paysMinor)}"
                val baselineText = String.format(Locale.US, "%.2f", baseline[index])
                val finalText = String.format(Locale.US, "%.2f", mult)
                view.findViewById<TextView>(R.id.txt_bill_formula).text =
                    "₹${crew.totalBill.toInt()} ÷ ${sorted.size} = ${formatMoney(baseShareMinor)} base share\n" +
                            "×$baselineText rank multiplier → ×$finalText after challenge adjustments = " +
                            "${formatMoney(paysMinor)} allocated (paise balanced across crew)"
                payableAmountMinor = paysMinor
                payableMultiplier = mult
                view.findViewById<Button>(R.id.btn_pay_stripe)?.apply {
                    text = "Pay ${formatMoney(paysMinor)}"
                    isEnabled = true
                }
            }

            container.addView(rowView)
        }
        showCrewContent(view)
    }

    private fun showCrewContent(view: View) {
        view.findViewById<View>(R.id.crew_detail_loading).visibility = View.GONE
        view.findViewById<View>(R.id.crew_detail_content).visibility = View.VISIBLE
    }

    private fun showCrewLoadError(view: View, title: String, message: String) {
        view.findViewById<View>(R.id.crew_detail_content).visibility = View.GONE
        view.findViewById<View>(R.id.crew_detail_loading).visibility = View.VISIBLE
        view.findViewById<ProgressBar>(R.id.crew_detail_loading_progress).visibility = View.GONE
        view.findViewById<TextView>(R.id.txt_crew_detail_loading).text = title
        view.findViewById<TextView>(R.id.txt_crew_detail_error).apply {
            text = message
            visibility = View.VISIBLE
        }
    }

    private fun formatMoney(minorUnits: Long): String {
        val rupees = minorUnits / 100
        val paise = minorUnits % 100
        return if (paise == 0L) "₹$rupees" else String.format(Locale.US, "₹%d.%02d", rupees, paise)
    }

    private fun populateWhyYouPaySection(view: View, entry: FriendEntry) {
        val socialMins = entry.socialMinutes
        val streamMins = entry.streamMinutes
        val neutralMins = entry.neutralMinutes
        val prodMins = entry.productiveMinutes

        fun hours(m: Double) = String.format("%.1fh", m / 60.0)
        view.findViewById<TextView>(R.id.txt_social_hours).text = hours(socialMins)
        view.findViewById<TextView>(R.id.txt_stream_hours).text = hours(streamMins)
        view.findViewById<TextView>(R.id.txt_neutral_hours).text = hours(neutralMins)
        view.findViewById<TextView>(R.id.txt_productive_hours).text = hours(prodMins)

        view.findViewById<TextView>(R.id.txt_raw_time_sum).text = hours(entry.rawMinutes)
        view.findViewById<TextView>(R.id.txt_weighted_sum).text =
            String.format(Locale.US, "%.1f", entry.weightedMinutes)

        val socialScore = socialMins * 2.0
        val streamScore = streamMins * 1.5
        val neutralScore = neutralMins
        val productiveScore = prodMins * 0.5
        val weightedScore = entry.weightedMinutes
        view.findViewById<TextView>(R.id.txt_score_formula).text =
            "${socialMins.toInt()}m × 2 + ${streamMins.toInt()}m × 1.5 + " +
                    "${neutralMins.toInt()}m × 1 + ${prodMins.toInt()}m × 0.5 = " +
                    "${weightedScore.toInt()} weighted minutes"

        setWeightedBar(view, R.id.bar_social, R.id.lbl_social, R.id.grp_social_value, socialScore, weightedScore)
        setWeightedBar(view, R.id.bar_stream, R.id.lbl_stream, R.id.grp_stream_value, streamScore, weightedScore)
        setWeightedBar(view, R.id.bar_neutral, R.id.lbl_neutral, R.id.grp_neutral_value, neutralScore, weightedScore)
        setWeightedBar(view, R.id.bar_productive, R.id.lbl_productive, R.id.grp_productive_value, productiveScore, weightedScore)
    }

    private fun setWeightedBar(
        root: View,
        barId: Int,
        labelId: Int,
        valueGroupId: Int,
        contribution: Double,
        totalScore: Double
    ) {
        val bar = root.findViewById<View>(barId)
        val row = bar.parent as View
        bar.post {
            val labelWidth = row.findViewById<View>(labelId).width
            val valueWidth = row.findViewById<View>(valueGroupId).width
            val availableWidth = (row.width - labelWidth - valueWidth).coerceAtLeast(0)
            val weight = if (totalScore > 0.0) contribution / totalScore else 0.0
            bar.layoutParams = bar.layoutParams.apply {
                width = (availableWidth * weight).toInt()
            }
        }
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