package com.example.util

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AutoBackupStorage {
    fun saveToPhone(context: Context, bytes: ByteArray): String {
        val stamp = SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.US).format(Date())
        val fileName = "Tahalil_Auto_Backup_${stamp}.tahbak"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Tahalil Alakkad/Backups")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: error("تعذر إنشاء ملف النسخة على الهاتف")
            try {
                resolver.openOutputStream(uri, "w")?.use { it.write(bytes) }
                    ?: error("تعذر فتح ملف النسخة للكتابة")
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            } catch (t: Throwable) {
                runCatching { resolver.delete(uri, null, null) }
                throw t
            }
            return "Download/Tahalil Alakkad/Backups/$fileName"
        }

        @Suppress("DEPRECATION")
        val root = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val dir = File(root, "Tahalil Alakkad/Backups").apply { mkdirs() }
        val target = File(dir, fileName)
        target.writeBytes(bytes)
        return target.absolutePath
    }

    /** V142: newest .tahbak visible anywhere in Downloads. */
    fun latestBackupEpochMillis(context: Context): Long? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val projection = arrayOf(MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.DATE_ADDED)
            val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?"
            val args = arrayOf("%.tahbak")
            resolver.query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI, projection, selection, args,
                "${MediaStore.MediaColumns.DATE_ADDED} DESC"
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val sec = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED))
                    return sec * 1000L
                }
            }
            return null
        }
        @Suppress("DEPRECATION")
        val root = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        return root.walkTopDown()
            .filter { it.isFile && it.extension.equals("tahbak", ignoreCase = true) }
            .maxByOrNull { it.lastModified() }?.lastModified()
    }

    fun latestBackupName(context: Context): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val projection = arrayOf(MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.DATE_ADDED)
            val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?"
            val args = arrayOf("%.tahbak")
            resolver.query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI, projection, selection, args,
                "${MediaStore.MediaColumns.DATE_ADDED} DESC"
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    return cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME))
                }
            }
            return null
        }
        @Suppress("DEPRECATION")
        val root = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        return root.walkTopDown()
            .filter { it.isFile && it.extension.equals("tahbak", ignoreCase = true) }
            .maxByOrNull { it.lastModified() }?.name
    }

}
