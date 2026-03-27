package com.swiftshare

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import fi.iki.elonen.NanoHTTPD
import java.io.InputStream

class FileHttpServer(
    private val port: Int,
    private val context: Context,
    private var fileUri: Uri? = null
) : NanoHTTPD(port) {

    fun setFileToServe(uri: Uri) {
        this.fileUri = uri
    }

    override fun serve(session: IHTTPSession): Response {
        val uri = fileUri ?: return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "No file selected to serve")

        return try {
            val fileName = getFileName(uri) ?: "downloaded_file"
            val fileSize = getFileSize(uri)
            val mimeType = getMimeType(fileName)
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)

            if (inputStream != null) {
                val response = newFixedLengthResponse(Response.Status.OK, mimeType, inputStream, fileSize)
                response.addHeader("Content-Disposition", "attachment; filename=\"$fileName\"")
                response
            } else {
                newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Failed to open file")
            }
        } catch (e: Exception) {
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, e.message)
        }
    }

    @Suppress("Range")
    private fun getFileName(uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    result = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME))
                }
            } finally {
                cursor?.close()
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/') ?: -1
            if (cut != -1) {
                result = result?.substring(cut + 1)
            }
        }
        return result
    }

    @Suppress("Range")
    private fun getFileSize(uri: Uri): Long {
        var size = 0L
        if (uri.scheme == "content") {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    size = cursor.getLong(cursor.getColumnIndex(OpenableColumns.SIZE))
                }
            } finally {
                cursor?.close()
            }
        }
        return size
    }

    private fun getMimeType(fileName: String): String {
        val extension = MimeTypeMap.getFileExtensionFromUrl(fileName)
        return if (extension != null) {
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.lowercase()) ?: "application/octet-stream"
        } else {
            "application/octet-stream"
        }
    }
}
