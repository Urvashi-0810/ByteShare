package com.example.test

import android.app.Application
import android.os.Build
import android.util.Log
import android.webkit.WebView
import androidx.webkit.WebViewCompat

class ScreenSplitApp : Application() {

    override fun onCreate() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val processName = Application.getProcessName()
            if (processName != packageName) {
                WebView.setDataDirectorySuffix(processName)
            }
        }
        super.onCreate()

        if (BuildConfig.DEBUG) {
            WebView.setWebContentsDebuggingEnabled(true)
        }

        val pkg = WebViewCompat.getCurrentWebViewPackage(this)
        if (pkg == null) {
            Log.e(TAG, "No WebView provider package (install Android System WebView or Chrome)")
        } else {
            Log.i(TAG, "WebView provider: ${pkg.packageName} ${pkg.versionName}")
        }
    }

    companion object {
        private const val TAG = "ScreenSplitApp"
    }
}
