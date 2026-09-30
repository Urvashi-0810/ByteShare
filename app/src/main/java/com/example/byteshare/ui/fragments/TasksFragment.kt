package com.example.byteshare.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.byteshare.R
import com.example.byteshare.data.ChallengeDef
import com.example.byteshare.data.ChallengeProgress
import com.example.byteshare.data.ChallengeRepository
import com.example.byteshare.data.ChallengeState

class TasksFragment : Fragment() {

    private lateinit var individualContainer: LinearLayout
    private lateinit var groupContainer: LinearLayout

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_tasks, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        individualContainer = view.findViewById(R.id.individual_challenges_container)
        groupContainer = view.findViewById(R.id.group_challenges_container)
    }

    override fun onResume() {
        super.onResume()
        loadChallenges()
    }

    private fun loadChallenges() {
        ChallengeRepository.fetchChallenges { defs ->
            if (!isAdded) return@fetchChallenges
            // Evaluate active windows so states are fresh before rendering
            ChallengeRepository.evaluateActiveChallenges(requireContext(), defs) { progress ->
                if (isAdded) renderChallenges(defs, progress)
            }
        }
    }

    private fun renderChallenges(defs: List<ChallengeDef>, progress: Map<String, ChallengeProgress>) {
        individualContainer.removeAllViews()
        groupContainer.removeAllViews()

        for (challenge in defs) {
            val targetContainer = if (challenge.isGroupPod) groupContainer else individualContainer
            val itemView = layoutInflater.inflate(R.layout.item_challenge, targetContainer, false)

            itemView.findViewById<TextView>(R.id.txt_challenge_emoji).text = challenge.emoji
            itemView.findViewById<TextView>(R.id.txt_challenge_title).text = challenge.title
            itemView.findViewById<TextView>(R.id.txt_challenge_subtitle).text = challenge.subtitle

            // Badge reflects live progress state once the user has started the challenge
            val badge = itemView.findViewById<TextView>(R.id.badge_reward)
            badge.text = when (progress[challenge.id]?.state) {
                ChallengeState.ACTIVE -> "IN PROGRESS"
                ChallengeState.COMPLETED -> "COMPLETED"
                ChallengeState.FAILED -> "FAILED"
                else -> challenge.rewardBadge
            }

            itemView.findViewById<TextView>(R.id.txt_pod_count).visibility = View.GONE

            itemView.setOnClickListener {
                openChallengeDetail(challenge.id)
            }

            targetContainer.addView(itemView)
        }
    }

    private fun openChallengeDetail(challengeId: String) {
        val fragment = ChallengeDetailFragment.newInstance(challengeId)
        parentFragmentManager.beginTransaction()
            .replace(R.id.nav_host_fragment, fragment)
            .addToBackStack(null)
            .commit()
    }
}