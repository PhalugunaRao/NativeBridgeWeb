package com.phalu.webview.security

import android.content.Context
import android.net.Uri
import android.os.Build
import android.webkit.WebSettings
import android.webkit.WebView
import com.phalu.webview.core.SecureWebView

/**
 * Security utilities and best practices for WebView configuration.
 *
 * This class provides static methods to audit and enforce security settings,
 * detect common misconfigurations, and suggest improvements.
 */
object SecurityUtils {

    /**
     * Checks if a WebView is configured with safe defaults.
     *
     * @param webView The WebView to audit.
     * @return A list of security warnings (empty if all checks pass).
     */
    fun auditWebView(webView: WebView): List<SecurityWarning> {
        val warnings = mutableListOf<SecurityWarning>()
        val settings = webView.settings

        // 1. JavaScript enabled without domain restriction
        if (settings.javaScriptEnabled) {
            warnings.add(SecurityWarning(
                code = "JS_ENABLED",
                severity = Severity.MEDIUM,
                message = "JavaScript is enabled. Ensure you trust the loaded content and restrict JS interface exposure.",
                recommendation = "Consider disabling JavaScript unless required, and use addJavascriptInterface with caution."
            ))
        }

        // 2. File access
        if (settings.allowFileAccess) {
            warnings.add(SecurityWarning(
                code = "FILE_ACCESS_ENABLED",
                severity = Severity.HIGH,
                message = "File access is enabled. This could allow reading local files via file:// URLs.",
                recommendation = "Disable file access unless absolutely necessary: settings.allowFileAccess = false"
            ))
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            if (settings.allowFileAccessFromFileURLs) {
                warnings.add(SecurityWarning(
                    code = "FILE_ACCESS_FROM_FILE_URLS",
                    severity = Severity.HIGH,
                    message = "allowFileAccessFromFileURLs is true. This allows file:// URLs to access other file:// URLs.",
                    recommendation = "Set settings.allowFileAccessFromFileURLs = false"
                ))
            }
            if (settings.allowUniversalAccessFromFileURLs) {
                warnings.add(SecurityWarning(
                    code = "UNIVERSAL_ACCESS_FROM_FILE_URLS",
                    severity = Severity.CRITICAL,
                    message = "allowUniversalAccessFromFileURLs is true. This allows file:// URLs to access any origin.",
                    recommendation = "Set settings.allowUniversalAccessFromFileURLs = false immediately."
                ))
            }
        }

        // 3. Mixed content
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (settings.mixedContentMode != WebSettings.MIXED_CONTENT_NEVER_ALLOW) {
                warnings.add(SecurityWarning(
                    code = "MIXED_CONTENT_ALLOWED",
                    severity = Severity.MEDIUM,
                    message = "Mixed content mode is not set to NEVER_ALLOW. This could allow insecure HTTP resources on HTTPS pages.",
                    recommendation = "Set settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW"
                ))
            }
        }

        // 4. Debugging enabled in release builds - cannot check per WebView, rely on static method
        // No reliable API to detect if debugging is enabled; we skip this check.
        // Developers should ensure WebView.setWebContentsDebuggingEnabled(false) in release builds.

        // 5. No SSL error handler (if custom WebViewClient is not set)
        // This is a heuristic; we cannot detect it reliably.

        return warnings
    }

    /**
     * Applies the highest security configuration to a WebView.
     * This is a stricter version of [SecureWebView.configureSecureDefaults].
     */
    fun applyMaximumSecurity(webView: WebView) {
        val settings = webView.settings
        settings.javaScriptEnabled = false
        settings.allowFileAccess = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            settings.allowFileAccessFromFileURLs = false
            settings.allowUniversalAccessFromFileURLs = false
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        }
        settings.savePassword = false
        settings.saveFormData = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            settings.mediaPlaybackRequiresUserGesture = true
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            WebView.setWebContentsDebuggingEnabled(false)
        }
    }

    /**
     * Validates that a URL is safe to load.
     *
     * @param url The URL to validate.
     * @param allowedSchemes List of allowed schemes (default: http, https).
     * @param blockedHosts List of blocked hostnames (e.g., "evil.com").
     * @return true if the URL is considered safe, false otherwise.
     */
    fun isUrlSafe(
        url: String,
        allowedSchemes: List<String> = listOf("http", "https"),
        blockedHosts: List<String> = emptyList()
    ): Boolean {
        return try {
            val uri = Uri.parse(url)
            val scheme = uri.scheme ?: return false
            if (!allowedSchemes.contains(scheme.lowercase())) {
                return false
            }
            val host = uri.host ?: return false
            !blockedHosts.any { blocked ->
                host.equals(blocked, ignoreCase = true) || host.endsWith(".$blocked", ignoreCase = true)
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Returns true if the app is built in debug mode.
     */
    private fun isDebugBuild(context: Context): Boolean {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            (packageInfo.applicationInfo!!.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
        } catch (e: Exception) {
            false
        }
    }

    data class SecurityWarning(
        val code: String,
        val severity: Severity,
        val message: String,
        val recommendation: String
    )

    enum class Severity {
        LOW, MEDIUM, HIGH, CRITICAL
    }
}