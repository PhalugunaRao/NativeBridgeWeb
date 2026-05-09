package com.phalu.webview.permissions.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.app.AlertDialog
import com.phalu.webview.permissions.model.PermissionCopy

interface PermissionUiDelegate {
    fun showRationale(
        context: Context,
        copy: PermissionCopy,
        onContinue: () -> Unit,
        onCancel: () -> Unit,
    )

    fun showSettingsDialog(
        context: Context,
        copy: PermissionCopy,
        onOpenSettings: () -> Unit,
        onCancel: () -> Unit,
    )
}

class DefaultPermissionUiDelegate : PermissionUiDelegate {
    override fun showRationale(
        context: Context,
        copy: PermissionCopy,
        onContinue: () -> Unit,
        onCancel: () -> Unit,
    ) {
        AlertDialog.Builder(context)
            .setTitle(copy.title)
            .setMessage(copy.message)
            .setPositiveButton(copy.positive) { _, _ -> onContinue() }
            .setNegativeButton(copy.negative) { _, _ -> onCancel() }
            .show()
    }

    override fun showSettingsDialog(
        context: Context,
        copy: PermissionCopy,
        onOpenSettings: () -> Unit,
        onCancel: () -> Unit,
    ) {
        AlertDialog.Builder(context)
            .setTitle(copy.title)
            .setMessage(copy.message)
            .setPositiveButton(copy.positive) { _, _ -> onOpenSettings() }
            .setNegativeButton(copy.negative) { _, _ -> onCancel() }
            .show()
    }
}

fun Context.openAppSettings() {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", packageName, null),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    startActivity(intent)
}
