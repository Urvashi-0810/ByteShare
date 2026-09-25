package com.example.byteshare.ui.fragments

import android.content.Intent
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
import com.example.byteshare.R
import com.example.byteshare.data.Crew
import com.example.byteshare.data.CrewRepository
import com.google.android.material.bottomsheet.BottomSheetDialog

class CrewsFragment : Fragment() {

    private lateinit var crewListContainer: LinearLayout

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

        renderCrewList()
    }

    private fun renderCrewList() {
        crewListContainer.removeAllViews()
        val crews = CrewRepository.getCrews()

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

        // Friends direct addition toggles
        val chipRahul = dialogView.findViewById<TextView>(R.id.chip_rahul)
        val chipPriya = dialogView.findViewById<TextView>(R.id.chip_priya)
        val chipArjun = dialogView.findViewById<TextView>(R.id.chip_arjun)

        var addRahul = true
        var addPriya = true
        var addArjun = false

        chipRahul?.setOnClickListener {
            addRahul = !addRahul
            chipRahul.text = if (addRahul) "🐼 Rahul ✓" else "🐼 Rahul +"
            chipRahul.backgroundTintList = ContextCompat.getColorStateList(
                requireContext(), if (addRahul) R.color.accent_lime else R.color.muted_cream
            )
        }

        chipPriya?.setOnClickListener {
            addPriya = !addPriya
            chipPriya.text = if (addPriya) "🦄 Priya ✓" else "🦄 Priya +"
            chipPriya.backgroundTintList = ContextCompat.getColorStateList(
                requireContext(), if (addPriya) R.color.accent_lime else R.color.muted_cream
            )
        }

        chipArjun?.setOnClickListener {
            addArjun = !addArjun
            chipArjun.text = if (addArjun) "🐯 Arjun ✓" else "🐯 Arjun +"
            chipArjun.backgroundTintList = ContextCompat.getColorStateList(
                requireContext(), if (addArjun) R.color.accent_lime else R.color.muted_cream
            )
        }

        btnSubmit.setOnClickListener {
            val name = editName.text.toString().ifBlank { "Friday Night" }
            val bill = editBill.text.toString().toDoubleOrNull() ?: 2000.0

            var count = 1
            if (addRahul) count++
            if (addPriya) count++
            if (addArjun) count++

            val inviteCode = generateInviteCode()
            val newCrew = Crew(
                id = System.currentTimeMillis().toString(),
                name = name,
                emoji = selectedEmoji,
                memberCount = count,
                totalBill = bill,
                oweAmount = bill / count,
                inviteCode = inviteCode
            )

            CrewRepository.addCrew(newCrew)
            renderCrewList()
            dialog.dismiss()

            Toast.makeText(requireContext(), "Crew '$name' created!", Toast.LENGTH_SHORT).show()

            // Open Detail Page for the created crew!
            openCrewDetail(newCrew.id)
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

            val joined = CrewRepository.joinCrewByCode(code)
            renderCrewList()
            dialog.dismiss()

            Toast.makeText(requireContext(), "Joined crew ${joined?.name ?: code}!", Toast.LENGTH_SHORT).show()

            // Open Detail Page for joined crew!
            joined?.let { openCrewDetail(it.id) }
        }

        dialog.show()
    }

    private fun generateInviteCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..6).map { chars.random() }.joinToString("")
    }
}