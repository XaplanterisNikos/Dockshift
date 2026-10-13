package com.foxnks.dockshift.testing

import com.foxnks.dockshift.data.backup.BackupFormat
import com.foxnks.dockshift.data.backup.BackupFormatException
import com.foxnks.dockshift.data.backup.BackupStorage
import com.foxnks.dockshift.domain.backup.BackupContents
import com.foxnks.dockshift.domain.backup.BackupRepository
import java.io.IOException

/**
 * [BackupRepository] for tests: [read] returns [contents]; [restore] records what it got.
 *
 * @property contents what a backup holds.
 */
class FakeBackupRepository(var contents: BackupContents) : BackupRepository {

    /** The backup last restored, or null. */
    var restored: BackupContents? = null

    /** When true, [restore] fails like a database error. */
    var failRestore = false

    override suspend fun read(): BackupContents = contents

    override suspend fun restore(contents: BackupContents) {
        if (failRestore) throw IllegalStateException("database error")
        restored = contents
    }
}

/**
 * [BackupFormat] for tests, no JSON: a backup is kept in a map under a made-up text.
 * Any other text is not a backup.
 */
class FakeBackupFormat : BackupFormat {

    private val written = mutableMapOf<String, BackupContents>()

    override fun encode(contents: BackupContents): String {
        val text = "backup-${written.size}"
        written[text] = contents
        return text
    }

    override fun decode(text: String): BackupContents = written[text] ?: throw BackupFormatException("Not a backup")
}

/** [BackupStorage] for tests: files are entries of [files]. */
class FakeBackupStorage : BackupStorage {

    /** File address → text. Documents files are under "documents/<name>". */
    val files = mutableMapOf<String, String>()

    /** When true, writing fails like a full disk. */
    var failWrite = false

    override suspend fun saveToDocuments(fileName: String, text: String): String {
        val uri = "documents/$fileName"
        write(uri, text)
        return uri
    }

    override suspend fun write(uri: String, text: String) {
        if (failWrite) throw IOException("disk full")
        files[uri] = text
    }

    override suspend fun read(uri: String): String = files[uri] ?: throw IOException("no file $uri")
}
