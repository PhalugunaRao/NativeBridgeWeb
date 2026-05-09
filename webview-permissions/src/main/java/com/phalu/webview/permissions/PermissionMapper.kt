package com.phalu.webview.permissions

import android.Manifest
import android.os.Build
import android.webkit.PermissionRequest
import com.phalu.webview.core.config.WebPermissions

object PermissionMapper {
    fun androidPermissionsFor(config: WebPermissions): List<String> = buildList {
        if (config.camera) add(Manifest.permission.CAMERA)
        if (config.microphone) add(Manifest.permission.RECORD_AUDIO)
        if (config.location) add(Manifest.permission.ACCESS_FINE_LOCATION)
        if (config.notifications && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (config.bluetooth && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Manifest.permission.BLUETOOTH_CONNECT)
            add(Manifest.permission.BLUETOOTH_SCAN)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (config.photos) add(Manifest.permission.READ_MEDIA_IMAGES)
            if (config.videos) add(Manifest.permission.READ_MEDIA_VIDEO)
            if (config.audio) add(Manifest.permission.READ_MEDIA_AUDIO)
        } else if (config.storage || config.photos || config.videos || config.audio) {
            add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }.distinct()

    fun androidPermissionsForWebResources(resources: Array<String>): List<String> {
        return resources.flatMap { resource ->
            when (resource) {
                PermissionRequest.RESOURCE_VIDEO_CAPTURE -> listOf(Manifest.permission.CAMERA)
                PermissionRequest.RESOURCE_AUDIO_CAPTURE -> listOf(Manifest.permission.RECORD_AUDIO)
                PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID -> emptyList()
                else -> emptyList()
            }
        }.distinct()
    }

    fun geolocationPermissions(): List<String> = listOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    )
}
