package com.n9nik.pdftools.pdf

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import java.io.File
import java.io.OutputStream

/**
 * Storage helpers: everything goes through the Storage Access Framework for
 * input (no broad storage permission) and MediaStore Downloads for output.
 */
object FileUtils {

    fun displayName(context: Context, uri: Uri): String {
        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && c.moveToFirst()) {
                c.getString(idx)?.let { return it }
            }
        }
        return "document.pdf"
    }

    fun sizeOf(context: Context, uri: Uri): Long {
        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(OpenableColumns.SIZE)
            if (idx >= 0 && c.moveToFirst()) return c.getLong(idx)
        }
        return -1L
    }

    fun baseName(name: String): String {
        val stripped = name.substringAfterLast('/')
        return if (stripped.endsWith(".pdf", ignoreCase = true)) stripped.dropLast(4) else stripped
    }

    /**
     * Create an empty entry in Downloads/TinyPDF and return its Uri.
     * Open an output stream on it when ready to write.
     */
    fun createDownloadEntry(context: Context, fileName: String): Uri {
        val safeName = fileName.replace(Regex("[^A-Za-z0-9._-]"), "_").ifEmpty { "output.pdf" }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, safeName)
                put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/TinyPDF")
            }
            return context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw java.io.IOException("Could not create file in Downloads.")
        }
        val dir = File(context.getExternalFilesDir(null), "TinyPDF").apply { mkdirs() }
        val file = File(dir, safeName)
        file.createNewFile()
        return FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
    }

    /**
     * Save bytes into Downloads/TinyPDF (MediaStore, no permission needed on
     * API 29+). Returns the content Uri of the saved file.
     */
    fun saveToDownloads(context: Context, fileName: String, write: (OutputStream) -> Unit): Uri {
        val uri = createDownloadEntry(context, fileName)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.contentResolver.openOutputStream(uri)?.use(write)
                ?: throw java.io.IOException("Could not write file.")
        } else {
            // FileProvider Uris are not writable via resolver; write directly.
            // (Legacy path only; modern devices use MediaStore above.)
            context.contentResolver.openOutputStream(uri)?.use(write)
                ?: throw java.io.IOException("Could not write file.")
        }
        return uri
    }

    fun sharePdf(context: Context, uri: Uri) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share PDF"))
    }

    // ------------------------------------------------------------------
    // Page thumbnails via the framework PdfRenderer (no PDFBox needed).
    // ------------------------------------------------------------------

    fun pdfPageCount(context: Context, uri: Uri): Int? {
        return try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                PdfRenderer(pfd).use { it.pageCount }
            }
        } catch (e: Exception) {
            null // encrypted or unreadable: caller shows page count from PDFBox instead
        }
    }

    fun renderThumbnail(context: Context, uri: Uri, pageIndex: Int, targetWidth: Int): Bitmap? {
        return try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    if (pageIndex !in 0 until renderer.pageCount) return null
                    renderer.openPage(pageIndex).use { page ->
                        val scale = targetWidth.toFloat() / page.width
                        val w = targetWidth
                        val h = (page.height * scale).toInt().coerceAtLeast(1)
                        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        bmp.eraseColor(0xFFFFFFFF.toInt())
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bmp
                    }
                }
            }
        } catch (e: Exception) {
            null
        }
    }
}

fun formatBytes(bytes: Long): String {
    if (bytes < 0) return "?"
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format("%.0f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format("%.1f MB", mb)
    return String.format("%.2f GB", mb / 1024.0)
}
