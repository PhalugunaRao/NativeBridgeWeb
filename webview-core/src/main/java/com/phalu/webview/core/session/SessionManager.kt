package com.phalu.webview.core.session

import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView

class SessionManager(
    private val cookieManager: CookieManager = CookieManager.getInstance(),
) {
    fun configureCookies(acceptThirdParty: Boolean, webView: WebView? = null) {
        cookieManager.setAcceptCookie(true)
        webView?.let { cookieManager.setAcceptThirdPartyCookies(it, acceptThirdParty) }
    }

    fun setCookie(url: String, cookie: String) {
        cookieManager.setCookie(url, cookie)
        cookieManager.flush()
    }

    fun setAuthCookie(
        url: String,
        name: String,
        value: String,
        secure: Boolean = true,
        httpOnly: Boolean = true,
        sameSite: String = "Lax",
    ) {
        val cookie = buildString {
            append("$name=$value; Path=/")
            if (secure) append("; Secure")
            if (httpOnly) append("; HttpOnly")
            append("; SameSite=$sameSite")
        }
        setCookie(url, cookie)
    }

    fun flush() {
        cookieManager.flush()
    }

    fun clear(includeStorage: Boolean = true, onComplete: (() -> Unit)? = null) {
        cookieManager.removeAllCookies {
            cookieManager.flush()
            if (includeStorage) {
                WebStorage.getInstance().deleteAllData()
            }
            onComplete?.invoke()
        }
    }
}
