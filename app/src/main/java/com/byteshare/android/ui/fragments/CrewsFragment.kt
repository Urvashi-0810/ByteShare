package com.byteshare.android.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.byteshare.android.R
import com.byteshare.android.data.Crew
import com.byteshare.android.data.FirebaseCrewRepository
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.database.ValueEventListener

class CrewsFragment : Fragment() {

    private lateinit var crewListContainer: LinearLayout
    private var crewListener: ValueEventListener? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_crews, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        crewListContainer = view.findViewById(R.id.crew_list_container)

        val newCrewCard = view.findViewById<View>(R.id.new_crew_card)
        val joinCrewCard = view.findViewById<View>(R.id.join_crew_card)

        newCrewCard.setOnClickListener {
            showNewCrewDialog()
        }

        joinCrewCard.setOnClickListener {
            showJoinCrewDialog()
        }

        // Quick Pro Upgrade Action
        view.findViewById<View>(R.id.btn_quick_revenuecat_pro)?.setOnClickListener {
            PaywallDialogFragment.show(parentFragmentManager)
        }

        // Attach real-time listener — crew list updates automatically
        crewListener = FirebaseCrewRepository.listenForCrews { crews ->
            if (isAdded) {
                renderCrewList(crews)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Clean up the Firebase listener to avoid memory leaks
        crewListener?.let { FirebaseCrewRepository.removeCrewListener(it) }
        crewListener = null
    }

    private fun renderCrewList(crews: List<Crew>) {
        crewListContainer.removeAllViews()

        for (crew in crews) {
            val itemView = layoutInflater.inflate(R.layout.item_crew, crewListContainer, false)
            itemView.findViewById<TextView>(R.id.crew_emoji).text = crew.emoji
            itemView.findViewById<TextView>(R.id.crew_name).text = crew.name
            itemView.findViewById<TextView>(R.id.crew_subtitle).text =
                "${crew.memberCount} members • ₹${crew.totalBill.toInt()}"
            itemView.findViewById<TextView>(R.id.crew_owe).text = "₹${crew.oweAmount.toInt()}"

            itemView.setOnClickListener {
                openCrewDetail(crew.id)
            }

            crewListContainer.addView(itemView)
        }
    }

    private fun openCrewDetail(crewId: String) {
        val fragment = CrewDetailFragment.newInstance(crewId)
        parentFragmentManager.beginTransaction()
            .replace(R.id.nav_host_fragment, fragment)
            .addToBackStack(null)
            .commit()
    }

    private fun showNewCrewDialog() {
        val dialog = BottomSheetDialog(requireContext())
        val dialogView = layoutInflater.inflate(R.layout.dialog_new_crew, null)
        dialog.setContentView(dialogView)

        val editName = dialogView.findViewById<EditText>(R.id.edit_crew_name)
        val editBill = dialogView.findViewById<EditText>(R.id.edit_crew_bill)
        val btnSubmit = dialogView.findViewById<Button>(R.id.btn_create_crew_submit)
        val vibeContainer = dialogView.findViewById<LinearLayout>(R.id.vibe_container)

        var selectedEmoji = "🍺"

        // Vibe emoji selection listeners
        for (i in 0 until vibeContainer.childCount) {
            val child = vibeContainer.getChildAt(i) as? TextView ?: continue
            child.setOnClickListener {
                selectedEmoji = child.text.toString()
                // Reset backgrounds
                for (j in 0 until vibeContainer.childCount) {
                    val v = vibeContainer.getChildAt(j) as? TextView ?: continue
                    v.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.muted_cream)
                }
                child.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.accent_lime)
            }
        }

        btnSubmit.setOnClickListener {
            val name = editName.text.toString().ifBlank { "Friday Night" }
            val bill = editBill.text.toString().toDoubleOrNull() ?: 2000.0
            if (bill <= 0.0 || bill > MAX_BILL) {
                Toast.makeText(requireContext(), "Enter a bill between ₹1 and ₹${MAX_BILL.toInt()}", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Creator starts alone; others join via invite code
            val inviteCode = generateInviteCode()
            val newCrew = Crew(
                id = System.currentTimeMillis().toString(),
                name = name,
                emoji = selectedEmoji,
                memberCount = 1,
                totalBill = bill,
                oweAmount = bill,
                inviteCode = inviteCode
            )

            // Write to Firebase — the real-time listener will auto-refresh the list
            FirebaseCrewRepository.createCrew(newCrew) { success ->
                if (isAdded) {
                    if (success) {
                        Toast.makeText(requireContext(), "Crew '$name' created!", Toast.LENGTH_SHORT).show()
                        openCrewDetail(newCrew.id)
                    } else {
                        Toast.makeText(requireContext(), "Failed to create crew. Check connection.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showJoinCrewDialog() {
        val dialog = BottomSheetDialog(requireContext())
        val dialogView = layoutInflater.inflate(R.layout.dialog_join_crew, null)
        dialog.setContentView(dialogView)

        val editCode = dialogView.findViewById<EditText>(R.id.edit_invite_code)
        val btnSubmit = dialogView.findViewById<Button>(R.id.btn_join_crew_submit)

        btnSubmit.setOnClickListener {
            val code = editCode.text.toString().trim()
            if (code.isEmpty()) {
                Toast.makeText(requireContext(), "Please enter invite code", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Look up invite code in Firebase
            FirebaseCrewRepository.joinCrewByCode(code) { crew ->
                if (isAdded) {
                    if (crew != null) {
                        Toast.makeText(requireContext(), "Joined crew ${crew.name}!", Toast.LENGTH_SHORT).show()
                        openCrewDetail(crew.id)
                    } else {
                        Toast.makeText(requireContext(), "Invalid invite code '$code'", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun generateInviteCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..6).map { chars.random() }.joinToString("")
    }

    companion object {
        // Must match the totalBill limit in database.rules.json
        private const val MAX_BILL = 1_000_000.0
    }
}