package com.example.byteshare.logic

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

object AdManager {

    private const val TAG = "ByteShareAdManager"

    // Official Google AdMob Test Ad Unit IDs
    const val TEST_BANNER_ID = "ca-app-pub-3940256099942544/6300978111"
    const val TEST_REWARDED_ID = "ca-app-pub-3940256099942544/5224354917"
    const val TEST_NATIVE_ID = "ca-app-pub-3940256099942544/2247696110"

    private var rewardedAd: RewardedAd? = null
    private var isInitialized = false
    private var hasEarnedRewardThisSession = false

    fun initialize(context: Context) {
        if (isInitialized) return
        MobileAds.initialize(context) { initializationStatus ->
            Log.d(TAG, "AdMob Initialized: ${initializationStatus.adapterStatusMap}")
            isInitialized = true
        }
        // Pre-load a Rewarded Ad
        loadRewardedAd(context)
    }

    /**
     * Load Rewarded Video Ad
     */
    fun loadRewardedAd(context: Context) {
        val adRequest = AdRequest.Builder().build()
        RewardedAd.load(
            context,
            TEST_REWARDED_ID,
            adRequest,
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    rewardedAd = ad
                    Log.d(TAG, "Rewarded Ad Loaded Successfully")
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    rewardedAd = null
                    Log.e(TAG, "Rewarded Ad Failed to Load: ${loadAdError.message}")
                }
            }
        )
    }

    /**
     * Show Rewarded Video Ad
     */
    fun showRewardedAd(activity: Activity, onRewardEarned: (rewardAmount: Int) -> Unit) {
        if (hasEarnedRewardThisSession) {
            android.widget.Toast.makeText(
                activity,
                "You have already collected an ad reward this session!",
                android.widget.Toast.LENGTH_SHORT
            ).show()
            return
        }

        if (rewardedAd != null) {
            rewardedAd?.show(activity) { rewardItem ->
                Log.d(TAG, "User earned reward: ${rewardItem.amount} ${rewardItem.type}")
                hasEarnedRewardThisSession = true
                onRewardEarned(rewardItem.amount)
                loadRewardedAd(activity) // Preload next ad
            }
        } else {
            Log.d(TAG, "Rewarded Ad wasn't ready yet, attempting to reload")
            loadRewardedAd(activity)
            hasEarnedRewardThisSession = true
            onRewardEarned(10) // Fallback simulated reward for dev test
        }
    }

    /**
     * Load Native Ad
     */
    fun loadNativeAd(context: Context, onNativeAdLoaded: (NativeAd) -> Unit) {
        val adLoader = AdLoader.Builder(context, TEST_NATIVE_ID)
            .forNativeAd { nativeAd ->
                Log.d(TAG, "Native Ad Loaded Successfully")
                onNativeAdLoaded(nativeAd)
            }
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(adError: LoadAdError) {
                    Log.e(TAG, "Native Ad Failed to Load: ${adError.message}")
                }
            })
            .withNativeAdOptions(NativeAdOptions.Builder().build())
            .build()

        adLoader.loadAd(AdRequest.Builder().build())
    }
}