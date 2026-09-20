package com.example.test.auth

import android.webkit.WebView
import com.google.gson.Gson
import org.json.JSONObject

/**
 * Reads the PocketBase JS SDK token from WebView localStorage (`pocketbase_auth`).
 */
object PocketBaseAuthBridge {

    private val gson = Gson()

    private const val JS_READ_AUTH =
        """
        (function(){
          try {
            var raw = localStorage.getItem('pocketbase_auth');
            return raw === null ? '' : raw;
          } catch (e) { return ''; }
        })()
        """

    fun readTokenAsync(webView: WebView, onResult: (String?) -> Unit) {
        webView.evaluateJavascript(JS_READ_AUTH) { value ->
            onResult(parseTokenFromJsCallback(value))
        }
    }

    internal fun parseTokenFromJsCallback(jsCallbackValue: String?): String? {
        if (jsCallbackValue.isNullOrBlank() || jsCallbackValue == "null") return null
        return try {
            val inner = gson.fromJson(jsCallbackValue, String::class.java) ?: return null
            if (inner.isBlank()) return null
            JSONObject(inner).optString("token", "").takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }
}
