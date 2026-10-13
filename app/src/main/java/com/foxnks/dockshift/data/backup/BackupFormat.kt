package com.foxnks.dockshift.data.backup

import com.foxnks.dockshift.domain.backup.BackupContents

/**
 * Turns a backup into text and back. An interface, so screens can be tested without
 * a JSON library.
 */
interface BackupFormat {

    /**
     * @param contents the backup.
     * @return the text of the backup file.
     */
    fun encode(contents: BackupContents): String

    /**
     * @param text the text of a backup file.
     * @return the backup.
     * @throws BackupFormatException when the text is not a Dockshift backup this
     * version can read.
     */
    fun decode(text: String): BackupContents
}

/**
 * A file that cannot be restored, with the reason in English (for logs and support).
 *
 * @param reason what is wrong.
 * @param cause the underlying error, if any.
 */
class BackupFormatException(reason: String, cause: Throwable? = null) : Exception(reason, cause)
