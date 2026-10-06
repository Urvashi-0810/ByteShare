package com.byteshare.android.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.byteshare.android.R
import com.byteshare.android.data.ChallengeDef
import com.byteshare.android.data.ChallengeProgress
import com.byteshare.android.data.ChallengeRepository
import com.byteshare.android.data.ChallengeState

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

            // Personal progress + group progress in parallel
            var personalProgress: Map<String, ChallengeProgress>? = null
            var groupProgress: Map<String, ChallengeProgress>? = null
            var participantCounts: Map<String, Int>? = null

            val tryRender = {
                val pp = personalProgress
                val gp = groupProgress
                val pc = participantCounts
                if (pp != null && gp != null && pc != null && isAdded) {
                    // Merge: group progress wins for group challenges
                    val merged = pp.toMutableMap()
                    gp.forEach { (id, progress) -> merged[id] = progress }
                    renderChallenges(defs, merged, pc)
                }
            }

            // Evaluate personal challenges (also evaluates active windows)
            ChallengeRepository.evaluateActiveChallenges(requireContext(), defs) { progress ->
                personalProgress = progress
                tryRender()
            }

            // Fetch group challenge statuses across all crews
            ChallengeRepository.fetchMyGroupProgressAcrossCrews { progress, counts ->
                groupProgress = progress
                participantCounts = counts
                tryRender()
            }
        }
    }

    private fun renderChallenges(
        defs: List<ChallengeDef>,
        progress: Map<String, ChallengeProgress>,
        participantCounts: Map<String, Int>
    ) {
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

            // Show participant count for group challenges
            val podCount = itemView.findViewById<TextView>(R.id.txt_pod_count)
            if (challenge.isGroupPod) {
                val count = participantCounts[challenge.id] ?: 0
                if (count > 0) {
                    podCount.text = "👥 $count active"
                    podCount.visibility = View.VISIBLE
                } else {
                    podCount.text = "👥 GROUP"
                    podCount.visibility = View.VISIBLE
                }
            } else {
                podCount.visibility = View.GONE
            }

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