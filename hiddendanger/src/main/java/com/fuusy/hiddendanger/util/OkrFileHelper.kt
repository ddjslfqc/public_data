package com.fuusy.hiddendanger.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File

object OkrFileHelper {

    fun resolveUploadFile(context: Context, path: String): File? = when {
        path.startsWith("http://") || path.startsWith("https://") -> null
        path.startsWith("content://") -> copyUriToTempFile(context, Uri.parse(path))
        else -> File(path).takeIf { it.exists() }
    }

    fun copyUriToTempFile(context: Context, uri: Uri, suffix: String = ""): File? {
        return try {
            val resolver = context.contentResolver
            val nameFromCursor = resolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
            val ext = when {
                suffix.isNotBlank() -> suffix
                nameFromCursor?.substringAfterLast('.', missingDelimiterValue = "")
                    .orEmpty()
                    .isNotBlank() -> "." + nameFromCursor!!.substringAfterLast('.')
                else -> {
                    when (resolver.getType(uri).orEmpty()) {
                        "image/png" -> ".png"
                        "image/jpeg", "image/jpg" -> ".jpg"
                        "video/mp4" -> ".mp4"
                        else -> ".dat"
                    }
                }
            }
            val tempFile = File.createTempFile("okr_upload_", ext, context.cacheDir)
            resolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output -> input.copyTo(output) }
            }
            tempFile
        } catch (_: Exception) {
            null
        }
    }
}
