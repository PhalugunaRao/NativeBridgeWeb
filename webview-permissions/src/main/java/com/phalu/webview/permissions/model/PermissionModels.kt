package com.phalu.webview.permissions.model

sealed interface PermissionState {
    data object Granted : PermissionState
    data object Denied : PermissionState
    data object PermanentlyDenied : PermissionState
    data class RationaleRequired(val permissions: List<String>) : PermissionState
}

interface PermissionCallbacks {
    fun onPermissionStateChanged(permissions: List<String>, state: PermissionState) = Unit
}

data class PermissionCopy(
    val title: String,
    val message: String,
    val positive: String = "Continue",
    val negative: String = "Not now",
)
