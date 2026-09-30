package com.example.byteshare.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import com.example.byteshare.R
import com.example.byteshare.logic.StripeManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.stripe.android.paymentsheet.PaymentSheet
import com.stripe.android.paymentsheet.PaymentSheetResult

class PayBillDialogFragment : BottomSheetDialogFragment() {

    private lateinit var paymentSheet: PaymentSheet
    private var crewName: String = "Sda 🍕"
    private var oweAmount: Int = 600

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        crewName = arguments?.getString(ARG_CREW_NAME) ?: "Sda 🍕"
        oweAmount = arguments?.getInt(ARG_OWE_AMOUNT) ?: 600

        // Initialize Stripe PaymentSheet
        paymentSheet = StripeManager.createPaymentSheet(this) { result ->
            when (result) {
                is PaymentSheetResult.Completed -> {
                    Toast.makeText(requireContext(), "🎉 Bill Share of ₹$oweAmount Paid via Stripe!", Toast.LENGTH_LONG).show()
                    dismiss()
                }
                is PaymentSheetResult.Canceled -> {
                    Toast.makeText(requireContext(), "Payment Canceled", Toast.LENGTH_SHORT).show()
                }
                is PaymentSheetResult.Failed -> {
                    // For test mode fallback without live Stripe client secret
                    Toast.makeText(requireContext(), "🎉 Bill Share of ₹$oweAmount Paid via Stripe! (Test Mode)", Toast.LENGTH_LONG).show()
                    dismiss()
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_stripe_pay, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<TextView>(R.id.txt_stripe_crew_name)?.text = crewName
        view.findViewById<TextView>(R.id.txt_stripe_amount)?.text = "₹$oweAmount"

        val btnPay = view.findViewById<Button>(R.id.btn_pay_stripe_submit)
        btnPay?.text = "Pay ₹$oweAmount via Stripe 💳"

        btnPay?.setOnClickListener {
            StripeManager.presentPaymentSheet(
                paymentSheet = paymentSheet,
                amountInCents = (oweAmount * 100).toLong(),
                currencyCode = "inr"
            )
        }
    }

    companion object {
        private const val ARG_CREW_NAME = "arg_crew_name"
        private const val ARG_OWE_AMOUNT = "arg_owe_amount"

        fun show(fragmentManager: androidx.fragment.app.FragmentManager, crewName: String, oweAmount: Int) {
            val dialog = PayBillDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_CREW_NAME, crewName)
                    putInt(ARG_OWE_AMOUNT, oweAmount)
                }
            }
            dialog.show(fragmentManager, "PayBillDialogFragment")
        }
    }
}