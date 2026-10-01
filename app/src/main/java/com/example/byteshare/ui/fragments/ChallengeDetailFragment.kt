package com.example.byteshare.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.byteshare.R
import com.example.byteshare.data.ChallengeDef
import com.example.byteshare.data.ChallengeProgress
import com.example.byteshare.data.ChallengeRepository
import com.example.byteshare.data.ChallengeParticipant
import com.example.byteshare.data.ChallengeState
import com.example.byteshare.data.Crew
import com.example.byteshare.data.FirebaseCrewRepository
import com.example.byteshare.logic.AdManager

class ChallengeDetailFragment : Fragment() {

    private var challengeId: String? = null
    private var selectedGroupCrew: Crew? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        challengeId = arguments?.getString(ARG_CHALLENGE_ID)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_challenge_detail, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<ImageView>(R.id.btn_back_challenge).setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        view.findViewById<Button>(R.id.btn_watch_rewarded_ad)?.setOnClickListener {
            AdManager.showRewardedAd(requireActivity()) { rewardAmount ->
                com.example.byteshare.data.XpRepository.addXp(requireContext(), 20, "Watched Rewarded Ad") { newXp ->
                    Toast.makeText(
                        requireContext(),
                        "🎉 +20 Social XP Added! Total XP: $newXp",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }

        val id = challengeId ?: return
        ChallengeRepository.fetchChallenge(id) { def ->
            if (isAdded && def != null) {
                bindChallenge(view, def)
                if (def.isGroupPod) setupGroupCrewPicker(view, def)
                else refreshProgress(view, def)
            }
        }
    }

    private fun bindChallenge(view: View, challenge: ChallengeDef) {
        view.findViewById<TextView>(R.id.txt_detail_hero_emoji).text = challenge.emoji
        view.findViewById<TextView>(R.id.txt_detail_title).text = challenge.title
        view.findViewById<TextView>(R.id.tag_type).text = challenge.tag
        view.findViewById<TextView>(R.id.tag_frequency).text = challenge.startFrequency
        view.findViewById<TextView>(R.id.txt_instructions_quote).text = challenge.instructions
        view.findViewById<TextView>(R.id.txt_requirement).text = challenge.requirement
        view.findViewById<TextView>(R.id.badge_detail_reward).text = challenge.reward
        view.findViewById<TextView>(R.id.txt_how_it_works).text = challenge.howItWorks
    }

    private fun setupGroupCrewPicker(view: View, def: ChallengeDef) {
        val pickerContainer = view.findViewById<View>(R.id.group_crew_picker_container)
        val spinner = view.findViewById<Spinner>(R.id.spinner_group_crew)
        val empty = view.findViewById<TextView>(R.id.txt_group_crew_empty)
        pickerContainer.visibility = View.VISIBLE
        spinner.isEnabled = false
        showLoading(view)

        FirebaseCrewRepository.fetchMyCrewsOnce { crews ->
            if (!isAdded || this.view !== view) return@fetchMyCrewsOnce
            if (crews.isEmpty()) {
                spinner.visibility = View.GONE
                empty.visibility = View.VISIBLE
                view.findViewById<Button>(R.id.btn_accept_challenge).isEnabled = false
                hideLoading(view)
                bindProgress(view, def, null, null, emptyList())
                return@fetchMyCrewsOnce
            }

            // Pre-select before attaching adapter so onItemSelected identity check
            // prevents duplicate refreshProgress on initial layout.
            selectedGroupCrew = crews.first()

            spinner.visibility = View.VISIBLE
            empty.visibility = View.GONE
            val adapter = ArrayAdapter(
                requireContext(),
                R.layout.item_spinner_crew,
                android.R.id.text1,
                crews.map { "${it.emoji}  ${it.name}" }
            ).also { it.setDropDownViewResource(R.layout.item_spinner_crew_dropdown) }
            spinner.adapter = adapter
            spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: AdapterView<*>?) {
                    selectedGroupCrew = null
                }

                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    selectedView: View?,
                    position: Int,
                    itemId: Long
                ) {
                    val crew = crews.getOrNull(position) ?: return
                    if (selectedGroupCrew?.id == crew.id) return
                    selectedGroupCrew = crew
                    refreshProgress(view, def, crew)
                }
            }
            spinner.isEnabled = true
            refreshProgress(view, def, crews.first())
        }
    }

    private fun refreshProgress(view: View, def: ChallengeDef, crew: Crew? = null) {
        showLoading(view)
        if (def.isGroupPod) {
            val selectedCrew = crew ?: selectedGroupCrew ?: return
            ChallengeRepository.evaluateGroupChallenge(
                requireContext(), selectedCrew.id, def
            ) { participants ->
                if (!isAdded || this.view !== view) return@evaluateGroupChallenge
                hideLoading(view)
                val myUid = com.example.byteshare.data.AuthRepository.currentUserId
                val myProgress = participants.firstOrNull { it.uid == myUid }?.progress
                bindProgress(view, def, myProgress, selectedCrew.id, participants)
            }
        } else {
            ChallengeRepository.evaluateActiveChallenges(requireContext(), listOf(def)) { progressMap ->
                if (isAdded && this.view === view) {
                    hideLoading(view)
                    bindProgress(view, def, progressMap[def.id], null, emptyList())
                }
            }
        }
    }

    private fun bindProgress(
        view: View,
        def: ChallengeDef,
        progress: ChallengeProgress?,
        groupCrewId: String?,
        participants: List<ChallengeParticipant>
    ) {
        val btn = view.findViewById<Button>(R.id.btn_accept_challenge)
        val state = progress?.state ?: ChallengeState.NOT_STARTED

        val container = view.findViewById<LinearLayout>(R.id.participants_container)
        container.removeAllViews()
        view.findViewById<TextView>(R.id.txt_participants_title).text =
            if (def.isGroupPod) "CREW PARTICIPANTS" else "YOUR CHALLENGE"
        if (def.isGroupPod) {
            if (participants.isEmpty()) {
                container.addView(TextView(requireContext()).apply {
                    text = "No crew members have joined this challenge yet."
                    setTextColor(requireContext().getColor(R.color.gray_ink))
                    textSize = 14f
                    setPadding(8, 12, 8, 12)
                })
            } else {
                participants.forEach { participant ->
                    addParticipantRow(
                        container,
                        def,
                        participant.name,
                        participant.progress,
                        participant.uid == com.example.byteshare.data.AuthRepository.currentUserId
                    )
                }
            }
        } else if (progress != null) {
            addParticipantRow(container, def, "You", progress, true)
        }

        when (state) {
            ChallengeState.NOT_STARTED -> {
                styleButton(
                    btn,
                    if (def.isGroupPod) "Join Group Challenge" else "Accept Challenge",
                    R.color.primary_ink,
                    R.color.accent_lime
                )
                btn.isEnabled = !def.isGroupPod || groupCrewId != null
                btn.setOnClickListener {
                    val start = { callback: (Boolean) -> Unit ->
                        if (def.isGroupPod) {
                            groupCrewId?.let {
                                ChallengeRepository.startGroupChallenge(it, def.id, callback)
                            } ?: callback(false)
                        } else {
                            ChallengeRepository.startChallenge(def.id, callback)
                        }
                    }
                    start { ok ->
                        if (!isAdded) return@start
                        if (ok) {
                            Toast.makeText(requireContext(), "Challenge started — good luck!", Toast.LENGTH_SHORT).show()
                            refreshProgress(view, def, selectedGroupCrew)
                        } else {
                            Toast.makeText(requireContext(), "Couldn't join. Check crew membership and connection.", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            ChallengeState.ACTIVE -> {
                styleButton(btn, "In Progress — Give Up?", R.color.accent_lime, R.color.primary_ink)
                btn.isEnabled = true
                btn.setOnClickListener {
                    val abandon: ((Boolean) -> Unit) -> Unit = { callback ->
                        if (def.isGroupPod && groupCrewId != null) {
                            ChallengeRepository.abandonGroupChallenge(groupCrewId, def.id, callback)
                        } else {
                            ChallengeRepository.abandonChallenge(def.id, callback)
                        }
                    }
                    abandon { ok ->
                        if (isAdded && ok) refreshProgress(view, def, selectedGroupCrew)
                    }
                }
            }
            ChallengeState.COMPLETED -> {
                styleButton(btn, "Completed ✓", R.color.accent_lime, R.color.primary_ink)
                btn.isEnabled = false
            }
            ChallengeState.FAILED -> {
                styleButton(btn, "Failed — Try Again", R.color.primary_ink, R.color.accent_lime)
                btn.isEnabled = true
                btn.setOnClickListener {
                    val retry: ((Boolean) -> Unit) -> Unit = { callback ->
                        if (def.isGroupPod && groupCrewId != null) {
                            ChallengeRepository.startGroupChallenge(groupCrewId, def.id, callback)
                        } else {
                            ChallengeRepository.startChallenge(def.id, callback)
                        }
                    }
                    retry { ok ->
                        if (isAdded && ok) refreshProgress(view, def, selectedGroupCrew)
                    }
                }
            }
        }
    }

    private fun addParticipantRow(
        container: LinearLayout,
        def: ChallengeDef,
        name: String,
        progress: ChallengeProgress,
        isYou: Boolean
    ) {
        val row = layoutInflater.inflate(R.layout.item_participant, container, false)
        row.findViewById<TextView>(R.id.txt_participant_avatar).text =
            name.firstOrNull()?.uppercase() ?: "?"
        row.findViewById<TextView>(R.id.txt_participant_name).text = if (isYou) "You" else name
        row.findViewById<TextView>(R.id.txt_participant_detail).text =
            "${progress.minutesUsed.toInt()}m of ${def.limitMinutes.toInt()}m allowed"
        val badge = row.findViewById<TextView>(R.id.badge_participant_status)
        badge.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.muted_cream)
        when (progress.state) {
            ChallengeState.COMPLETED -> {
                badge.text = "DONE"
                badge.setTextColor(ContextCompat.getColor(requireContext(), R.color.cat_productive))
            }
            ChallengeState.FAILED -> {
                badge.text = "FAILED"
                badge.setTextColor(ContextCompat.getColor(requireContext(), R.color.cat_stream))
            }
            ChallengeState.ACTIVE -> {
                badge.text = "LIVE"
                badge.setTextColor(ContextCompat.getColor(requireContext(), R.color.gray_ink))
            }
            ChallengeState.NOT_STARTED -> {
                badge.text = "READY"
                badge.setTextColor(ContextCompat.getColor(requireContext(), R.color.gray_ink))
            }
        }
        container.addView(row)
    }

    private fun styleButton(btn: Button, label: String, bg: Int, fg: Int) {
        btn.text = label
        btn.backgroundTintList = ContextCompat.getColorStateList(requireContext(), bg)
        btn.setTextColor(ContextCompat.getColor(requireContext(), fg))
    }

    private fun showLoading(view: View) {
        view.findViewById<View>(R.id.progress_loading)?.visibility = View.VISIBLE
    }

    private fun hideLoading(view: View) {
        view.findViewById<View>(R.id.progress_loading)?.visibility = View.GONE
    }

    companion object {
        private const val ARG_CHALLENGE_ID = "arg_challenge_id"

        fun newInstance(challengeId: String): ChallengeDetailFragment {
            return ChallengeDetailFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_CHALLENGE_ID, challengeId)
                }
            }
        }
    }
}