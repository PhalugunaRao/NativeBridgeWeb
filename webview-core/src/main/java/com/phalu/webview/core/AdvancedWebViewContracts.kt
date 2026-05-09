package com.phalu.webview.core

import android.net.Uri
import android.os.Bundle
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import com.phalu.webview.core.config.AdvancedWebViewConfig
import com.phalu.webview.core.config.AdvancedWebViewConfigBuilder
import kotlinx.coroutines.flow.StateFlow

interface AdvancedWebViewCallback {
    fun onPageStarted(url: String) = Unit
    fun onPageFinished(url: String) = Unit
    fun onProgressChanged(progress: Int) = Unit
    fun onTitleChanged(title: String?) = Unit
    fun onUrlBlocked(url: String, reason: String) = Unit
    fun onExternalUrl(url: String) = Unit
    fun onError(error: WebViewError) = Unit
    fun onDownloadRequested(download: DownloadRequest) = Unit
    fun onFullscreenChanged(isFullscreen: Boolean) = Unit
}

interface RequestInterceptor {
    fun intercept(request: WebResourceRequest): WebResourceResponse? = null
    fun headersFor(url: String): Map<String, String> = emptyMap()
}

interface PermissionRequestHandler {
    fun handleWebPermissionRequest(request: PermissionRequest)
    fun handleGeolocationPermissionRequest(
        origin: String,
        callback: GeolocationPermissions.Callback,
    )
}

interface FileChooserHandler {
    fun openFileChooser(
        params: android.webkit.WebChromeClient.FileChooserParams,
        callback: ValueCallback<Array<Uri>>,
    ): Boolean
}

data class DownloadRequest(
    val url: String,
    val userAgent: String?,
    val contentDisposition: String?,
    val mimeType: String?,
    val contentLength: Long,
)

data class WebViewError(
    val url: String?,
    val code: Int,
    val description: String,
    val isMainFrame: Boolean = true,
)

data class BrowserState(
    val url: String? = null,
    val title: String? = null,
    val progress: Int = 0,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val isLoading: Boolean = false,
    val isOffline: Boolean = false,
    val lastError: WebViewError? = null,
)

interface AdvancedWebViewController {
    val state: StateFlow<BrowserState>
    val webView: WebView?
    fun loadUrl(url: String, headers: Map<String, String> = emptyMap())
    fun reload()
    fun stopLoading()
    fun goBack(): Boolean
    fun goForward(): Boolean
    fun evaluateJavascript(script: String, callback: ValueCallback<String>? = null)
    fun saveState(outState: Bundle)
    fun restoreState(state: Bundle)
    fun clearSession(includeCookies: Boolean = false)
    fun destroy()
}

object ComposeSupport {
    const val artifactHint: String =
        "Use AndroidView(factory = { AdvancedWebView(it) }) from androidx.compose.ui.viewinterop."
}

fun advancedWebViewConfig(block: AdvancedWebViewConfigBuilder.() -> Unit): AdvancedWebViewConfig {
    return AdvancedWebViewConfigBuilder().apply(block).build()
}
