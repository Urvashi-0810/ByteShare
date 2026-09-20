package com.example.test.auth

import android.net.Uri
import android.util.Log
import android.webkit.JavascriptInterface
import androidx.activity.ComponentActivity
import androidx.browser.customtabs.CustomTabsIntent
import com.example.test.BuildConfig

/**
 * Opens PocketBase/Google OAuth in Chrome Custom Tabs so Google accepts the flow.
 * Embedded WebViews block [window.open] popups and Google disallows OAuth inside WebView.
 */
class OAuthJsBridge(private val activity: ComponentActivity) {

    @JavascriptInterface
    fun openOAuthUrl(url: String) {
        activity.runOnUiThread {
            try {
                val uri = Uri.parse(url.trim())
                if (!isAllowedOAuthStartUrl(uri)) {
                    Log.w(TAG, "Blocked unexpected OAuth URL host: ${uri.host}")
                    return@runOnUiThread
                }
                CustomTabsIntent.Builder().build().launchUrl(activity, uri)
            } catch (e: Exception) {
                Log.e(TAG, "openOAuthUrl failed", e)
            }
        }
    }

    private fun isAllowedOAuthStartUrl(uri: Uri): Boolean {
        val scheme = uri.scheme?.lowercase() ?: return false
        if (scheme != "https" && scheme != "http") return false
        val host = uri.host?.lowercase() ?: return false
        if (host == "accounts.google.com") return true
        val pbHost = Uri.parse(BuildConfig.POCKETBASE_URL).host?.lowercase()
        return pbHost != null && host == pbHost
    }

    companion object {
        private const val TAG = "OAuthJsBridge"
    }
}
