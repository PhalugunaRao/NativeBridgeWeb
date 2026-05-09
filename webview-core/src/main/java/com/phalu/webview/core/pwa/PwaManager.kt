package com.phalu.webview.core.pwa

import android.content.Context
import android.os.Build
import android.webkit.ServiceWorkerClient
import android.webkit.ServiceWorkerController
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import androidx.webkit.ServiceWorkerControllerCompat
import androidx.webkit.ServiceWorkerWebSettingsCompat
import androidx.webkit.WebViewFeature
import com.phalu.webview.core.config.PwaConfig

class PwaManager(
    private val context: Context,
    private val config: PwaConfig,
) {
    fun configure() {
        if (!config.serviceWorkersEnabled) return

        if (WebViewFeature.isFeatureSupported(WebViewFeature.SERVICE_WORKER_BASIC_USAGE)) {
            val controller = ServiceWorkerControllerCompat.getInstance()
            controller.serviceWorkerWebSettings.apply {
                cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
                allowContentAccess = true
                allowFileAccess = false
                if (WebViewFeature.isFeatureSupported(WebViewFeature.SERVICE_WORKER_CACHE_MODE)) {
                    cacheMode = if (config.offlineCacheEnabled) {
                        android.webkit.WebSettings.LOAD_DEFAULT
                    } else {
                        android.webkit.WebSettings.LOAD_NO_CACHE
                    }
                }
                if (WebViewFeature.isFeatureSupported(WebViewFeature.SERVICE_WORKER_CONTENT_ACCESS)) {
                    allowContentAccess = true
                }
                if (WebViewFeature.isFeatureSupported(WebViewFeature.SERVICE_WORKER_FILE_ACCESS)) {
                    allowFileAccess = false
                }
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            ServiceWorkerController.getInstance().serviceWorkerWebSettings.allowFileAccess = false
        }
    }

    fun installNoopInterceptor() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return
        if (!config.serviceWorkersEnabled) return
        ServiceWorkerController.getInstance().setServiceWorkerClient(object : ServiceWorkerClient() {
            override fun shouldInterceptRequest(request: WebResourceRequest): WebResourceResponse? {
                return null
            }
        })
    }
}
