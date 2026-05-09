package com.phalu.webview.security

import android.net.Uri
import com.phalu.webview.core.config.SecurityConfig
import okhttp3.CertificatePinner

class EnterpriseSecurityPolicy(
    private val config: SecurityConfig,
) {
    fun certificatePinner(): CertificatePinner? {
        val pins = config.sslPinning?.hostPins.orEmpty()
        if (pins.isEmpty()) return null
        return CertificatePinner.Builder().apply {
            pins.forEach { (host, hostPins) ->
                hostPins.forEach { pin -> add(host, pin) }
            }
        }.build()
    }

    fun validateBridgeOrigin(url: String?): Boolean {
        val host = runCatching { Uri.parse(url).host?.lowercase() }.getOrNull() ?: return false
        if (config.allowedHosts.isEmpty()) return true
        return config.allowedHosts.any { allowed -> host == allowed || host.endsWith(".$allowed") }
    }

    fun isSecureCookieRequired(): Boolean = config.requireSecureCookies
}
