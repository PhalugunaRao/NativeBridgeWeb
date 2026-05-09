package com.phalu.webview.core

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.util.AttributeSet
import android.webkit.DownloadListener
import android.webkit.ValueCallback
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ProgressBar
import androidx.annotation.MainThread
import androidx.core.view.isVisible
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.phalu.webview.core.client.AdvancedWebChromeClient
import com.phalu.webview.core.client.AdvancedWebViewClient
import com.phalu.webview.core.config.AdvancedWebViewConfig
import com.phalu.webview.core.config.DarkMode
import com.phalu.webview.core.download.AndroidDownloadController
import com.phalu.webview.core.network.ConnectivityObserver
import com.phalu.webview.core.network.HeaderManager
import com.phalu.webview.core.pwa.PwaManager
import com.phalu.webview.core.security.UrlPolicy
import com.phalu.webview.core.session.SessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AdvancedWebView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr), AdvancedWebViewController {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val connectivityObserver = ConnectivityObserver(context)
    private val sessionManager = SessionManager()
    private val progressBar = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal)
    private val swipeRefreshLayout = SwipeRefreshLayout(context)
    private var callback: AdvancedWebViewCallback? = null
    private var requestInterceptor: RequestInterceptor? = null
    private var permissionHandler: PermissionRequestHandler? = null
    private var fileChooserHandler: FileChooserHandler? = null
    private var chromeClient: AdvancedWebChromeClient? = null
    private var config: AdvancedWebViewConfig? = null
    private var headerManager = HeaderManager()
    private var downloadController = AndroidDownloadController(context)

    private val mutableState = MutableStateFlow(BrowserState())
    override val state: StateFlow<BrowserState> = mutableState
    override val webView: SecureWebView = SecureWebView(context)

    init {
        setWillNotDraw(false)
        swipeRefreshLayout.addView(
            webView,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT),
        )
        addView(swipeRefreshLayout, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(
            progressBar,
            LayoutParams(LayoutParams.MATCH_PARENT, resources.displayMetrics.density.times(3).toInt()),
        )
        progressBar.max = 100
        progressBar.isVisible = false
        swipeRefreshLayout.setOnRefreshListener { reload() }
    }

    @SuppressLint("SetJavaScriptEnabled")
    @MainThread
    fun configure(
        config: AdvancedWebViewConfig,
        lifecycleOwner: LifecycleOwner? = null,
        permissionHandler: PermissionRequestHandler? = null,
        fileChooserHandler: FileChooserHandler? = null,
        callback: AdvancedWebViewCallback? = null,
        requestInterceptor: RequestInterceptor? = null,
    ): AdvancedWebView {
        this.config = config
        this.callback = callback
        this.requestInterceptor = requestInterceptor
        this.permissionHandler = permissionHandler
        this.fileChooserHandler = fileChooserHandler
        this.headerManager = HeaderManager(config.headers)

        sessionManager.configureCookies(acceptThirdParty = true, webView = webView)
        PwaManager(context.applicationContext, config.pwa).configure()
        configureSettings(config)
        configureClients(config)
        lifecycleOwner?.let { attachToLifecycle(it) }
        loadUrl(config.url, config.headers)
        return this
    }

    private fun configureSettings(config: AdvancedWebViewConfig) {
        with(webView.settings) {
            javaScriptEnabled = config.settings.javaScriptEnabled
            domStorageEnabled = config.settings.domStorageEnabled
            databaseEnabled = config.settings.databaseEnabled
            setSupportMultipleWindows(config.settings.supportMultipleWindows)
            javaScriptCanOpenWindowsAutomatically = config.settings.supportMultipleWindows
            mediaPlaybackRequiresUserGesture = config.settings.mediaPlaybackRequiresUserGesture
            mixedContentMode = config.settings.mixedContentMode
            cacheMode = config.settings.cacheMode
            setSupportZoom(config.settings.supportZoom)
            builtInZoomControls = config.settings.supportZoom
            displayZoomControls = false
            textZoom = config.settings.textZoom
            allowFileAccess = config.settings.allowFileAccess
            allowContentAccess = config.settings.allowContentAccess
            loadWithOverviewMode = true
            useWideViewPort = true
            loadsImagesAutomatically = true
            mediaPlaybackRequiresUserGesture = config.settings.mediaPlaybackRequiresUserGesture
            if (config.userAgent != null) {
                userAgentString = config.userAgent
            } else if (config.additionalUserAgentSuffix != null && !userAgentString.contains("AdvancedWebViewSDK")) {
                userAgentString += config.additionalUserAgentSuffix
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                forceDark = when (config.settings.darkMode) {
                    DarkMode.FOLLOW_SYSTEM -> WebSettings.FORCE_DARK_AUTO
                    DarkMode.LIGHT -> WebSettings.FORCE_DARK_OFF
                    DarkMode.DARK -> WebSettings.FORCE_DARK_ON
                }
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                safeBrowsingEnabled = config.settings.safeBrowsingEnabled
            }
        }
        swipeRefreshLayout.isEnabled = config.settings.pullToRefreshEnabled
    }

    private fun configureClients(config: AdvancedWebViewConfig) {
        val urlPolicy = UrlPolicy(config.security)
        webView.webViewClient = AdvancedWebViewClient(
            context = context.applicationContext,
            urlPolicy = urlPolicy,
            callback = callback,
            requestInterceptor = requestInterceptor,
            updateNavigationState = ::updateNavigationState,
        )
        chromeClient = AdvancedWebChromeClient(
            permissionHandler = permissionHandler,
            fileChooserHandler = fileChooserHandler,
            callback = callback,
            createWindow = ::createPopupWebView,
            updateProgress = ::updateProgress,
        )
        webView.webChromeClient = chromeClient
        webView.setDownloadListener(DownloadListener { url, userAgent, contentDisposition, mimeType, contentLength ->
            val request = DownloadRequest(url, userAgent, contentDisposition, mimeType, contentLength)
            callback?.onDownloadRequested(request)
            if (config.downloads.enabled) downloadController.enqueue(request)
        })
        scope.launch {
            connectivityObserver.isOnline.collect { online ->
                mutableState.update { it.copy(isOffline = !online) }
            }
        }
    }

    private fun createPopupWebView(parent: WebView): WebView {
        val child = SecureWebView(parent.context)
        child.settings.javaScriptEnabled = parent.settings.javaScriptEnabled
        child.webViewClient = parent.webViewClient
        child.webChromeClient = parent.webChromeClient
        addView(child, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        return child
    }

    override fun loadUrl(url: String, headers: Map<String, String>) {
        val mergedHeaders = LinkedHashMap<String, String>()
        mergedHeaders.putAll(headerManager.headersFor(url))
        mergedHeaders.putAll(requestInterceptor?.headersFor(url).orEmpty())
        mergedHeaders.putAll(headers)
        if (mergedHeaders.isEmpty()) webView.loadUrl(url) else webView.loadUrl(url, mergedHeaders)
        mutableState.update { it.copy(url = url, isLoading = true, lastError = null) }
    }

    override fun reload() {
        webView.reload()
    }

    override fun stopLoading() {
        webView.stopLoading()
        mutableState.update { it.copy(isLoading = false) }
    }

    override fun goBack(): Boolean {
        if (!webView.canGoBack()) return false
        webView.goBack()
        return true
    }

    override fun goForward(): Boolean {
        if (!webView.canGoForward()) return false
        webView.goForward()
        return true
    }

    override fun evaluateJavascript(script: String, callback: ValueCallback<String>?) {
        webView.evaluateJavascript(script, callback)
    }

    override fun saveState(outState: Bundle) {
        webView.saveState(outState)
    }

    override fun restoreState(state: Bundle) {
        webView.restoreState(state)
        updateNavigationState()
    }

    override fun clearSession(includeCookies: Boolean) {
        if (includeCookies) {
            sessionManager.clear(includeStorage = true)
        } else {
            webView.clearCache(true)
            webView.clearHistory()
        }
    }

    fun attachToLifecycle(owner: LifecycleOwner) {
        owner.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) {
                webView.onResume()
                webView.resumeTimers()
            }

            override fun onPause(owner: LifecycleOwner) {
                webView.onPause()
                webView.pauseTimers()
                sessionManager.flush()
            }

            override fun onDestroy(owner: LifecycleOwner) {
                owner.lifecycle.removeObserver(this)
                destroy()
            }
        })
    }

    private fun updateProgress(progress: Int) {
        progressBar.progress = progress
        progressBar.isVisible = progress in 1..99
        swipeRefreshLayout.isRefreshing = progress in 1..99
        mutableState.update { it.copy(progress = progress, isLoading = progress in 1..99) }
    }

    private fun updateNavigationState() {
        mutableState.update {
            it.copy(
                url = webView.url,
                title = webView.title,
                progress = webView.progress,
                canGoBack = webView.canGoBack(),
                canGoForward = webView.canGoForward(),
                isLoading = webView.progress in 1..99,
            )
        }
    }

    override fun destroy() {
        chromeClient?.dispose()
        scope.cancel()
        webView.stopLoading()
        webView.webChromeClient = null
        webView.webViewClient = WebViewClient()
        webView.removeAllViews()
        removeAllViews()
        webView.destroy()
    }
}
