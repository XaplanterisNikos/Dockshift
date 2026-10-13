package com.foxnks.dockshift.data.backup

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import com.foxnks.dockshift.data.legacy.TextFileReader
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * [BackupStorage] through MediaStore and the ContentResolver: no storage permission
 * is needed (Android 10+ lets an app add files to `Documents` and change its own).
 *
 * Files in `Documents` survive uninstalling the app, unlike the app's private
 * storage, so the backup is there when the app is installed again or the files
 * are copied to a new phone.
 *
 * @param context the application context, injected by Hilt.
 * @param reader reads picked files (shared with the v1 import).
 */
class AndroidBackupStorage @Inject constructor(
    @ApplicationContext private val context: Context,
    private val reader: TextFileReader,
) : BackupStorage {

    override suspend fun saveToDocuments(fileName: String, text: String): String = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val folder = "${Environment.DIRECTORY_DOCUMENTS}/${BackupStorage.FOLDER}/"

        // This app's own file of the same day, if any (other apps' files are not visible)
        val existing = resolver.query(
            collection,
            arrayOf(MediaStore.MediaColumns._ID),
            "${MediaStore.MediaColumns.RELATIVE_PATH} = ? AND ${MediaStore.MediaColumns.DISPLAY_NAME} = ?",
            arrayOf(folder, fileName),
            null,
        )?.use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) else null }

        val uri = if (existing != null) {
            ContentUris.withAppendedId(collection, existing)
        } else {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, MIME_TYPE)
                // The folder is created by the system when missing
                put(MediaStore.MediaColumns.RELATIVE_PATH, folder)
                // Hidden from other apps until fully written
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            resolver.insert(collection, values) ?: throw IOException("Cannot create $folder$fileName")
        }

        try {
            writeText(uri, text)
        } catch (e: IOException) {
            // A half-written new file is removed rather than left behind
            if (existing == null) resolver.delete(uri, null, null)
            throw e
        }
        if (existing == null) {
            resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        }
        uri.toString()
    }

    override suspend fun write(uri: String, text: String) = withContext(Dispatchers.IO) {
        writeText(Uri.parse(uri), text)
    }

    override suspend fun read(uri: String): String = reader.read(uri)

    /** Replaces the file's contents ("wt": write, truncate) with the UTF-8 text. */
    private fun writeText(uri: Uri, text: String) {
        val output = context.contentResolver.openOutputStream(uri, "wt") ?: throw IOException("Cannot open $uri")
        output.use { it.write(text.toByteArray(Charsets.UTF_8)) }
    }

    companion object {
        /** The type of backup files. */
        const val MIME_TYPE = "application/json"
    }
}
