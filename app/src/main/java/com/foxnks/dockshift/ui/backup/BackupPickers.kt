package com.foxnks.dockshift.ui.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.result.contract.ActivityResultContracts
import com.foxnks.dockshift.data.backup.AndroidBackupStorage
import com.foxnks.dockshift.data.backup.BackupStorage

/**
 * The system "save as" picker for a backup, opened in `Documents/DataShift_foxnks`.
 * From there the user can go to any folder, an SD card or Google Drive.
 */
class CreateBackupFile : ActivityResultContracts.CreateDocument(AndroidBackupStorage.MIME_TYPE) {

    override fun createIntent(context: Context, input: String): Intent =
        // A hint, not a requirement: a missing folder just opens the picker at its usual place
        super.createIntent(context, input).putExtra(DocumentsContract.EXTRA_INITIAL_URI, BACKUP_FOLDER)
}

/** The system file picker for restoring, opened in `Documents/DataShift_foxnks`. */
class OpenBackupFile : ActivityResultContracts.OpenDocument() {

    override fun createIntent(context: Context, input: Array<String>): Intent =
        super.createIntent(context, input).putExtra(DocumentsContract.EXTRA_INITIAL_URI, BACKUP_FOLDER)

    companion object {
        /**
         * Any file type: Drive and file managers label .json files inconsistently, and a
         * filter could hide the user's backup. Other files are refused after reading.
         */
        val MIME_TYPES = arrayOf("*/*")
    }
}

/** Document address of `Documents/DataShift_foxnks` on the primary storage. */
private val BACKUP_FOLDER: Uri = DocumentsContract.buildDocumentUri(
    "com.android.externalstorage.documents",
    "primary:Documents/${BackupStorage.FOLDER}",
)

/**
 * The share sheet for a backup file: e-mail, Viber, Drive, Bluetooth...
 *
 * @param uri the file's `content://` address.
 * @param title the share sheet's title.
 */
fun shareBackupIntent(uri: String, title: String): Intent {
    val send = Intent(Intent.ACTION_SEND)
        .setType(AndroidBackupStorage.MIME_TYPE)
        .putExtra(Intent.EXTRA_STREAM, Uri.parse(uri))
        // The receiving app may read this one file, nothing else
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    return Intent.createChooser(send, title)
}
