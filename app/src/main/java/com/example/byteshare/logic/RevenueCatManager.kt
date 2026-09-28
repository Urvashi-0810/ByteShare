package com.example.byteshare.logic

import android.app.Activity
import android.content.Context
import android.util.Log
import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.Offerings
import com.revenuecat.purchases.Package
import com.revenuecat.purchases.PurchaseParams
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesConfiguration
import com.revenuecat.purchases.PurchasesError
import com.revenuecat.purchases.getOfferingsWith
import com.revenuecat.purchases.interfaces.PurchaseCallback
import com.revenuecat.purchases.interfaces.ReceiveCustomerInfoCallback
import com.revenuecat.purchases.models.StoreTransaction

object RevenueCatManager {

    private const val TAG = "ByteShareRevenueCat"

    // RevenueCat Public API Key (Replace with your real Key from RevenueCat Dashboard)
    const val REVENUECAT_API_KEY = "goog_sample_byteshare_api_key"

    // Entitlement Identifier configured in RevenueCat Dashboard
    const val ENTITLEMENT_PRO = "pro_access"

    var isProUser = false
        private set

    /**
     * Configure RevenueCat SDK at App Startup
     */
    fun configure(context: Context) {
        try {
            Purchases.configure(
                PurchasesConfiguration.Builder(context, REVENUECAT_API_KEY).build()
            )
            Log.d(TAG, "RevenueCat Configured Successfully")

            // Check initial customer info
            checkProStatus { proActive ->
                isProUser = proActive
                Log.d(TAG, "Initial Pro Status: $isProUser")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to configure RevenueCat: ${e.message}")
        }
    }

    /**
     * Check if user has active Pro access (No Ads)
     */
    fun checkProStatus(onResult: (Boolean) -> Unit) {
        if (!Purchases.isConfigured) {
            onResult(isProUser)
            return
        }

        Purchases.sharedInstance.getCustomerInfo(object : ReceiveCustomerInfoCallback {
            override fun onReceived(customerInfo: CustomerInfo) {
                val entitlement = customerInfo.entitlements[ENTITLEMENT_PRO]
                val proActive = entitlement?.isActive == true
                isProUser = proActive
                onResult(proActive)
            }

            override fun onError(error: PurchasesError) {
                Log.e(TAG, "Error fetching customer info: ${error.message}")
                onResult(isProUser)
            }
        })
    }

    /**
     * Fetch Offerings for Monthly Subscription
     */
    fun getOfferings(onSuccess: (Offerings) -> Unit, onError: (String) -> Unit) {
        if (!Purchases.isConfigured) {
            onError("RevenueCat SDK is not configured")
            return
        }

        Purchases.sharedInstance.getOfferingsWith(
            onError = { error ->
                Log.e(TAG, "Failed to fetch offerings: ${error.message}")
                onError(error.message)
            },
            onSuccess = { offerings ->
                Log.d(TAG, "Fetched offerings: ${offerings.current?.identifier}")
                onSuccess(offerings)
            }
        )
    }

    /**
     * Purchase Package
     */
    fun purchasePackage(
        activity: Activity,
        rcPackage: Package,
        onSuccess: (CustomerInfo?) -> Unit,
        onError: (String) -> Unit
    ) {
        if (!Purchases.isConfigured) {
            // Simulated purchase success for development testing
            isProUser = true
            onSuccess(null)
            return
        }

        val params = PurchaseParams.Builder(activity, rcPackage).build()
        Purchases.sharedInstance.purchase(params, object : PurchaseCallback {
            override fun onCompleted(storeTransaction: StoreTransaction, customerInfo: CustomerInfo) {
                val entitlement = customerInfo.entitlements[ENTITLEMENT_PRO]
                if (entitlement?.isActive == true) {
                    isProUser = true
                }
                onSuccess(customerInfo)
            }

            override fun onError(error: PurchasesError, userCancelled: Boolean) {
                val msg = if (userCancelled) "Purchase cancelled" else error.message
                onError(msg)
            }
        })
    }

    /**
     * Restore Purchases
     */
    fun restorePurchases(onSuccess: (Boolean) -> Unit, onError: (String) -> Unit) {
        if (!Purchases.isConfigured) {
            isProUser = true
            onSuccess(true)
            return
        }

        Purchases.sharedInstance.restorePurchases(object : ReceiveCustomerInfoCallback {
            override fun onReceived(customerInfo: CustomerInfo) {
                val proActive = customerInfo.entitlements[ENTITLEMENT_PRO]?.isActive == true
                isProUser = proActive
                onSuccess(proActive)
            }

            override fun onError(error: PurchasesError) {
                onError(error.message)
            }
        })
    }
}