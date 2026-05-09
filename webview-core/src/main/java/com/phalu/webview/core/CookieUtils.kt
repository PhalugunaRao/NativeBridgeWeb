package com.phalu.webview.core

import android.webkit.CookieManager
import android.webkit.WebView

/**
 * Utilities for managing cookies in WebView.
 *
 * Cookies are a more reliable way to pass authentication tokens than headers,
 * because headers are only applied to the initial request, while cookies
 * are automatically included in every request to matching domains.
 */
object CookieUtils {

    /**
     * Sets a cookie for the given domain.
     *
     * @param domain The domain for which the cookie is valid (e.g., "example.com").
     * @param name Cookie name.
     * @param value Cookie value.
     * @param path Cookie path (default "/").
     * @param secure Whether the cookie should only be sent over HTTPS.
     * @param httpOnly Whether the cookie should be inaccessible to JavaScript.
     */
    @JvmOverloads
    fun setCookie(
        domain: String,
        name: String,
        value: String,
        path: String = "/",
        secure: Boolean = true,
        httpOnly: Boolean = false
    ) {
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        val cookieValue = buildString {
            append("$name=$value; Domain=$domain; Path=$path")
            if (secure) append("; Secure")
            if (httpOnly) append("; HttpOnly")
        }
        cookieManager.setCookie(domain, cookieValue)
    }

    /**
     * Clears all cookies for the given domain.
     */
    fun clearCookies(domain: String) {
        val cookieManager = CookieManager.getInstance()
        val cookies = cookieManager.getCookie(domain)
        cookies?.split(';')?.forEach { cookie ->
            val name = cookie.substringBefore('=').trim()
            cookieManager.setCookie(domain, "$name=; Max-Age=0")
        }
        cookieManager.flush()
    }

    /**
     * Injects cookies into the WebView before loading a URL.
     * This ensures the cookies are present for the initial request.
     */
    fun injectCookies(webView: WebView, domain: String, cookies: Map<String, String>) {
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookies.forEach { (name, value) ->
            cookieManager.setCookie(domain, "$name=$value")
        }
        cookieManager.flush()
    }

    /**
     * Returns true if cookies are enabled and accepted.
     */
    fun areCookiesEnabled(): Boolean {
        return CookieManager.getInstance().acceptCookie()
    }

    /**
     * Enables or disables cookie acceptance globally.
     */
    fun setAcceptCookies(accept: Boolean) {
        CookieManager.getInstance().setAcceptCookie(accept)
    }
}