package com.byteshare.android.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import com.byteshare.android.R
import com.byteshare.android.logic.RevenueCatManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.revenuecat.purchases.Package

class PaywallDialogFragment : BottomSheetDialogFragment() {

    private var monthlyPackage: Package? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_paywall, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val btnSubscribe = view.findViewById<Button>(R.id.btn_subscribe_pro)
        val btnRestore = view.findViewById<TextView>(R.id.btn_restore_purchases)

        btnSubscribe.setOnClickListener {
            val pkg = monthlyPackage
            if (pkg == null) {
                // Offerings failed to load earlier; the button acts as a retry
                loadOfferings(view)
                return@setOnClickListener
            }
            btnSubscribe.isEnabled = false
            RevenueCatManager.purchasePackage(
                activity = requireActivity(),
                rcPackage = pkg,
                onSuccess = { proActive ->
                    if (!isAdded) return@purchasePackage
                    btnSubscribe.isEnabled = true
                    if (proActive) {
                        Toast.makeText(requireContext(), "🎉 Welcome to ByteShare Pro! Ads removed.", Toast.LENGTH_LONG).show()
                        dismiss()
                    } else {
                        Toast.makeText(
                            requireContext(),
                            "Purchase received, but Pro isn't active yet. Try Restore Purchases in a moment.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                },
                onError = { err ->
                    if (!isAdded) return@purchasePackage
                    btnSubscribe.isEnabled = true
                    Toast.makeText(requireContext(), "Purchase error: $err", Toast.LENGTH_SHORT).show()
                }
            )
        }

        btnRestore.setOnClickListener {
            RevenueCatManager.restorePurchases(
                onSuccess = { restored ->
                    if (!isAdded) return@restorePurchases
                    if (restored) {
                        Toast.makeText(requireContext(), "Purchases restored! ByteShare Pro active.", Toast.LENGTH_LONG).show()
                        dismiss()
                    } else {
                        Toast.makeText(requireContext(), "No active Pro purchases found.", Toast.LENGTH_SHORT).show()
                    }
                },
                onError = { err ->
                    if (!isAdded) return@restorePurchases
                    Toast.makeText(requireContext(), "Restore failed: $err", Toast.LENGTH_SHORT).show()
                }
            )
        }

        loadOfferings(view)
    }

    private fun loadOfferings(view: View) {
        val btnSubscribe = view.findViewById<Button>(R.id.btn_subscribe_pro)
        val txtPrice = view.findViewById<TextView>(R.id.txt_plan_price)
        val txtTrial = view.findViewById<TextView>(R.id.txt_plan_trial)
        btnSubscribe.isEnabled = false
        btnSubscribe.text = "Loading plans…"

        RevenueCatManager.getOfferings(
            onSuccess = { offerings ->
                if (!isAdded) return@getOfferings
                val currentOffering = offerings.current
                val pkg = currentOffering?.monthly ?: currentOffering?.availablePackages?.firstOrNull()
                if (pkg == null) {
                    showOfferingsError(btnSubscribe, "No Pro plan is available right now.")
                    return@getOfferings
                }
                monthlyPackage = pkg
                val formattedPrice = pkg.product.price.formatted
                txtPrice.text = "$formattedPrice / mo"
                // Only advertise a trial when the Play product actually has one
                val trial = pkg.product.defaultOption?.freePhase?.billingPeriod
                txtTrial.text = if (trial != null) {
                    val unit = trial.unit.name.lowercase().replaceFirstChar { it.uppercase() }
                    "${trial.value}-$unit Free Trial • Cancel Anytime"
                } else {
                    "Cancel Anytime"
                }
                btnSubscribe.text = "Unlock ByteShare Pro ($formattedPrice/mo)"
                btnSubscribe.isEnabled = true
            },
            onError = { errorMsg ->
                if (isAdded) showOfferingsError(btnSubscribe, errorMsg)
            }
        )
    }

    private fun showOfferingsError(btnSubscribe: Button, message: String) {
        monthlyPackage = null
        btnSubscribe.text = "Retry loading plans"
        btnSubscribe.isEnabled = true
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    companion object {
        fun show(fragmentManager: androidx.fragment.app.FragmentManager) {
            PaywallDialogFragment().show(fragmentManager, "PaywallDialogFragment")
        }
    }
}