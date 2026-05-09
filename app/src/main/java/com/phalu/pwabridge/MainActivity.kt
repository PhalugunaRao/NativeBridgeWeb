package com.phalu.pwabridge

import android.os.Bundle
import android.util.Log
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.phalu.webview.core.AdvancedWebView
import com.phalu.webview.core.AdvancedWebViewCallback
import com.phalu.webview.core.DownloadRequest
import com.phalu.webview.core.WebViewError
import com.phalu.webview.core.config.AdvancedWebViewConfig
import com.phalu.webview.core.config.SecurityConfig
import com.phalu.webview.core.config.WebPermissions
import com.phalu.webview.core.config.WebViewSettings
import com.phalu.webview.jsbridge.JsBridge
import com.phalu.webview.permissions.WebViewPermissionManager
import org.json.JSONObject

class MainActivity : AppCompatActivity() {
    private lateinit var advancedWebView: AdvancedWebView
    private lateinit var permissionManager: WebViewPermissionManager
    private lateinit var jsBridge: JsBridge

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        permissionManager = WebViewPermissionManager.from(this)
        advancedWebView = findViewById(R.id.advanced_webview)

        val config = AdvancedWebViewConfig(
            url = "https://example.com",
            headers = mapOf(
                "Authorization" to "Bearer demo_token",
                "Device" to "Android",
            ),
            permissions = WebPermissions(
                camera = true,
                microphone = true,
                location = true,
                photos = true,
                videos = true,
                notifications = true,
            ),
            settings = WebViewSettings(
                javaScriptEnabled = true,
                domStorageEnabled = true,
                supportMultipleWindows = true,
                pullToRefreshEnabled = true,
            ),
            security = SecurityConfig(
                allowedHosts = setOf("example.com"),
                openExternalSchemes = true,
            ),
        )

        jsBridge = JsBridge(trustedHosts = setOf("example.com")).apply {
            registerHandler("login") { payload: JSONObject ->
                Log.d("PwaBridge", "Login token: ${payload.optString("token")}")
                JSONObject().put("accepted", true)
            }
            registerHandler("getDevice") { _: JSONObject ->
                JSONObject()
                    .put("platform", "android")
                    .put("sdk", "AdvancedWebView")
            }
        }
        jsBridge.attachToWebView(advancedWebView.webView)

        advancedWebView.configure(
            config = config,
            lifecycleOwner = this,
            permissionHandler = permissionManager,
            fileChooserHandler = permissionManager,
            callback = object : AdvancedWebViewCallback {
                override fun onPageFinished(url: String) {
                    jsBridge.injectRuntime()
                }

                override fun onError(error: WebViewError) {
                    Log.w("PwaBridge", "Web error ${error.code}: ${error.description}")
                }

                override fun onDownloadRequested(download: DownloadRequest) {
                    Log.d("PwaBridge", "Downloading ${download.url}")
                }
            },
        )

        savedInstanceState?.let { advancedWebView.restoreState(it) }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        advancedWebView.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        jsBridge.destroy()
        permissionManager.cleanup()
        super.onDestroy()
    }
}
