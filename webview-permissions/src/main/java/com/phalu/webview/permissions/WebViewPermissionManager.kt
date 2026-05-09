package com.phalu.webview.permissions

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.phalu.webview.core.FileChooserHandler
import com.phalu.webview.core.PermissionRequestHandler
import com.phalu.webview.core.config.WebPermissions
import com.phalu.webview.permissions.model.PermissionCallbacks
import com.phalu.webview.permissions.model.PermissionCopy
import com.phalu.webview.permissions.model.PermissionState
import com.phalu.webview.permissions.ui.DefaultPermissionUiDelegate
import com.phalu.webview.permissions.ui.PermissionUiDelegate
import com.phalu.webview.permissions.ui.openAppSettings

class WebViewPermissionManager private constructor(
    private val host: Host,
    private val uiDelegate: PermissionUiDelegate,
    private val callbacks: PermissionCallbacks?,
) : PermissionRequestHandler, FileChooserHandler {

    private val prefs = host.context.getSharedPreferences("advanced_webview_permissions", Context.MODE_PRIVATE)
    private var pendingWebRequest: PermissionRequest? = null
    private var pendingGeoRequest: Pair<String, GeolocationPermissions.Callback>? = null
    private var pendingFileCallback: ValueCallback<Array<Uri>>? = null
    private var pendingPermissionCompletion: ((Boolean) -> Unit)? = null

    private lateinit var requestPermissionsLauncher: ActivityResultLauncher<Array<String>>
    private lateinit var fileChooserLauncher: ActivityResultLauncher<Intent>

    init {
        requestPermissionsLauncher = host.registerPermissions { result ->
            val permissions = result.keys.toList()
            val granted = result.values.all { it }
            markAsked(permissions)
            val state = stateFor(permissions)
            callbacks?.onPermissionStateChanged(permissions, state)
            pendingPermissionCompletion?.invoke(granted)
            pendingPermissionCompletion = null
            finishWebPermission(granted)
            finishGeolocation(granted)
        }
        fileChooserLauncher = host.registerFileChooser { result ->
            val uris = WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
            pendingFileCallback?.onReceiveValue(uris ?: emptyArray())
            pendingFileCallback = null
        }
    }

    fun ensurePermissions(
        permissions: WebPermissions,
        onResult: (PermissionState) -> Unit = {},
    ) {
        val androidPermissions = PermissionMapper.androidPermissionsFor(permissions)
        request(androidPermissions) { granted ->
            onResult(if (granted) PermissionState.Granted else stateFor(androidPermissions))
        }
    }

    override fun handleWebPermissionRequest(request: PermissionRequest) {
        val permissions = PermissionMapper.androidPermissionsForWebResources(request.resources)
        if (permissions.isEmpty()) {
            request.grant(request.resources)
            return
        }
        pendingWebRequest?.deny()
        pendingWebRequest = request
        request(permissions) { granted -> finishWebPermission(granted) }
    }

    override fun handleGeolocationPermissionRequest(
        origin: String,
        callback: GeolocationPermissions.Callback,
    ) {
        pendingGeoRequest = origin to callback
        request(PermissionMapper.geolocationPermissions()) { granted -> finishGeolocation(granted) }
    }

    override fun openFileChooser(
        params: WebChromeClient.FileChooserParams,
        callback: ValueCallback<Array<Uri>>,
    ): Boolean {
        pendingFileCallback?.onReceiveValue(emptyArray())
        pendingFileCallback = callback
        return runCatching {
            fileChooserLauncher.launch(params.createIntent())
            true
        }.getOrElse {
            pendingFileCallback?.onReceiveValue(emptyArray())
            pendingFileCallback = null
            false
        }
    }

    fun cleanup() {
        pendingWebRequest?.deny()
        pendingWebRequest = null
        pendingGeoRequest?.let { (origin, callback) -> callback.invoke(origin, false, false) }
        pendingGeoRequest = null
        pendingFileCallback?.onReceiveValue(emptyArray())
        pendingFileCallback = null
    }

    private fun request(permissions: List<String>, onComplete: (Boolean) -> Unit) {
        val required = permissions.filterNot(::isGranted).distinct()
        if (required.isEmpty()) {
            callbacks?.onPermissionStateChanged(permissions, PermissionState.Granted)
            onComplete(true)
            return
        }

        val permanentlyDenied = required.filter(::isPermanentlyDenied)
        if (permanentlyDenied.isNotEmpty()) {
            val copy = copyFor(permanentlyDenied, settings = true)
            callbacks?.onPermissionStateChanged(permanentlyDenied, PermissionState.PermanentlyDenied)
            uiDelegate.showSettingsDialog(
                context = host.context,
                copy = copy,
                onOpenSettings = {
                    host.context.openAppSettings()
                    onComplete(false)
                },
                onCancel = { onComplete(false) },
            )
            return
        }

        val needsRationale = required.filter { host.shouldShowRationale(it) }
        pendingPermissionCompletion = onComplete
        if (needsRationale.isNotEmpty()) {
            callbacks?.onPermissionStateChanged(required, PermissionState.RationaleRequired(needsRationale))
            uiDelegate.showRationale(
                context = host.context,
                copy = copyFor(needsRationale, settings = false),
                onContinue = { requestPermissionsLauncher.launch(required.toTypedArray()) },
                onCancel = {
                    pendingPermissionCompletion = null
                    callbacks?.onPermissionStateChanged(required, PermissionState.Denied)
                    onComplete(false)
                },
            )
        } else {
            requestPermissionsLauncher.launch(required.toTypedArray())
        }
    }

    private fun finishWebPermission(granted: Boolean) {
        val request = pendingWebRequest ?: return
        if (granted) request.grant(request.resources) else request.deny()
        pendingWebRequest = null
    }

    private fun finishGeolocation(granted: Boolean) {
        val pending = pendingGeoRequest ?: return
        pending.second.invoke(pending.first, granted, false)
        pendingGeoRequest = null
    }

    private fun isGranted(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(host.context, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun markAsked(permissions: List<String>) {
        prefs.edit().apply {
            permissions.forEach { putBoolean(it, true) }
        }.apply()
    }

    private fun wasAsked(permission: String): Boolean = prefs.getBoolean(permission, false)

    private fun isPermanentlyDenied(permission: String): Boolean {
        return !isGranted(permission) && wasAsked(permission) && !host.shouldShowRationale(permission)
    }

    private fun stateFor(permissions: List<String>): PermissionState {
        if (permissions.all(::isGranted)) return PermissionState.Granted
        return if (permissions.any(::isPermanentlyDenied)) {
            PermissionState.PermanentlyDenied
        } else {
            PermissionState.Denied
        }
    }

    private fun copyFor(permissions: List<String>, settings: Boolean): PermissionCopy {
        val names = permissions.joinToString { it.substringAfterLast('.') }
        return if (settings) {
            PermissionCopy(
                title = "Permission required",
                message = "This web app needs $names. Please enable it in app settings to continue.",
                positive = "Open settings",
            )
        } else {
            PermissionCopy(
                title = "Allow permission",
                message = "This web app needs $names to complete the requested action.",
            )
        }
    }

    interface Host {
        val context: Context
        fun shouldShowRationale(permission: String): Boolean
        fun registerPermissions(callback: (Map<String, Boolean>) -> Unit): ActivityResultLauncher<Array<String>>
        fun registerFileChooser(callback: (androidx.activity.result.ActivityResult) -> Unit): ActivityResultLauncher<Intent>
    }

    companion object {
        fun from(
            activity: ComponentActivity,
            uiDelegate: PermissionUiDelegate = DefaultPermissionUiDelegate(),
            callbacks: PermissionCallbacks? = null,
        ): WebViewPermissionManager {
            val host = object : Host {
                override val context: Context = activity
                override fun shouldShowRationale(permission: String): Boolean {
                    return activity.shouldShowRequestPermissionRationale(permission)
                }

                override fun registerPermissions(
                    callback: (Map<String, Boolean>) -> Unit,
                ): ActivityResultLauncher<Array<String>> {
                    return activity.registerForActivityResult(
                        ActivityResultContracts.RequestMultiplePermissions(),
                        callback,
                    )
                }

                override fun registerFileChooser(
                    callback: (androidx.activity.result.ActivityResult) -> Unit,
                ): ActivityResultLauncher<Intent> {
                    return activity.registerForActivityResult(
                        ActivityResultContracts.StartActivityForResult(),
                        callback,
                    )
                }
            }
            return WebViewPermissionManager(host, uiDelegate, callbacks)
        }

        fun from(
            fragment: Fragment,
            uiDelegate: PermissionUiDelegate = DefaultPermissionUiDelegate(),
            callbacks: PermissionCallbacks? = null,
        ): WebViewPermissionManager {
            val host = object : Host {
                override val context: Context
                    get() = fragment.requireContext()

                override fun shouldShowRationale(permission: String): Boolean {
                    return fragment.shouldShowRequestPermissionRationale(permission)
                }

                override fun registerPermissions(
                    callback: (Map<String, Boolean>) -> Unit,
                ): ActivityResultLauncher<Array<String>> {
                    return fragment.registerForActivityResult(
                        ActivityResultContracts.RequestMultiplePermissions(),
                        callback,
                    )
                }

                override fun registerFileChooser(
                    callback: (androidx.activity.result.ActivityResult) -> Unit,
                ): ActivityResultLauncher<Intent> {
                    return fragment.registerForActivityResult(
                        ActivityResultContracts.StartActivityForResult(),
                        callback,
                    )
                }
            }
            return WebViewPermissionManager(host, uiDelegate, callbacks)
        }
    }
}

typealias WebViewPermissionDelegate = WebViewPermissionManager

fun ComponentActivity.createWebViewPermissionDelegate(
    uiDelegate: PermissionUiDelegate = DefaultPermissionUiDelegate(),
    callbacks: PermissionCallbacks? = null,
): WebViewPermissionManager = WebViewPermissionManager.from(this, uiDelegate, callbacks)

fun Fragment.createWebViewPermissionDelegate(
    uiDelegate: PermissionUiDelegate = DefaultPermissionUiDelegate(),
    callbacks: PermissionCallbacks? = null,
): WebViewPermissionManager = WebViewPermissionManager.from(this, uiDelegate, callbacks)
