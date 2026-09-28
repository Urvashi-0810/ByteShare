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
import com.example.byteshare.data.ChallengeRepository
import com.example.byteshare.data.ParticipantStatus
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

        val challenge = challengeId?.let { ChallengeRepository.getChallengeById(it) }
            ?: ChallengeRepository.getChallenges().first()

        // Back button
        view.findViewById<ImageView>(R.id.btn_back_challenge).setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        // Hero info
        view.findViewById<TextView>(R.id.txt_detail_hero_emoji).text = challenge.emoji
        view.findViewById<TextView>(R.id.txt_detail_title).text = challenge.title
        view.findViewById<TextView>(R.id.tag_type).text = challenge.tag
        view.findViewById<TextView>(R.id.tag_frequency).text = challenge.startFrequency

        // Instructions
        view.findViewById<TextView>(R.id.txt_instructions_quote).text = challenge.instructions
        view.findViewById<TextView>(R.id.txt_requirement).text = challenge.requirement
        view.findViewById<TextView>(R.id.badge_detail_reward).text = challenge.reward
        view.findViewById<TextView>(R.id.txt_how_it_works).text = challenge.howItWorks

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

        // Populate Participants List
        val container = view.findViewById<LinearLayout>(R.id.participants_container)
        container.removeAllViews()

        for (p in challenge.participants) {
            val rowView = layoutInflater.inflate(R.layout.item_participant, container, false)
            rowView.findViewById<TextView>(R.id.txt_participant_avatar).text = p.avatar
            rowView.findViewById<TextView>(R.id.txt_participant_name).text = p.name
            rowView.findViewById<TextView>(R.id.txt_participant_detail).text = p.detail

            val badge = rowView.findViewById<TextView>(R.id.badge_participant_status)
            when (p.status) {
                ParticipantStatus.PASS -> {
                    badge.text = "PASS"
                    badge.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.muted_cream)
                    badge.setTextColor(ContextCompat.getColor(requireContext(), R.color.cat_productive))
                }
                ParticipantStatus.FAIL -> {
                    badge.text = "FAIL"
                    badge.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.muted_cream)
                    badge.setTextColor(ContextCompat.getColor(requireContext(), R.color.cat_stream))
                }
                ParticipantStatus.LIVE -> {
                    badge.text = "LIVE"
                    badge.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.muted_cream)
                    badge.setTextColor(ContextCompat.getColor(requireContext(), R.color.gray_ink))
                }
            }

            container.addView(rowView)
        }
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