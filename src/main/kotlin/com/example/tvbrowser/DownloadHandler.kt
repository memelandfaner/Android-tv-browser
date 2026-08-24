package com.example.tvbrowser

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.webkit.MimeTypeMap
import android.webkit.URLUtil
import android.widget.Toast
import java.io.File

object DownloadHandler {

    fun enqueueDownload(
        context: Context,
        url: String,
        userAgent: String?,
        contentDisposition: String?,
        mimeType: String?
    ) {
        try {
            val request = DownloadManager.Request(Uri.parse(url))
            val filename = URLUtil.guessFileName(url, contentDisposition, mimeType)

            request.setMimeType(mimeType)
            if (!userAgent.isNullOrEmpty()) {
                request.addRequestHeader("User-Agent", userAgent)
            }
            request.setDescription("Prenašam datoteko za Android TV...")
            request.setTitle(filename)
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename)

            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            dm.enqueue(request)

            Toast.makeText(context, "⬇️ Začenjam prenos: $filename", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Napaka pri prenosu: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    fun openDownloadedFile(context: Context, file: File) {
        if (!file.exists()) {
            Toast.makeText(context, "Datoteka ne obstaja več.", Toast.LENGTH_SHORT).show()
            return
        }

        if (file.name.lowercase().endsWith(".apk")) {
            installApk(context, file)
            return
        }

        val extension = MimeTypeMap.getFileExtensionFromUrl(file.absolutePath)
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "*/*"

        try {
            val contentUri = TvFileProvider.getUriForFile(context, file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, mime)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Ni nameščene aplikacije za odpiranje te datoteke.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun installApk(context: Context, apkFile: File) {
        try {
            val contentUri = TvFileProvider.getUriForFile(context, apkFile)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Namestitev ni uspela: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
