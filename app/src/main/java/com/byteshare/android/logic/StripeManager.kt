package com.byteshare.android.logic

import android.util.Log
import androidx.fragment.app.Fragment
import com.stripe.android.PaymentConfiguration
import com.stripe.android.paymentsheet.PaymentSheet
import com.stripe.android.paymentsheet.PaymentSheetResult

object StripeManager {

    private const val TAG = "ByteShareStripeManager"

    fun initPaymentSheet(
        fragment: Fragment,
        onResult: (PaymentSheetResult) -> Unit
    ): PaymentSheet {
        return PaymentSheet(fragment) { result ->
            when (result) {
                is PaymentSheetResult.Completed -> Log.d(TAG, "Stripe Payment Completed Successfully")
                is PaymentSheetResult.Canceled -> Log.d(TAG, "Stripe Payment Canceled by User")
                is PaymentSheetResult.Failed -> Log.e(TAG, "Stripe Payment Failed: ${result.error.message}")
            }
            onResult(result)
        }
    }

    fun presentPaymentSheet(
        fragment: Fragment,
        paymentSheet: PaymentSheet,
        publishableKey: String,
        clientSecret: String
    ): Boolean {
        if (!publishableKey.startsWith("pk_test_") && !publishableKey.startsWith("pk_live_")) {
            Log.e(TAG, "Backend returned an invalid Stripe publishable key")
            return false
        }
        if (clientSecret.isBlank()) {
            Log.e(TAG, "PaymentIntent client secret is missing")
            return false
        }
        
        return try {
            PaymentConfiguration.init(fragment.requireContext(), publishableKey)
            val configuration = PaymentSheet.Configuration(
                merchantDisplayName = "ByteShare Split Bill Settlement",
                allowsDelayedPaymentMethods = false
            )
            paymentSheet.presentWithPaymentIntent(clientSecret, configuration)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Exception presenting Stripe PaymentSheet", e)
            false
        }
    }
}