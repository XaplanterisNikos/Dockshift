package com.foxnks.dockshift.ui.backup

import com.foxnks.dockshift.domain.backup.BackupContents
import com.foxnks.dockshift.domain.backup.BackupSummary
import com.foxnks.dockshift.domain.machine.Machine
import com.foxnks.dockshift.domain.machine.MachineCategory
import com.foxnks.dockshift.domain.settings.AppSettings
import com.foxnks.dockshift.domain.shift.MachineAssignment
import com.foxnks.dockshift.domain.shift.Shift
import com.foxnks.dockshift.domain.shift.ShiftType
import com.foxnks.dockshift.testing.FakeBackupFormat
import com.foxnks.dockshift.testing.FakeBackupRepository
import com.foxnks.dockshift.testing.FakeBackupStorage
import com.foxnks.dockshift.testing.FakeSettingsRepository
import com.foxnks.dockshift.testing.InMemoryShiftRepository
import com.foxnks.dockshift.testing.MainDispatcherRule
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** [BackupViewModel] with fakes for the database, the file format and the files. */
class BackupViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val today = LocalDate.of(2026, 10, 14)

    /** The clock says 14 October 2026. */
    private val clock = Clock.fixed(today.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC)

    private val rtg = Machine(7, MachineCategory.RTG, "12")

    /** A small but complete backup. */
    private val contents = BackupContents(
        createdAt = LocalDateTime.of(2026, 10, 1, 9, 30),
        appVersion = "2.0.0",
        shifts = listOf(
            Shift(LocalDate.of(2026, 9, 30), ShiftType.MORNING, machines = listOf(MachineAssignment(rtg))),
            Shift(LocalDate.of(2026, 10, 1), ShiftType.DAY_OFF),
        ),
        machines = listOf(rtg),
        leaves = emptyList(),
        settings = AppSettings(),
    )

    private val repository = FakeBackupRepository(contents)
    private val format = FakeBackupFormat()
    private val storage = FakeBackupStorage()
    private val settings = FakeSettingsRepository()
    private val shifts = InMemoryShiftRepository(contents.shifts)

    private fun viewModel() = BackupViewModel(repository, format, storage, settings, shifts, clock)

    /** Collects the state like the screen does, so WhileSubscribed runs. */
    private fun TestScope.observe(viewModel: BackupViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
    }

    private val BackupViewModel.step get() = uiState.value.step

    @Test
    fun `shows the stored count, the reminder and today's file name`() = runTest {
        val viewModel = viewModel()
        observe(viewModel)

        assertEquals(2, viewModel.uiState.value.storedShifts)
        assertTrue(viewModel.uiState.value.reminder)
        assertNull(viewModel.uiState.value.lastBackup)
        assertEquals("dockshift-backup-2026-10-14.json", viewModel.uiState.value.fileName)
    }

    @Test
    fun `back up now saves in Documents and remembers the day`() = runTest {
        val viewModel = viewModel()
        observe(viewModel)

        viewModel.onBackupNow()

        val uri = "documents/dockshift-backup-2026-10-14.json"
        assertEquals(BackupStep.Saved(uri, inDocuments = true), viewModel.step)
        assertEquals(contents, format.decode(storage.files.getValue(uri)))
        assertEquals(today, settings.current.lastBackup)
        assertEquals(today, viewModel.uiState.value.lastBackup)
    }

    @Test
    fun `save elsewhere writes the picked file`() = runTest {
        val viewModel = viewModel()
        observe(viewModel)

        viewModel.onSaveTo("content://drive/file")

        assertEquals(BackupStep.Saved("content://drive/file", inDocuments = false), viewModel.step)
        assertTrue("content://drive/file" in storage.files)
        assertEquals(today, settings.current.lastBackup)
    }

    @Test
    fun `cancelled picker does nothing`() = runTest {
        val viewModel = viewModel()
        observe(viewModel)

        viewModel.onSaveTo(null)
        viewModel.onRestoreChosen(null)

        assertEquals(BackupStep.Idle, viewModel.step)
        assertTrue(storage.files.isEmpty())
    }

    @Test
    fun `a failed write is reported and the day is not remembered`() = runTest {
        storage.failWrite = true
        val viewModel = viewModel()
        observe(viewModel)

        viewModel.onBackupNow()

        assertEquals(BackupStep.Failed(BackupFailure.SAVE), viewModel.step)
        assertNull(settings.current.lastBackup)
    }

    @Test
    fun `restore asks twice, then replaces everything`() = runTest {
        storage.files["file"] = format.encode(contents)
        val viewModel = viewModel()
        observe(viewModel)
        val summary = BackupSummary.of(contents)

        viewModel.onRestoreChosen("file")
        assertEquals(BackupStep.RestorePreview(summary), viewModel.step)
        assertNull(repository.restored)

        viewModel.onRestoreContinue()
        assertEquals(BackupStep.RestoreConfirm(summary), viewModel.step)
        assertNull(repository.restored)

        viewModel.onRestoreConfirmed()
        assertEquals(BackupStep.Restored(summary), viewModel.step)
        assertEquals(contents, repository.restored)
    }

    @Test
    fun `dismissing the preview restores nothing`() = runTest {
        storage.files["file"] = format.encode(contents)
        val viewModel = viewModel()
        observe(viewModel)

        viewModel.onRestoreChosen("file")
        viewModel.onDismiss()
        viewModel.onRestoreContinue()
        viewModel.onRestoreConfirmed()

        assertEquals(BackupStep.Idle, viewModel.step)
        assertNull(repository.restored)
    }

    @Test
    fun `unreadable, foreign and broken files are refused`() = runTest {
        val viewModel = viewModel()
        observe(viewModel)

        viewModel.onRestoreChosen("missing")
        assertEquals(BackupStep.Failed(BackupFailure.READ), viewModel.step)

        storage.files["photo"] = "not a backup"
        viewModel.onRestoreChosen("photo")
        assertEquals(BackupStep.Failed(BackupFailure.INVALID), viewModel.step)

        // A shift refers to a machine the file lacks
        storage.files["broken"] = format.encode(contents.copy(machines = emptyList()))
        viewModel.onRestoreChosen("broken")
        assertEquals(BackupStep.Failed(BackupFailure.INVALID), viewModel.step)
        assertNull(repository.restored)
    }

    @Test
    fun `a failed restore is reported`() = runTest {
        storage.files["file"] = format.encode(contents)
        repository.failRestore = true
        val viewModel = viewModel()
        observe(viewModel)

        viewModel.onRestoreChosen("file")
        viewModel.onRestoreContinue()
        viewModel.onRestoreConfirmed()

        assertEquals(BackupStep.Failed(BackupFailure.RESTORE), viewModel.step)
    }

    @Test
    fun `reminder switch is saved`() = runTest {
        val viewModel = viewModel()
        observe(viewModel)

        viewModel.onReminderToggled(false)

        assertFalse(settings.current.backupReminder)
        assertFalse(viewModel.uiState.value.reminder)
    }

    @Test
    fun `delete all removes every shift and the count follows`() = runTest {
        val viewModel = viewModel()
        observe(viewModel)

        viewModel.onDeleteAllShifts()

        assertTrue(shifts.stored.isEmpty())
        assertEquals(0, viewModel.uiState.value.storedShifts)
    }
}
