package com.phalu.webview.core.security

import android.net.Uri
import com.phalu.webview.core.config.SecurityConfig

class UrlPolicy(
    private val config: SecurityConfig,
) {
    fun check(url: String): UrlDecision {
        val uri = runCatching { Uri.parse(url) }.getOrNull()
            ?: return UrlDecision.Block("Malformed URL")
        val scheme = uri.scheme?.lowercase()
            ?: return UrlDecision.Block("URL has no scheme")
        val host = uri.host?.lowercase()

        if (scheme !in config.allowedSchemes) {
            return if (config.openExternalSchemes) {
                UrlDecision.External
            } else {
                UrlDecision.Block("Scheme '$scheme' is not allowed")
            }
        }

        if (config.blockCleartextMainFrameLoads && scheme == "http") {
            return UrlDecision.Block("Cleartext HTTP main-frame loads are blocked")
        }

        if (host == null) return UrlDecision.Allow

        if (config.blockedHosts.any { blocked -> host == blocked || host.endsWith(".$blocked") }) {
            return UrlDecision.Block("Host '$host' is blocked")
        }

        if (config.allowedHosts.isNotEmpty() &&
            config.allowedHosts.none { allowed -> host == allowed || host.endsWith(".$allowed") }
        ) {
            return UrlDecision.Block("Host '$host' is not in the allowlist")
        }

        return UrlDecision.Allow
    }
}

sealed interface UrlDecision {
    data object Allow : UrlDecision
    data object External : UrlDecision
    data class Block(val reason: String) : UrlDecision
}
