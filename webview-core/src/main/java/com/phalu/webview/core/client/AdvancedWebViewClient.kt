package com.phalu.webview.core.client

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Build
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.phalu.webview.core.AdvancedWebViewCallback
import com.phalu.webview.core.RequestInterceptor
import com.phalu.webview.core.WebViewError
import com.phalu.webview.core.security.UrlDecision
import com.phalu.webview.core.security.UrlPolicy

class AdvancedWebViewClient(
    private val context: Context,
    private val urlPolicy: UrlPolicy,
    private val callback: AdvancedWebViewCallback?,
    private val requestInterceptor: RequestInterceptor?,
    private val updateNavigationState: () -> Unit,
) : WebViewClient() {

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val url = request.url.toString()
        return when (val decision = urlPolicy.check(url)) {
            UrlDecision.Allow -> false
            UrlDecision.External -> {
                openExternal(url)
                callback?.onExternalUrl(url)
                true
            }
            is UrlDecision.Block -> {
                callback?.onUrlBlocked(url, decision.reason)
                true
            }
        }
    }

    @Suppress("DEPRECATION")
    override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
        return when (val decision = urlPolicy.check(url)) {
            UrlDecision.Allow -> false
            UrlDecision.External -> {
                openExternal(url)
                callback?.onExternalUrl(url)
                true
            }
            is UrlDecision.Block -> {
                callback?.onUrlBlocked(url, decision.reason)
                true
            }
        }
    }

    override fun shouldInterceptRequest(
        view: WebView,
        request: WebResourceRequest,
    ): WebResourceResponse? {
        return requestInterceptor?.intercept(request)
    }

    override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        callback?.onPageStarted(url.orEmpty())
        updateNavigationState()
    }

    override fun onPageFinished(view: WebView, url: String?) {
        super.onPageFinished(view, url)
        callback?.onPageFinished(url.orEmpty())
        updateNavigationState()
    }

    override fun onReceivedError(
        view: WebView,
        request: WebResourceRequest,
        error: WebResourceError,
    ) {
        val webError = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            WebViewError(
                url = request.url?.toString(),
                code = error.errorCode,
                description = error.description?.toString().orEmpty(),
                isMainFrame = request.isForMainFrame,
            )
        } else {
            WebViewError(
                url = request.url?.toString(),
                code = -1,
                description = "Unknown WebView error",
                isMainFrame = request.isForMainFrame,
            )
        }
        callback?.onError(webError)
        updateNavigationState()
    }

    @Suppress("DEPRECATION")
    override fun onReceivedError(
        view: WebView,
        errorCode: Int,
        description: String?,
        failingUrl: String?,
    ) {
        callback?.onError(WebViewError(failingUrl, errorCode, description.orEmpty()))
        updateNavigationState()
    }

    override fun onReceivedSslError(
        view: WebView,
        handler: SslErrorHandler,
        error: SslError,
    ) {
        handler.cancel()
        callback?.onError(
            WebViewError(
                url = error.url,
                code = error.primaryError,
                description = "SSL error: ${error.primaryError}",
            ),
        )
    }

    private fun openExternal(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
            .recoverCatching { error ->
                if (error !is ActivityNotFoundException) throw error
            }
    }
}
