package com.example.byteshare.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import com.example.byteshare.R
import com.example.byteshare.data.StripePaymentRepository
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.stripe.android.PaymentConfiguration
import com.stripe.android.paymentsheet.PaymentSheet
import com.stripe.android.paymentsheet.PaymentSheetResult

class PayBillDialogFragment : BottomSheetDialogFragment() {

    private var crewName: String = "Sda 🍕"
    private var crewId: String = ""
    private var oweAmountMinor: Long = 60000L
    private var multiplier: Double = 1.0

    private lateinit var paymentSheet: PaymentSheet

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        crewName = arguments?.getString(ARG_CREW_NAME) ?: "Sda 🍕"
        crewId = arguments?.getString(ARG_CREW_ID).orEmpty()
        oweAmountMinor = arguments?.getLong(ARG_OWE_AMOUNT) ?: 60000L
        multiplier = arguments?.getDouble(ARG_MULTIPLIER) ?: 1.0

        paymentSheet = PaymentSheet(this) { result ->
            handlePaymentResult(result)
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
        val oweAmount = formatMoney(oweAmountMinor)
        view.findViewById<TextView>(R.id.txt_stripe_amount)?.text = oweAmount
        view.findViewById<TextView>(R.id.txt_stripe_multiplier)?.text =
            "Calculated from your seven-day rank (×%.2f)".format(java.util.Locale.US, multiplier)

        val btnPay = view.findViewById<Button>(R.id.btn_pay_stripe_submit)
        btnPay?.text = "Pay $oweAmount"

        btnPay?.setOnClickListener {
            if (crewId.isBlank()) {
                Toast.makeText(requireContext(), "Crew information is missing.", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            btnPay.isEnabled = false
            btnPay.text = "Preparing secure checkout…"

            StripePaymentRepository.createCrewPaymentIntent(crewId) { payment, error ->
                if (!isAdded) return@createCrewPaymentIntent
                if (payment == null) {
                    btnPay.isEnabled = true
                    btnPay.text = "Pay ${formatMoney(oweAmountMinor)}"
                    Toast.makeText(
                        requireContext(),
                        error ?: "Couldn't prepare secure checkout.",
                        Toast.LENGTH_LONG
                    ).show()
                    return@createCrewPaymentIntent
                }

                oweAmountMinor = payment.amountMinor
                multiplier = payment.multiplier
                view.findViewById<TextView>(R.id.txt_stripe_amount).text = formatMoney(oweAmountMinor)
                view.findViewById<TextView>(R.id.txt_stripe_multiplier).text =
                    "Rank ${payment.rank} · seven-day multiplier ×%.2f".format(
                        java.util.Locale.US, multiplier
                    )

                PaymentConfiguration.init(requireContext(), payment.publishableKey)

                try {
                    btnPay.text = "Opening secure checkout…"
                    paymentSheet.presentWithPaymentIntent(
                        payment.clientSecret,
                        PaymentSheet.Configuration("ByteShare")
                    )
                } catch (e: Exception) {
                    btnPay.isEnabled = true
                    btnPay.text = "Pay ${formatMoney(oweAmountMinor)}"
                    Toast.makeText(
                        requireContext(),
                        "Couldn't open Stripe checkout: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun handlePaymentResult(result: PaymentSheetResult) {
        when (result) {
            is PaymentSheetResult.Completed -> {
                Toast.makeText(
                    requireContext(),
                    "Payment of ${formatMoney(oweAmountMinor)} completed.",
                    Toast.LENGTH_LONG
                ).show()
                dismiss()
            }
            is PaymentSheetResult.Canceled -> {
                view?.findViewById<Button>(R.id.btn_pay_stripe_submit)?.apply {
                    isEnabled = true
                    text = "Pay ${formatMoney(oweAmountMinor)}"
                }
                Toast.makeText(requireContext(), "Payment canceled.", Toast.LENGTH_SHORT).show()
            }
            is PaymentSheetResult.Failed -> {
                view?.findViewById<Button>(R.id.btn_pay_stripe_submit)?.apply {
                    isEnabled = true
                    text = "Pay ${formatMoney(oweAmountMinor)}"
                }
                Toast.makeText(
                    requireContext(),
                    "Payment failed: ${result.error.localizedMessage ?: "please try again"}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    companion object {
        private const val ARG_CREW_NAME = "arg_crew_name"
        private const val ARG_CREW_ID = "arg_crew_id"
        private const val ARG_OWE_AMOUNT = "arg_owe_amount"
        private const val ARG_MULTIPLIER = "arg_multiplier"

        fun show(
            fragmentManager: androidx.fragment.app.FragmentManager,
            crewName: String,
            crewId: String,
            oweAmountMinor: Long,
            multiplier: Double
        ) {
            val dialog = PayBillDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_CREW_NAME, crewName)
                    putString(ARG_CREW_ID, crewId)
                    putLong(ARG_OWE_AMOUNT, oweAmountMinor)
                    putDouble(ARG_MULTIPLIER, multiplier)
                }
            }
            dialog.show(fragmentManager, "PayBillDialogFragment")
        }
    }

    private fun formatMoney(minorUnits: Long): String {
        val rupees = minorUnits / 100
        val paise = minorUnits % 100
        return if (paise == 0L) "₹$rupees" else String.format(java.util.Locale.US, "₹%d.%02d", rupees, paise)
    }
}