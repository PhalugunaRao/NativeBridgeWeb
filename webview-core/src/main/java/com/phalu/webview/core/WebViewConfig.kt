package com.phalu.webview.core

import android.content.Context
import android.webkit.WebView
import androidx.annotation.StringRes
import androidx.lifecycle.LifecycleOwner

/**
 * Configuration for the WebView library.
 *
 * This class allows the host app to customize the WebView behavior
 * without directly manipulating WebView settings.
 *
 * @property url The initial URL to load.
 * @property headers Optional HTTP headers to include with the initial request.
 * @property enableJavaScript Whether JavaScript should be enabled (default false).
 * @property enableDomStorage Whether DOM storage should be enabled (default true for PWA).
 * @property enableZoom Whether zoom controls should be enabled (default true).
 * @property trustedDomains List of domains allowed for JavaScript communication (if empty, all domains are allowed).
 * @property userAgent Optional custom user agent string (null uses default).
 */
data class WebViewConfig(
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val enableJavaScript: Boolean = false,
    val enableDomStorage: Boolean = true,
    val enableZoom: Boolean = true,
    val trustedDomains: List<String> = emptyList(),
    val userAgent: String? = null,
) {
    /**
     * Validates the configuration and throws [IllegalArgumentException] if invalid.
     */
    fun validate() {
        require(url.isNotBlank()) { "URL must not be blank" }
        if (trustedDomains.isNotEmpty()) {
            require(trustedDomains.all { it.isNotBlank() }) {
                "Trusted domains must not contain blank strings"
            }
        }
    }
}

/**
 * Builder for [WebViewConfig] to allow step‑by‑step configuration.
 */
class WebViewConfigBuilder {
    private var url: String = ""
    private var headers: Map<String, String> = emptyMap()
    private var enableJavaScript: Boolean = false
    private var enableDomStorage: Boolean = true
    private var enableZoom: Boolean = true
    private var trustedDomains: List<String> = emptyList()
    private var userAgent: String? = null

    fun url(url: String) = apply { this.url = url }
    fun headers(headers: Map<String, String>) = apply { this.headers = headers }
    fun enableJavaScript(enable: Boolean) = apply { this.enableJavaScript = enable }
    fun enableDomStorage(enable: Boolean) = apply { this.enableDomStorage = enable }
    fun enableZoom(enable: Boolean) = apply { this.enableZoom = enable }
    fun trustedDomains(domains: List<String>) = apply { this.trustedDomains = domains }
    fun userAgent(userAgent: String?) = apply { this.userAgent = userAgent }

    fun build(): WebViewConfig {
        return WebViewConfig(
            url = url,
            headers = headers,
            enableJavaScript = enableJavaScript,
            enableDomStorage = enableDomStorage,
            enableZoom = enableZoom,
            trustedDomains = trustedDomains,
            userAgent = userAgent
        ).also { it.validate() }
    }
}

/**
 * Manager that creates and configures a [SecureWebView] according to a [WebViewConfig].
 *
 * This class handles the entire setup and lifecycle binding, providing a clean API
 * for the host app.
 */
class WebViewManager(
    private val context: Context,
    private val config: WebViewConfig
) {
    private var webView: SecureWebView? = null

    /**
     * Creates a new [SecureWebView] instance, applies the configuration,
     * and loads the URL.
     */
    fun createWebView(): SecureWebView {
        val webView = SecureWebView(context).apply {
            // Apply config
            enableJavaScript(config.enableJavaScript)
            settings.domStorageEnabled = config.enableDomStorage
            settings.setSupportZoom(config.enableZoom)
            settings.builtInZoomControls = config.enableZoom
            config.userAgent?.let { settings.userAgentString = it }

            // Load URL with headers
            loadUrlWithHeaders(config.url, config.headers)
        }
        this.webView = webView
        return webView
    }

    /**
     * Attaches the WebView to a lifecycle owner and returns the same WebView.
     */
    fun attachToLifecycle(webView: SecureWebView, lifecycleOwner: LifecycleOwner): SecureWebView {
        webView.attachToLifecycle(lifecycleOwner)
        return webView
    }

    /**
     * Returns the current WebView (if created).
     */
    fun getWebView(): SecureWebView? = webView

    /**
     * Cleans up the WebView and releases resources.
     */
    fun destroy() {
        webView?.destroy()
        webView = null
    }
}