package com.example.byteshare.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import com.example.byteshare.R
import com.example.byteshare.logic.RevenueCatManager
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
        val txtPrice = view.findViewById<TextView>(R.id.txt_plan_price)

        // Fetch live RevenueCat Offerings
        RevenueCatManager.getOfferings(
            onSuccess = { offerings ->
                val currentOffering = offerings.current
                val pkg = currentOffering?.monthly ?: currentOffering?.availablePackages?.firstOrNull()
                if (pkg != null) {
                    monthlyPackage = pkg
                    val formattedPrice = pkg.product.price.formatted
                    txtPrice?.text = "$formattedPrice / mo"
                    btnSubscribe?.text = "Unlock ByteShare Pro ($formattedPrice/mo)"
                }
            },
            onError = { errorMsg ->
                // Keep default $2.99/mo text if offline/dev mode
            }
        )

        // Handle Subscription Click
        btnSubscribe.setOnClickListener {
            val pkg = monthlyPackage
            if (pkg != null) {
                RevenueCatManager.purchasePackage(
                    activity = requireActivity(),
                    rcPackage = pkg,
                    onSuccess = {
                        Toast.makeText(requireContext(), "🎉 Welcome to ByteShare Pro! Ads removed.", Toast.LENGTH_LONG).show()
                        dismiss()
                    },
                    onError = { err ->
                        Toast.makeText(requireContext(), "Purchase error: $err", Toast.LENGTH_SHORT).show()
                    }
                )
            } else {
                // Dev test mode fallback when live billing package is offline
                Toast.makeText(requireContext(), "🎉 Welcome to ByteShare Pro! (Test Mode)", Toast.LENGTH_LONG).show()
                dismiss()
            }
        }

        // Handle Restore Click
        btnRestore.setOnClickListener {
            RevenueCatManager.restorePurchases(
                onSuccess = { restored ->
                    if (restored) {
                        Toast.makeText(requireContext(), "Purchases restored! ByteShare Pro active.", Toast.LENGTH_LONG).show()
                        dismiss()
                    } else {
                        Toast.makeText(requireContext(), "No active Pro purchases found.", Toast.LENGTH_SHORT).show()
                    }
                },
                onError = { err ->
                    Toast.makeText(requireContext(), "Restore failed: $err", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }

    companion object {
        fun show(fragmentManager: androidx.fragment.app.FragmentManager) {
            PaywallDialogFragment().show(fragmentManager, "PaywallDialogFragment")
        }
    }
}