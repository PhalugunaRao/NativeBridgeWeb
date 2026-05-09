package com.phalu.webview.core.client

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.ConsoleMessage
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import com.phalu.webview.core.AdvancedWebViewCallback
import com.phalu.webview.core.FileChooserHandler
import com.phalu.webview.core.PermissionRequestHandler

class AdvancedWebChromeClient(
    private val permissionHandler: PermissionRequestHandler?,
    private val fileChooserHandler: FileChooserHandler?,
    private val callback: AdvancedWebViewCallback?,
    private val createWindow: ((WebView) -> WebView?)?,
    private val updateProgress: (Int) -> Unit,
) : WebChromeClient() {
    private var customView: View? = null
    private var customViewCallback: CustomViewCallback? = null
    private var originalSystemUiVisibility: Int = 0
    private var originalOrientation: Int = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED

    override fun onProgressChanged(view: WebView, newProgress: Int) {
        updateProgress(newProgress)
        callback?.onProgressChanged(newProgress)
    }

    override fun onReceivedTitle(view: WebView, title: String?) {
        callback?.onTitleChanged(title)
    }

    override fun onPermissionRequest(request: PermissionRequest) {
        permissionHandler?.handleWebPermissionRequest(request) ?: request.deny()
    }

    override fun onGeolocationPermissionsShowPrompt(
        origin: String,
        callback: GeolocationPermissions.Callback,
    ) {
        permissionHandler?.handleGeolocationPermissionRequest(origin, callback)
            ?: callback.invoke(origin, false, false)
    }

    override fun onShowFileChooser(
        webView: WebView,
        filePathCallback: ValueCallback<Array<Uri>>,
        fileChooserParams: FileChooserParams,
    ): Boolean {
        return fileChooserHandler?.openFileChooser(fileChooserParams, filePathCallback) ?: false
    }

    override fun onCreateWindow(
        view: WebView,
        isDialog: Boolean,
        isUserGesture: Boolean,
        resultMsg: android.os.Message,
    ): Boolean {
        val child = createWindow?.invoke(view) ?: return false
        val transport = resultMsg.obj as WebView.WebViewTransport
        transport.webView = child
        resultMsg.sendToTarget()
        return true
    }

    @SuppressLint("SourceLockedOrientationActivity")
    override fun onShowCustomView(view: View, callback: CustomViewCallback) {
        val activity = view.context.findActivity()
        val decor = activity?.window?.decorView as? ViewGroup ?: return
        if (customView != null) {
            callback.onCustomViewHidden()
            return
        }
        originalSystemUiVisibility = decor.systemUiVisibility
        originalOrientation = activity.requestedOrientation
        customView = view
        customViewCallback = callback
        decor.addView(
            view,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        decor.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        this.callback?.onFullscreenChanged(true)
    }

    override fun onHideCustomView() {
        val view = customView ?: return
        val activity = view.context.findActivity()
        val decor = activity?.window?.decorView as? ViewGroup
        decor?.removeView(view)
        decor?.systemUiVisibility = originalSystemUiVisibility
        activity?.requestedOrientation = originalOrientation
        activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        customView = null
        customViewCallback?.onCustomViewHidden()
        customViewCallback = null
        callback?.onFullscreenChanged(false)
    }

    override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
        return super.onConsoleMessage(consoleMessage)
    }

    fun dispose() {
        onHideCustomView()
    }

    private tailrec fun Context.findActivity(): Activity? {
        return when (this) {
            is Activity -> this
            is ContextWrapper -> baseContext.findActivity()
            else -> null
        }
    }
}
