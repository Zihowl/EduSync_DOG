package dev.zihowl.dog.utils

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

object AttachmentUtils {

    fun copyUriToInternalStorage(context: Context, uri: Uri): File? {
        return try {
            val attachmentsDir = File(context.filesDir, "attachments").apply {
                if (!exists()) mkdirs()
            }
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val originalName = getOriginalFileName(context, uri) ?: (uri.lastPathSegment ?: "file")
            val ext = originalName.substringAfterLast('.', "")
            val safeName = if (ext.isNotEmpty()) "${System.currentTimeMillis()}.$ext" else "${System.currentTimeMillis()}_file"
            val destFile = File(attachmentsDir, safeName)
            inputStream.use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            destFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun openAttachment(context: Context, attachmentPath: String?, attachmentName: String?) {
        if (attachmentPath.isNullOrEmpty()) {
            Toast.makeText(context, "No hay adjunto disponible", Toast.LENGTH_SHORT).show()
            return
        }
        val file = File(attachmentPath)
        if (!file.exists()) {
            Toast.makeText(context, "El archivo adjunto ya no existe", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val mimeType = getMimeTypeFromExtension(file.name)
            val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(viewIntent, attachmentName ?: "Abrir adjunto").apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "No hay aplicaciones para abrir este archivo", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Error al abrir el archivo: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun getOriginalFileName(context: Context, uri: Uri): String? {
        var name: String? = null
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) name = cursor.getString(idx)
            }
        }
        return name
    }

    private fun getMimeTypeFromExtension(fileName: String): String {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: when (ext) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "pdf" -> "application/pdf"
            else -> "*/*"
        }
    }
}
