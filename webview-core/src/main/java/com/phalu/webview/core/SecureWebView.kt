package com.phalu.webview.core

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import android.util.AttributeSet
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner

/**
 * A secure WebView with safe defaults and lifecycle integration.
 *
 * This WebView automatically applies security best practices:
 * - JavaScript disabled by default (enable via [WebViewConfig.enableJavaScript])
 * - File access restrictions
 * - Secure mixed content handling
 * - PWA compatibility settings
 *
 * The WebView observes the host [Lifecycle] to automatically pause/resume timers
 * and destroy itself when the lifecycle is destroyed.
 */
open class SecureWebView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : WebView(context, attrs, defStyleAttr) {

    private var lifecycleObserver: LifecycleEventObserver? = null

    init {
        configureSecureDefaults()
    }

    /**
     * Applies secure default settings to the WebView.
     * Override this method to customize settings.
     */
    protected open fun configureSecureDefaults() {
        with(settings) {
            // Security: disable file access
            allowFileAccess = false
            // Deprecated in API 30, only set for older versions
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                allowFileAccessFromFileURLs = false
                allowUniversalAccessFromFileURLs = false
            }

            // Mixed content
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            }

            // PWA compatibility
            domStorageEnabled = true
            // databaseEnabled is deprecated in API 30, only set for older versions
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                databaseEnabled = true
            }
            cacheMode = WebSettings.LOAD_DEFAULT
            javaScriptEnabled = false // disabled by default, enable via config

            // Performance
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = true
            displayZoomControls = false
            setSupportZoom(true)

            // Network
            loadsImagesAutomatically = true
            blockNetworkImage = false
            blockNetworkLoads = false
        }

        // Additional security for older APIs
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            settings.mediaPlaybackRequiresUserGesture = true
        }

        // Prevent remote debugging in release builds (optional)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            val isDebuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
            if (!isDebuggable) {
                setWebContentsDebuggingEnabled(false)
            }
        }
    }

    /**
     * Attaches this WebView to a [LifecycleOwner] (e.g., Activity/Fragment).
     * The WebView will automatically pause timers when paused and resume when resumed.
     * It will also destroy itself when the lifecycle is destroyed.
     */
    fun attachToLifecycle(lifecycleOwner: LifecycleOwner) {
        lifecycleObserver?.let { lifecycleOwner.lifecycle.removeObserver(it) }

        lifecycleObserver = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> onPause()
                Lifecycle.Event.ON_RESUME -> onResume()
                Lifecycle.Event.ON_DESTROY -> {
                    destroy()
                    lifecycleOwner.lifecycle.removeObserver(lifecycleObserver!!)
                    lifecycleObserver = null
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver!!)
    }

    /**
     * Loads a URL with optional headers.
     * Note: Headers are only applied to the initial request (Android limitation).
     * For subsequent requests, consider using cookies or intercepting requests.
     */
    fun loadUrlWithHeaders(url: String, headers: Map<String, String>?) {
        if (headers.isNullOrEmpty()) {
            loadUrl(url)
        } else {
            loadUrl(url, headers)
        }
    }

    /**
     * Enables JavaScript execution (use with caution).
     */
    fun enableJavaScript(enabled: Boolean) {
        settings.javaScriptEnabled = enabled
    }

    /**
     * Cleans up resources.
     */
    override fun destroy() {
        lifecycleObserver = null
        super.destroy()
    }
}