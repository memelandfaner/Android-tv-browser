package com.example.tvbrowser

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File
import java.io.FileNotFoundException

class TvFileProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor {
        val file = getFileForUri(uri)
        val cols = projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        val cursor = MatrixCursor(cols)
        val row = cursor.newRow()
        for (col in cols) {
            when (col) {
                OpenableColumns.DISPLAY_NAME -> row.add(file.name)
                OpenableColumns.SIZE -> row.add(file.length())
                else -> row.add(null)
            }
        }
        return cursor
    }

    override fun getType(uri: Uri): String? {
        val file = getFileForUri(uri)
        val name = file.name.lowercase()
        return when {
            name.endsWith(".apk") -> "application/vnd.android.package-archive"
            name.endsWith(".mp4") -> "video/mp4"
            name.endsWith(".mkv") -> "video/x-matroska"
            name.endsWith(".jpg") || name.endsWith(".jpeg") -> "image/jpeg"
            name.endsWith(".png") -> "image/png"
            name.endsWith(".pdf") -> "application/pdf"
            else -> "application/octet-stream"
        }
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val file = getFileForUri(uri)
        if (!file.exists()) {
            throw FileNotFoundException("Datoteka ne obstaja: ${file.absolutePath}")
        }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    private fun getFileForUri(uri: Uri): File {
        val path = uri.path ?: throw FileNotFoundException("Prazna pot")
        val realPath = if (path.startsWith("/")) path else "/$path"
        val file = File(realPath)
        val canonical = file.canonicalPath
        val externalStorage = Environment.getExternalStorageDirectory().canonicalPath
        if (!canonical.startsWith(externalStorage)) {
            throw SecurityException("Nedovoljen dostop do poti: $canonical")
        }
        return file
    }

    companion object {
        const val AUTHORITY = "com.example.tvbrowser.fileprovider"

        fun getUriForFile(context: Context, file: File): Uri {
            val absPath = file.absolutePath
            val cleanPath = if (absPath.startsWith("/")) absPath else "/$absPath"
            return Uri.parse("content://$AUTHORITY$cleanPath")
        }
    }
}
