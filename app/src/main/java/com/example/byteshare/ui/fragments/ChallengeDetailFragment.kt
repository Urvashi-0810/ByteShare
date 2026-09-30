package com.example.byteshare.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.byteshare.R
import com.example.byteshare.data.ChallengeDef
import com.example.byteshare.data.ChallengeProgress
import com.example.byteshare.data.ChallengeRepository
import com.example.byteshare.data.ChallengeState
import com.example.byteshare.logic.AdManager

class ChallengeDetailFragment : Fragment() {

    private var challengeId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        challengeId = arguments?.getString(ARG_CHALLENGE_ID)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_challenge_detail, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<ImageView>(R.id.btn_back_challenge).setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        val id = challengeId ?: return
        ChallengeRepository.fetchChallenge(id) { def ->
            if (isAdded && def != null) {
                bindChallenge(view, def)
                refreshProgress(view, def)
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

    private fun refreshProgress(view: View, def: ChallengeDef) {
        ChallengeRepository.evaluateActiveChallenges(requireContext(), listOf(def)) { progressMap ->
            if (isAdded) bindProgress(view, def, progressMap[def.id])
        }
    }

    private fun bindProgress(view: View, def: ChallengeDef, progress: ChallengeProgress?) {
        val btn = view.findViewById<Button>(R.id.btn_accept_challenge)
        val state = progress?.state ?: ChallengeState.NOT_STARTED

        // Participants panel shows own tracking status (group aggregation is a later phase)
        // Accept button
        val btnAccept = view.findViewById<Button>(R.id.btn_accept_challenge)
        var isAccepted = false

        btnAccept.setOnClickListener {
            isAccepted = !isAccepted
            if (isAccepted) {
                btnAccept.text = "Challenge Accepted! ✓"
                btnAccept.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.accent_lime)
                btnAccept.setTextColor(ContextCompat.getColor(requireContext(), R.color.primary_ink))
                Toast.makeText(requireContext(), "Accepted '${challenge.title}'!", Toast.LENGTH_SHORT).show()
            } else {
                btnAccept.text = "Accept Challenge"
                btnAccept.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.primary_ink)
                btnAccept.setTextColor(ContextCompat.getColor(requireContext(), R.color.accent_lime))
            }
        }

        // Rewarded Video Ad button
        view.findViewById<Button>(R.id.btn_watch_rewarded_ad)?.setOnClickListener {
            AdManager.showRewardedAd(requireActivity()) { rewardAmount ->
                Toast.makeText(requireContext(), "🎉 Earned +20 Social XP! Reward level: $rewardAmount", Toast.LENGTH_LONG).show()
            }
        }
        val container = view.findViewById<LinearLayout>(R.id.participants_container)
        container.removeAllViews()
        if (progress != null && state != ChallengeState.NOT_STARTED) {
            val rowView = layoutInflater.inflate(R.layout.item_participant, container, false)
            rowView.findViewById<TextView>(R.id.txt_participant_avatar).text = "Y"
            rowView.findViewById<TextView>(R.id.txt_participant_name).text = "You"
            rowView.findViewById<TextView>(R.id.txt_participant_detail).text =
                "${progress.minutesUsed.toInt()}m used of ${def.limitMinutes.toInt()}m limit"
            val badge = rowView.findViewById<TextView>(R.id.badge_participant_status)
            badge.backgroundTintList =
                ContextCompat.getColorStateList(requireContext(), R.color.muted_cream)
            when (state) {
                ChallengeState.COMPLETED -> {
                    badge.text = "PASS"
                    badge.setTextColor(ContextCompat.getColor(requireContext(), R.color.cat_productive))
                }
                ChallengeState.FAILED -> {
                    badge.text = "FAIL"
                    badge.setTextColor(ContextCompat.getColor(requireContext(), R.color.cat_stream))
                }
                else -> {
                    badge.text = "LIVE"
                    badge.setTextColor(ContextCompat.getColor(requireContext(), R.color.gray_ink))
                }
            }
            container.addView(rowView)
        }

        when (state) {
            ChallengeState.NOT_STARTED -> {
                styleButton(btn, "Accept Challenge", R.color.primary_ink, R.color.accent_lime)
                btn.isEnabled = true
                btn.setOnClickListener {
                    ChallengeRepository.startChallenge(def.id) { ok ->
                        if (!isAdded) return@startChallenge
                        if (ok) {
                            Toast.makeText(requireContext(), "Challenge started — good luck!", Toast.LENGTH_SHORT).show()
                            refreshProgress(view, def)
                        } else {
                            Toast.makeText(requireContext(), "Couldn't start. Check connection.", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            ChallengeState.ACTIVE -> {
                styleButton(btn, "In Progress — Give Up?", R.color.accent_lime, R.color.primary_ink)
                btn.isEnabled = true
                btn.setOnClickListener {
                    ChallengeRepository.abandonChallenge(def.id) { ok ->
                        if (isAdded && ok) refreshProgress(view, def)
                    }
                }
            }
            ChallengeState.COMPLETED -> {
                styleButton(btn, "Completed \u2713", R.color.accent_lime, R.color.primary_ink)
                btn.isEnabled = false
            }
            ChallengeState.FAILED -> {
                styleButton(btn, "Failed — Try Again", R.color.primary_ink, R.color.accent_lime)
                btn.isEnabled = true
                btn.setOnClickListener {
                    ChallengeRepository.startChallenge(def.id) { ok ->
                        if (isAdded && ok) refreshProgress(view, def)
                    }
                }
            }
        }
    }

    private fun styleButton(btn: Button, label: String, bg: Int, fg: Int) {
        btn.text = label
        btn.backgroundTintList = ContextCompat.getColorStateList(requireContext(), bg)
        btn.setTextColor(ContextCompat.getColor(requireContext(), fg))
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