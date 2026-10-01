package com.example.byteshare.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import com.example.byteshare.R
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class FormulaDialogFragment : BottomSheetDialogFragment() {

    private var scoreFormulaText: String = ""
    private var billFormulaText: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        scoreFormulaText = arguments?.getString(ARG_SCORE_FORMULA).orEmpty()
        billFormulaText = arguments?.getString(ARG_BILL_FORMULA).orEmpty()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_formula_explanation, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (scoreFormulaText.isNotBlank()) {
            view.findViewById<TextView>(R.id.txt_dialog_score_formula)?.text = scoreFormulaText
        }
        if (billFormulaText.isNotBlank()) {
            view.findViewById<TextView>(R.id.txt_dialog_bill_formula)?.text = billFormulaText
        }

        view.findViewById<ImageView>(R.id.btn_close_formula)?.setOnClickListener {
            dismiss()
        }

        view.findViewById<Button>(R.id.btn_got_it_formula)?.setOnClickListener {
            dismiss()
        }
    }

    companion object {
        private const val ARG_SCORE_FORMULA = "arg_score_formula"
        private const val ARG_BILL_FORMULA = "arg_bill_formula"

        fun show(
            fragmentManager: androidx.fragment.app.FragmentManager,
            scoreFormula: String,
            billFormula: String
        ) {
            val dialog = FormulaDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_SCORE_FORMULA, scoreFormula)
                    putString(ARG_BILL_FORMULA, billFormula)
                }
            }
            dialog.show(fragmentManager, "FormulaDialogFragment")
        }
    }
}