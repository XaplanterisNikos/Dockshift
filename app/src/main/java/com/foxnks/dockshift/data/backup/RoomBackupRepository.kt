package com.foxnks.dockshift.data.backup

import android.content.Context
import com.foxnks.dockshift.data.db.dao.BackupDao
import com.foxnks.dockshift.data.db.toAssignmentEntities
import com.foxnks.dockshift.data.db.toDomain
import com.foxnks.dockshift.data.db.toEntity
import com.foxnks.dockshift.data.faults.toDomain
import com.foxnks.dockshift.data.faults.toEntity
import com.foxnks.dockshift.data.leave.toDomain
import com.foxnks.dockshift.data.leave.toEntity
import com.foxnks.dockshift.domain.backup.BackupContents
import com.foxnks.dockshift.domain.backup.BackupRepository
import com.foxnks.dockshift.domain.settings.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import java.time.LocalDateTime
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/**
 * [BackupRepository] over the Room database and the settings store.
 *
 * @param context the application context, for the app's version name.
 * @param dao backup data access.
 * @param settings the settings store.
 * @param clock the current time, for the backup's date.
 */
class RoomBackupRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: BackupDao,
    private val settings: SettingsRepository,
    private val clock: Clock,
) : BackupRepository {

    override suspend fun read(): BackupContents = BackupContents(
        createdAt = LocalDateTime.now(clock),
        appVersion = appVersion(),
        shifts = dao.allShifts().map { it.toDomain() },
        machines = dao.allMachines().map { it.toDomain() },
        leaves = dao.allLeaves().map { it.toDomain() },
        settings = settings.settings.first(),
        faults = dao.allFaults().mapNotNull { it.toDomain() },
    )

    override suspend fun restore(contents: BackupContents) {
        dao.replaceAll(
            machines = contents.machines.map { it.toEntity() },
            shifts = contents.shifts.map { it.toEntity() },
            assignments = contents.shifts.flatMap { it.toAssignmentEntities() },
            // id 0: the database gives new identities
            leaves = contents.leaves.map { it.copy(id = 0).toEntity() },
            faults = contents.faults.map { it.copy(id = 0).toEntity() },
        )
        // A backup older than the profile has no id: keep this phone's, never lose one
        val currentId = settings.settings.first().worker.id
        val restored = contents.settings.let { saved ->
            if (saved.worker.id.isBlank()) saved.copy(worker = saved.worker.copy(id = currentId)) else saved
        }
        // The restored data is exactly that backup: it counts as the last backup
        settings.restore(restored.copy(lastBackup = contents.createdAt.toLocalDate()))
    }

    /** "2.0.0-alpha01", or empty if the system cannot tell. */
    private fun appVersion(): String =
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
}
