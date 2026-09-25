package com.example.byteshare.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.byteshare.R
import com.example.byteshare.data.Challenge
import com.example.byteshare.data.ChallengeRepository

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

        renderChallenges()
    }

    private fun renderChallenges() {
        individualContainer.removeAllViews()
        groupContainer.removeAllViews()

        val allChallenges = ChallengeRepository.getChallenges()

        for (challenge in allChallenges) {
            val targetContainer = if (challenge.isGroupPod) groupContainer else individualContainer
            val itemView = layoutInflater.inflate(R.layout.item_challenge, targetContainer, false)

            itemView.findViewById<TextView>(R.id.txt_challenge_emoji).text = challenge.emoji
            itemView.findViewById<TextView>(R.id.txt_challenge_title).text = challenge.title
            itemView.findViewById<TextView>(R.id.txt_challenge_subtitle).text = challenge.subtitle
            itemView.findViewById<TextView>(R.id.badge_reward).text = challenge.rewardBadge

            val podCountText = itemView.findViewById<TextView>(R.id.txt_pod_count)
            if (challenge.isGroupPod) {
                podCountText.visibility = View.VISIBLE
                podCountText.text = "👥 ${challenge.podCount} pod"
            } else {
                podCountText.visibility = View.GONE
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