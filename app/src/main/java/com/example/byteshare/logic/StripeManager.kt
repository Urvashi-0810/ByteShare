package com.example.byteshare.logic

import android.content.Context
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.fragment.app.Fragment
import com.stripe.android.PaymentConfiguration
import com.stripe.android.paymentsheet.PaymentSheet
import com.stripe.android.paymentsheet.PaymentSheetResult

object StripeManager {

    private const val TAG = "ByteShareStripeManager"

    // Official Stripe Test Publishable Key (Replace with your real Key from Stripe Dashboard)
    const val STRIPE_PUBLISHABLE_KEY = "pk_test_51PxSampleStripePublishableKeyForByteShareDevTesting"

    private var isInitialized = false

    fun initialize(context: Context) {
        if (isInitialized) return
        try {
            PaymentConfiguration.init(context, STRIPE_PUBLISHABLE_KEY)
            isInitialized = true
            Log.d(TAG, "Stripe PaymentConfiguration Initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Stripe: ${e.message}")
        }
    }

    /**
     * Create and return a PaymentSheet instance bound to a Fragment
     */
    fun createPaymentSheet(
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

    /**
     * Create and return a PaymentSheet instance bound to an Activity
     */
    fun createPaymentSheet(
        activity: ComponentActivity,
        onResult: (PaymentSheetResult) -> Unit
    ): PaymentSheet {
        return PaymentSheet(activity) { result ->
            when (result) {
                is PaymentSheetResult.Completed -> Log.d(TAG, "Stripe Payment Completed Successfully")
                is PaymentSheetResult.Canceled -> Log.d(TAG, "Stripe Payment Canceled by User")
                is PaymentSheetResult.Failed -> Log.e(TAG, "Stripe Payment Failed: ${result.error.message}")
            }
            onResult(result)
        }
    }

    /**
     * Launch Stripe PaymentSheet for settling a crew bill share
     */
    fun presentPaymentSheet(
        paymentSheet: PaymentSheet,
        amountInCents: Long = 60000, // e.g., $600.00 / ₹600 for demo
        currencyCode: String = "inr",
        clientSecret: String? = null
    ) {
        val configuration = PaymentSheet.Configuration(
            merchantDisplayName = "ByteShare Split Bill Settlement",
            allowsDelayedPaymentMethods = false
        )

        val secret = clientSecret ?: "pi_test_sample_client_secret_secret_12345"
        try {
            paymentSheet.presentWithPaymentIntent(secret, configuration)
        } catch (e: Exception) {
            Log.e(TAG, "Exception presenting Stripe PaymentSheet", e)
        }
    }
}