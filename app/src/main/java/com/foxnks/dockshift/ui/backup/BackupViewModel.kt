package com.foxnks.dockshift.ui.backup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.foxnks.dockshift.data.backup.BackupFormat
import com.foxnks.dockshift.data.backup.BackupFormatException
import com.foxnks.dockshift.data.backup.BackupStorage
import com.foxnks.dockshift.domain.backup.BackupCheck
import com.foxnks.dockshift.domain.backup.BackupContents
import com.foxnks.dockshift.domain.backup.BackupReminder
import com.foxnks.dockshift.domain.backup.BackupRepository
import com.foxnks.dockshift.domain.backup.BackupSummary
import com.foxnks.dockshift.domain.settings.SettingsRepository
import com.foxnks.dockshift.domain.shift.ShiftRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Why a backup or restore did not happen; each has its own message. */
enum class BackupFailure {
    /** The backup file could not be written. */
    SAVE,

    /** The chosen file could not be read. */
    READ,

    /** The chosen file is not a Dockshift backup, or is damaged. */
    INVALID,

    /** Writing the restored data failed; the old data stays as it was. */
    RESTORE,
}

/** The step a backup or restore is in; each step but [Idle] shows a dialog. */
sealed interface BackupStep {

    /** Nothing in progress. */
    data object Idle : BackupStep

    /** Reading, writing or restoring: a progress dialog that cannot be dismissed. */
    data object Working : BackupStep

    /**
     * The backup was written.
     *
     * @property uri the file's address, for sharing it.
     * @property inDocuments true when saved in `Documents/DataShift_foxnks`, false
     * when saved where the user chose.
     */
    data class Saved(val uri: String, val inDocuments: Boolean) : BackupStep

    /**
     * A valid backup was read: what it holds, before anything is replaced.
     *
     * @property summary its contents.
     */
    data class RestorePreview(val summary: BackupSummary) : BackupStep

    /**
     * The second, final confirmation: everything will be replaced.
     *
     * @property summary the backup's contents.
     */
    data class RestoreConfirm(val summary: BackupSummary) : BackupStep

    /**
     * The backup was restored.
     *
     * @property summary what was restored.
     */
    data class Restored(val summary: BackupSummary) : BackupStep

    /**
     * Something went wrong.
     *
     * @property failure what.
     */
    data class Failed(val failure: BackupFailure) : BackupStep
}

/**
 * What the Data & backup screen shows.
 *
 * @property lastBackup the day of the last backup, or null when none was made.
 * @property reminder whether the 15-day reminder is on.
 * @property storedShifts how many shifts are stored (shown before "delete all").
 * @property fileName the name offered for a new backup file.
 * @property step the dialog in progress.
 */
data class BackupUiState(
    val lastBackup: LocalDate? = null,
    val reminder: Boolean = true,
    val storedShifts: Int = 0,
    val fileName: String = "",
    val step: BackupStep = BackupStep.Idle,
)

/**
 * ViewModel of Menu → Data & backup: makes backups, restores one (replacing
 * everything, after two confirmations), the reminder switch and "delete all".
 *
 * @param backups reads and replaces the whole app.
 * @param format turns a backup into text and back.
 * @param storage writes and reads backup files.
 * @param settings the settings, for the last backup day and the reminder.
 * @param shifts stored shifts, for the count and "delete all".
 * @param clock the current time, for the backup's day.
 */
@HiltViewModel
class BackupViewModel @Inject constructor(
    private val backups: BackupRepository,
    private val format: BackupFormat,
    private val storage: BackupStorage,
    private val settings: SettingsRepository,
    private val shifts: ShiftRepository,
    private val clock: Clock,
) : ViewModel() {

    private val step = MutableStateFlow<BackupStep>(BackupStep.Idle)

    /** The backup read from a file, held between its preview and the final confirmation. */
    private var pending: BackupContents? = null

    /** The screen state. */
    val uiState: StateFlow<BackupUiState> = combine(settings.settings, shifts.observeCount(), step) { saved, count, current ->
        BackupUiState(
            lastBackup = saved.lastBackup,
            reminder = saved.backupReminder,
            storedShifts = count,
            fileName = BackupReminder.fileName(today()),
            step = current,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), BackupUiState())

    /** "Back up now": saves in `Documents/DataShift_foxnks`. */
    fun onBackupNow() {
        backup(inDocuments = true) { text -> storage.saveToDocuments(BackupReminder.fileName(today()), text) }
    }

    /**
     * "Save elsewhere": the user created a file with the system picker.
     *
     * @param uri the file's address, or null when the picker was cancelled.
     */
    fun onSaveTo(uri: String?) {
        if (uri == null) return
        backup(inDocuments = false) { text ->
            storage.write(uri, text)
            uri
        }
    }

    /**
     * "Restore": the user picked a file. It is read and checked; nothing is replaced yet.
     *
     * @param uri the file's address, or null when the picker was cancelled.
     */
    fun onRestoreChosen(uri: String?) {
        if (uri == null) return
        step.value = BackupStep.Working
        viewModelScope.launch {
            step.value = try {
                val contents = format.decode(storage.read(uri))
                if (BackupCheck.problemOf(contents) != null) {
                    BackupStep.Failed(BackupFailure.INVALID)
                } else {
                    pending = contents
                    BackupStep.RestorePreview(BackupSummary.of(contents))
                }
            } catch (e: BackupFormatException) {
                BackupStep.Failed(BackupFailure.INVALID)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Unreadable, gone, or no longer allowed (IOException, SecurityException...)
                BackupStep.Failed(BackupFailure.READ)
            }
        }
    }

    /** First confirmation given: ask the second, final one. */
    fun onRestoreContinue() {
        val contents = pending ?: return
        step.value = BackupStep.RestoreConfirm(BackupSummary.of(contents))
    }

    /** Second confirmation given: replace everything with the backup. */
    fun onRestoreConfirmed() {
        val contents = pending ?: return
        pending = null
        step.value = BackupStep.Working
        viewModelScope.launch {
            step.value = try {
                backups.restore(contents)
                BackupStep.Restored(BackupSummary.of(contents))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // The database rolls back: the old data is still there
                BackupStep.Failed(BackupFailure.RESTORE)
            }
        }
    }

    /** A dialog was closed: back to idle, forgetting any backup read for restoring. */
    fun onDismiss() {
        pending = null
        step.value = BackupStep.Idle
    }

    /** @param enabled new position of the reminder switch. */
    fun onReminderToggled(enabled: Boolean) {
        viewModelScope.launch { settings.setBackupReminder(enabled) }
    }

    /** Deletes every stored shift, after the screen's double confirmation. Machines and settings stay. */
    fun onDeleteAllShifts() {
        viewModelScope.launch { shifts.deleteAll() }
    }

    /**
     * Reads everything, writes it with [write] and remembers the day.
     *
     * @param inDocuments where it goes, for the message afterwards.
     * @param write writes the text and returns the file's address.
     */
    private fun backup(inDocuments: Boolean, write: suspend (String) -> String) {
        step.value = BackupStep.Working
        viewModelScope.launch {
            step.value = try {
                val uri = write(format.encode(backups.read()))
                settings.setLastBackup(today())
                BackupStep.Saved(uri, inDocuments)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                BackupStep.Failed(BackupFailure.SAVE)
            }
        }
    }

    private fun today(): LocalDate = LocalDate.now(clock)

    private companion object {
        /** Keeps the state alive across a rotation. */
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
