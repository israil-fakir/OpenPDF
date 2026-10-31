package com.example.pdf_reader.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

private const val PREFS_NAME = "pdf_reader_prefs"
private const val KEY_RECENT_PDFS = "recent_pdfs_list"
private const val MAX_RECENT_FILES = 20

/**
 * Data model representing a recently opened PDF file.
 */
data class RecentPdf(
    val uriString: String,
    val name: String,
    val lastOpened: Long,
    val page: Int = 0,
    val sizeBytes: Long = 0L,
    val thumbnailPath: String? = null,
) {
    fun toJson(): JSONObject {
        val json = JSONObject()
        json.put("uriString", uriString)
        json.put("name", name)
        json.put("lastOpened", lastOpened)
        json.put("page", page)
        json.put("sizeBytes", sizeBytes)
        if (thumbnailPath != null) {
            json.put("thumbnailPath", thumbnailPath)
        }
        return json
    }

    companion object {
        fun fromJson(json: JSONObject): RecentPdf {
            return RecentPdf(
                uriString = json.getString("uriString"),
                name = json.optString("name", "Document.pdf"),
                lastOpened = json.optLong("lastOpened", System.currentTimeMillis()),
                page = json.optInt("page", 0),
                sizeBytes = json.optLong("sizeBytes", 0L),
                thumbnailPath = if (json.has("thumbnailPath")) json.optString("thumbnailPath") else null,
            )
        }
    }
}

/**
 * SharedPreferences storage helper for managing the list of recent PDFs.
 */
object RecentPdfStore {

    fun getRecentPdfs(context: Context): List<RecentPdf> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonString = prefs.getString(KEY_RECENT_PDFS, null) ?: return emptyList()
        return try {
            val array = JSONArray(jsonString)
            val list = mutableListOf<RecentPdf>()
            for (i in 0 until array.length()) {
                list.add(RecentPdf.fromJson(array.getJSONObject(i)))
            }
            // Sort by newest first
            list.sortedByDescending(RecentPdf::lastOpened)
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun addOrUpdateRecentPdf(
        context: Context,
        uri: Uri,
        name: String,
        sizeBytes: Long,
        page: Int = 0,
    ) = withContext(Dispatchers.IO) {
        val currentList = getRecentPdfs(context).toMutableList()
        val uriStr = uri.toString()

        // Reuse existing thumbnail if available or generate a new one
        val existingIndex = currentList.indexOfFirst { it.uriString == uriStr }
        val existing = if (existingIndex != -1) currentList[existingIndex] else null
        val existingThumbnail = existing?.thumbnailPath

        val thumbnailPath = if (existingThumbnail != null && File(existingThumbnail).exists()) {
            existingThumbnail
        } else {
            generateThumbnail(context, uri)
        }

        // Remove duplicates of this file
        currentList.removeAll { it.uriString == uriStr }

        // Insert at the front (newest first)
        currentList.add(
            0,
            RecentPdf(
                uriString = uriStr,
                name = name,
                lastOpened = System.currentTimeMillis(),
                page = page,
                sizeBytes = if (sizeBytes > 0) sizeBytes else (existing?.sizeBytes ?: 0L),
                thumbnailPath = thumbnailPath,
            ),
        )

        // Limit list to max 20 recent files and clean up orphaned thumbnails
        val limitedList = currentList.take(MAX_RECENT_FILES)
        saveList(context, limitedList)
    }

    fun updateCurrentPage(context: Context, uri: Uri, page: Int) {
        val currentList = getRecentPdfs(context).toMutableList()
        val uriStr = uri.toString()
        val index = currentList.indexOfFirst { it.uriString == uriStr }
        if (index != -1) {
            val existing = currentList[index]
            currentList[index] = existing.copy(
                page = page,
                lastOpened = System.currentTimeMillis(),
            )
            // Re-sort newest first and keep up to 20
            currentList.sortByDescending(RecentPdf::lastOpened)
            saveList(context, currentList.take(MAX_RECENT_FILES))
        }
    }

    fun removeRecentPdf(context: Context, uriString: String) {
        val currentList = getRecentPdfs(context).toMutableList()
        val removed = currentList.firstOrNull { it.uriString == uriString }
        removed?.thumbnailPath?.let { path ->
            try {
                File(path).delete()
            } catch (_: Exception) {
            }
        }
        currentList.removeAll { it.uriString == uriString }
        saveList(context, currentList)
    }

    fun clearAllRecentPdfs(context: Context) {
        // Delete cached thumbnail files
        val thumbDir = File(context.cacheDir, "pdf_thumbs")
        if (thumbDir.exists()) {
            try {
                thumbDir.deleteRecursively()
            } catch (_: Exception) {
            }
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            remove(KEY_RECENT_PDFS)
        }
    }

    private fun saveList(context: Context, list: List<RecentPdf>) {
        val array = JSONArray()
        for (item in list) {
            array.put(item.toJson())
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            putString(KEY_RECENT_PDFS, array.toString())
        }
    }

    /**
     * Generates a compressed JPEG thumbnail of page 0 of the PDF using Android's native [PdfRenderer].
     */
    private fun generateThumbnail(context: Context, uri: Uri): String? {
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        var page: PdfRenderer.Page? = null
        return try {
            pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: return null
            renderer = PdfRenderer(pfd)
            if (renderer.pageCount <= 0) return null

            page = renderer.openPage(0)

            // Render scaled thumbnail (e.g. width ~300px preserving aspect ratio)
            val targetWidth = 300
            val targetHeight = (targetWidth * (page.height.toFloat() / page.width.toFloat())).toInt().coerceIn(100, 600)
            val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)

            // Fill white background before rendering page content
            bitmap.eraseColor(android.graphics.Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

            // Save to app internal cache
            val thumbDir = File(context.cacheDir, "pdf_thumbs").apply { mkdirs() }
            val hash = MessageDigest.getInstance("MD5").digest(uri.toString().toByteArray())
                .joinToString("") { "%02x".format(it) }
            val thumbFile = File(thumbDir, "thumb_$hash.jpg")

            FileOutputStream(thumbFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }
            bitmap.recycle()
            thumbFile.absolutePath
        } catch (_: Exception) {
            null
        } finally {
            try {
                page?.close()
                renderer?.close()
                pfd?.close()
            } catch (_: Exception) {
            }
        }
    }
}
