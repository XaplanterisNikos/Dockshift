package com.foxnks.dockshift.data.backup

import java.io.IOException

/** Where backup files are written and read. An interface, so screens can be tested with a fake. */
interface BackupStorage {

    /**
     * Saves a backup in the phone's `Documents/DataShift_foxnks` folder (created when
     * missing). A backup of the same day made earlier by this app is replaced, so one
     * day leaves one file.
     *
     * @param fileName the file name, e.g. "dockshift-backup-2026-10-14.json".
     * @param text the backup.
     * @return the file's `content://` address, for sharing it.
     * @throws IOException when the file cannot be written.
     */
    suspend fun saveToDocuments(fileName: String, text: String): String

    /**
     * Writes a backup to a file the user created with the system picker (any
     * folder, Google Drive...).
     *
     * @param uri the `content://` address the picker returned.
     * @param text the backup.
     * @throws IOException when the file cannot be written.
     */
    suspend fun write(uri: String, text: String)

    /**
     * @param uri the `content://` address of a file the user picked.
     * @return its text.
     * @throws IOException when the file cannot be read.
     */
    suspend fun read(uri: String): String

    companion object {
        /** The folder of the backups inside `Documents`; the name v1 used for its own. */
        const val FOLDER = "DataShift_foxnks"
    }
}
