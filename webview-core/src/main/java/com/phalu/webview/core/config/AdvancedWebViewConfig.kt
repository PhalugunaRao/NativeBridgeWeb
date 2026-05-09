package com.phalu.webview.core.config

import android.webkit.WebSettings

data class WebPermissions(
    val camera: Boolean = false,
    val microphone: Boolean = false,
    val location: Boolean = false,
    val storage: Boolean = false,
    val photos: Boolean = storage,
    val videos: Boolean = storage,
    val audio: Boolean = storage,
    val notifications: Boolean = false,
    val bluetooth: Boolean = false,
    val fileAccess: Boolean = false,
)

data class WebViewSettings(
    val javaScriptEnabled: Boolean = true,
    val domStorageEnabled: Boolean = true,
    val databaseEnabled: Boolean = true,
    val supportMultipleWindows: Boolean = true,
    val mediaPlaybackRequiresUserGesture: Boolean = false,
    val mixedContentMode: Int = WebSettings.MIXED_CONTENT_NEVER_ALLOW,
    val cacheMode: Int = WebSettings.LOAD_DEFAULT,
    val supportZoom: Boolean = true,
    val textZoom: Int = 100,
    val darkMode: DarkMode = DarkMode.FOLLOW_SYSTEM,
    val safeBrowsingEnabled: Boolean = true,
    val pullToRefreshEnabled: Boolean = true,
    val allowFileAccess: Boolean = false,
    val allowContentAccess: Boolean = true,
    val allowProtectedMedia: Boolean = true,
)

data class SecurityConfig(
    val allowedHosts: Set<String> = emptySet(),
    val blockedHosts: Set<String> = emptySet(),
    val allowedSchemes: Set<String> = setOf("https", "http"),
    val openExternalSchemes: Boolean = true,
    val sslPinning: SslPinningConfig? = null,
    val blockCleartextMainFrameLoads: Boolean = false,
    val requireSecureCookies: Boolean = true,
)

data class SslPinningConfig(
    val hostPins: Map<String, Set<String>>,
)

data class DownloadConfig(
    val enabled: Boolean = true,
    val showNotification: Boolean = true,
    val allowedMimeTypes: Set<String> = emptySet(),
    val blockedMimeTypes: Set<String> = emptySet(),
)

data class PwaConfig(
    val serviceWorkersEnabled: Boolean = true,
    val offlineCacheEnabled: Boolean = true,
    val geolocationEnabled: Boolean = true,
    val manifestUrl: String? = null,
)

data class AdvancedWebViewConfig(
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val permissions: WebPermissions = WebPermissions(),
    val settings: WebViewSettings = WebViewSettings(),
    val security: SecurityConfig = SecurityConfig(),
    val downloads: DownloadConfig = DownloadConfig(),
    val pwa: PwaConfig = PwaConfig(),
    val userAgent: String? = null,
    val additionalUserAgentSuffix: String? = " AdvancedWebViewSDK/1.0",
    val retryCount: Int = 2,
) {
    init {
        require(url.isNotBlank()) { "url must not be blank" }
        require(headers.keys.none { it.isBlank() }) { "header names must not be blank" }
    }
}

enum class DarkMode {
    FOLLOW_SYSTEM,
    LIGHT,
    DARK,
}

class AdvancedWebViewConfigBuilder {
    private var url: String = ""
    private var headers: Map<String, String> = emptyMap()
    private var permissions: WebPermissions = WebPermissions()
    private var settings: WebViewSettings = WebViewSettings()
    private var security: SecurityConfig = SecurityConfig()
    private var downloads: DownloadConfig = DownloadConfig()
    private var pwa: PwaConfig = PwaConfig()
    private var userAgent: String? = null
    private var userAgentSuffix: String? = " AdvancedWebViewSDK/1.0"
    private var retryCount: Int = 2

    fun url(value: String) = apply { url = value }
    fun headers(value: Map<String, String>) = apply { headers = value }
    fun permissions(value: WebPermissions) = apply { permissions = value }
    fun settings(value: WebViewSettings) = apply { settings = value }
    fun security(value: SecurityConfig) = apply { security = value }
    fun downloads(value: DownloadConfig) = apply { downloads = value }
    fun pwa(value: PwaConfig) = apply { pwa = value }
    fun userAgent(value: String?) = apply { userAgent = value }
    fun userAgentSuffix(value: String?) = apply { userAgentSuffix = value }
    fun retryCount(value: Int) = apply { retryCount = value.coerceAtLeast(0) }

    fun build(): AdvancedWebViewConfig = AdvancedWebViewConfig(
        url = url,
        headers = headers,
        permissions = permissions,
        settings = settings,
        security = security,
        downloads = downloads,
        pwa = pwa,
        userAgent = userAgent,
        additionalUserAgentSuffix = userAgentSuffix,
        retryCount = retryCount,
    )
}
