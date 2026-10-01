package com.example.byteshare.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import com.example.byteshare.R
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class FriendProfileDialogFragment : BottomSheetDialogFragment() {

    private var friendName: String = "Friend"
    private var friendAvatar: String = "🐼"
    private var rawMinutes: Int = 38
    private var isSelf: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        friendName = arguments?.getString(ARG_NAME) ?: "Friend"
        friendAvatar = arguments?.getString(ARG_AVATAR) ?: "🐼"
        rawMinutes = arguments?.getInt(ARG_RAW_MINS) ?: 38
        isSelf = arguments?.getBoolean(ARG_IS_SELF) ?: false
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_friend_profile, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<TextView>(R.id.txt_profile_name).text = friendName
        view.findViewById<TextView>(R.id.txt_profile_avatar).text = friendAvatar
        view.findViewById<TextView>(R.id.txt_profile_email).text =
            "${friendName.lowercase().replace(" ", "")}@demo.app"

        view.findViewById<TextView>(R.id.txt_profile_avg_daily).text = "${rawMinutes}m"
        view.findViewById<TextView>(R.id.txt_profile_weekly_total).text = "${rawMinutes * 7}m"

        val btnClose = view.findViewById<ImageView>(R.id.btn_close_profile)
        btnClose?.setOnClickListener { dismiss() }

        val btnKeepGrinding = view.findViewById<Button>(R.id.btn_keep_grinding)
        btnKeepGrinding?.setOnClickListener {
            Toast.makeText(requireContext(), "Sent motivation to $friendName! 🚀", Toast.LENGTH_SHORT).show()
            dismiss()
        }

        val btnRemove = view.findViewById<Button>(R.id.btn_remove_friend)
        if (isSelf) {
            btnRemove?.visibility = View.GONE
        } else {
            btnRemove?.setOnClickListener {
                Toast.makeText(requireContext(), "Friend $friendName removed", Toast.LENGTH_SHORT).show()
                dismiss()
            }
        }
    }

    companion object {
        private const val ARG_NAME = "arg_name"
        private const val ARG_AVATAR = "arg_avatar"
        private const val ARG_RAW_MINS = "arg_raw_mins"
        private const val ARG_IS_SELF = "arg_is_self"

        fun show(
            fragmentManager: androidx.fragment.app.FragmentManager,
            name: String,
            avatar: String,
            rawMinutes: Int,
            isSelf: Boolean = false
        ) {
            val dialog = FriendProfileDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_NAME, name)
                    putString(ARG_AVATAR, avatar)
                    putInt(ARG_RAW_MINS, rawMinutes)
                    putBoolean(ARG_IS_SELF, isSelf)
                }
            }
            dialog.show(fragmentManager, "FriendProfileDialogFragment")
        }
    }
}