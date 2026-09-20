package com.example.test

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import android.util.Log
import android.webkit.GeolocationPermissions
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.test.api.UpsertUsageBody
import com.example.test.api.UsageApi
import com.example.test.auth.OAuthJsBridge
import com.example.test.auth.PocketBaseAuthBridge
import com.example.test.ui.theme.TestTheme
import com.example.test.usage.UsageStatsCollector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.HttpException

/** ScreenSplit web theme: `index.css` --background / --foreground (cream + ink). */
private val ScreenSplitBackground = Color(0xFFFAF7EF)
private val ScreenSplitInk = Color(0xFF18181F)

class MainActivity : ComponentActivity() {

    private var webView: WebView? = null
    private var pendingGeoCallback: ((Boolean) -> Unit)? = null

    private val locationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
            val granted = grants.values.any { it }
            pendingGeoCallback?.invoke(granted)
            pendingGeoCallback = null
        }

    internal fun requestWebGeolocationPermission(onResult: (Boolean) -> Unit) {
        val alreadyGranted = hasLocationPermission(this)
        if (alreadyGranted) {
            onResult(true)
            return
        }
        pendingGeoCallback = onResult
        locationPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            ),
        )
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val usageApi = UsageApi.create(BuildConfig.POCKETBASE_URL)

        setContent {
            TestTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        TopAppBar(
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = ScreenSplitBackground,
                                titleContentColor = ScreenSplitInk,
                                actionIconContentColor = ScreenSplitInk,
                            ),
                            title = { Text("ScreenSplit") },
                            actions = {
                                IconButton(onClick = { publishUsage(usageApi) }) {
                                    Icon(
                                        Icons.Filled.CloudUpload,
                                        contentDescription = "Publish today’s screen time",
                                    )
                                }
                            },
                        )
                    },
                ) { innerPadding ->
                    WebScreen(
                        url = BuildConfig.WEB_APP_URL,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .imePadding(),
                        onWebViewCreated = { webView = it },
                    )
                }
            }
        }
    }

    private fun publishUsage(usageApi: UsageApi) {
        if (!isUsageStatsPermissionGranted(this)) {
            Toast.makeText(
                this,
                "Allow usage access in Settings to publish screen time",
                Toast.LENGTH_LONG,
            ).show()
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            return
        }

        val wv = webView
        if (wv == null) {
            Toast.makeText(this, "WebView not ready", Toast.LENGTH_SHORT).show()
            return
        }

        PocketBaseAuthBridge.readTokenAsync(wv) { token ->
            if (token.isNullOrBlank()) {
                Toast.makeText(
                    this,
                    "Sign in in the web app first, then tap upload again",
                    Toast.LENGTH_LONG,
                ).show()
                return@readTokenAsync
            }

            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val apps = UsageStatsCollector.collectTodayEntries(
                        this@MainActivity,
                        packageName,
                    )
                    if (apps.isEmpty()) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                this@MainActivity,
                                "No usage data for today yet",
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                        return@launch
                    }
                    val reportDate = UsageStatsCollector.utcTodayReportDate()
                    usageApi.upsertMyUsage(
                        token.trim(),
                        UpsertUsageBody(
                            reportDate = reportDate,
                            apps = apps,
                            source = "android",
                        ),
                    )
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@MainActivity,
                            "Published usage for $reportDate",
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                } catch (e: HttpException) {
                    withContext(Dispatchers.Main) {
                        val err =
                            e.response()?.errorBody()?.string()?.take(120)
                                ?: e.message()
                        Toast.makeText(
                            this@MainActivity,
                            "Sync failed ($err)",
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@MainActivity,
                            "Sync failed: ${e.message}",
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                }
            }
        }
    }
}

@Composable
private fun WebScreen(
    url: String,
    modifier: Modifier = Modifier,
    onWebViewCreated: (WebView?) -> Unit,
) {
    val activity = LocalContext.current as? ComponentActivity
    if (activity == null) {
        LaunchedEffect(Unit) {
            onWebViewCreated(null)
        }
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Can’t load the web app (missing activity context).")
        }
        return
    }

    AndroidView(
        factory = {
            try {
                WebView(activity).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.setGeolocationEnabled(true)
                    addJavascriptInterface(OAuthJsBridge(activity), "ScreenSplitNative")
                    webViewClient = WebViewClient()
                    webChromeClient = object : WebChromeClient() {
                        override fun onGeolocationPermissionsShowPrompt(
                            origin: String?,
                            callback: GeolocationPermissions.Callback?,
                        ) {
                            val host = activity as? MainActivity
                            if (host == null || callback == null || origin == null) {
                                callback?.invoke(origin, false, false)
                                return
                            }
                            host.requestWebGeolocationPermission { granted ->
                                callback.invoke(origin, granted, true)
                            }
                        }
                    }
                    loadUrl(url)
                    onWebViewCreated(this)
                }
            } catch (e: Throwable) {
                val cause = e.cause?.message ?: e.message ?: e.javaClass.simpleName
                Log.e("WebScreen", "WebView failed to start", e)
                onWebViewCreated(null)
                TextView(activity).apply {
                    text =
                        """
                        WebView could not start (system browser component missing or broken).

                        Install or update “Android System WebView” or Google Chrome from the Play Store, then reopen the app.

                        Details: $cause
                        """.trimIndent()
                    setPadding(48, 48, 48, 48)
                    textSize = 14f
                }
            }
        },
        modifier = modifier,
    )
}

private fun isUsageStatsPermissionGranted(context: Context): Boolean {
    val appOps = ContextCompat.getSystemService(context, AppOpsManager::class.java)
    val mode = appOps?.checkOpNoThrow(
        AppOpsManager.OPSTR_GET_USAGE_STATS,
        Process.myUid(),
        context.packageName,
    )
    return mode == AppOpsManager.MODE_ALLOWED
}

private fun hasLocationPermission(context: Context): Boolean {
    val fine = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED
    val coarse = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED
    return fine || coarse
}
