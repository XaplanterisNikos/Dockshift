package com.foxnks.dockshift.ui.legacy

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.result.contract.ActivityResultContracts

/**
 * The system file picker, opened directly inside the folder where Dockshift v1
 * kept its backup (`DataShift_foxnks` at the root of the phone's storage).
 *
 * The picker needs no storage permission. If the folder does not exist, the
 * picker simply opens at its usual place and the user browses.
 */
class OpenLegacyBackup : ActivityResultContracts.OpenDocument() {

    override fun createIntent(context: Context, input: Array<String>): Intent =
        super.createIntent(context, input)
            // A hint, not a requirement: honoured by the system picker since Android 8
            .putExtra(DocumentsContract.EXTRA_INITIAL_URI, LEGACY_FOLDER)

    companion object {
        /**
         * Any file type: phones label .csv files inconsistently (text/csv,
         * text/comma-separated-values, application/octet-stream...), and a filter
         * could hide the user's file.
         */
        val MIME_TYPES = arrayOf("*/*")

        /** Document address of `/DataShift_foxnks` on the primary storage. */
        private val LEGACY_FOLDER: Uri = DocumentsContract.buildDocumentUri(
            "com.android.externalstorage.documents",
            "primary:DataShift_foxnks",
        )
    }
}
