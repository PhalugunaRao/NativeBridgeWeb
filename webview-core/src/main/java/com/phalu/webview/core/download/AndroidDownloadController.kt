package com.phalu.webview.core.download

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.webkit.URLUtil
import com.phalu.webview.core.DownloadRequest

class AndroidDownloadController(context: Context) {
    private val appContext = context.applicationContext
    private val downloadManager =
        appContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    fun enqueue(download: DownloadRequest): Long {
        val fileName = URLUtil.guessFileName(
            download.url,
            download.contentDisposition,
            download.mimeType,
        )
        val request = DownloadManager.Request(Uri.parse(download.url))
            .setTitle(fileName)
            .setDescription(download.url)
            .setMimeType(download.mimeType)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)

        download.userAgent?.let { request.addRequestHeader("User-Agent", it) }
        return downloadManager.enqueue(request)
    }
}
