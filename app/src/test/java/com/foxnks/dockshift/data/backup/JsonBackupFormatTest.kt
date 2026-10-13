package com.foxnks.dockshift.data.backup

import com.foxnks.dockshift.domain.backup.BackupContents
import com.foxnks.dockshift.domain.holidays.GreekHoliday
import com.foxnks.dockshift.domain.leave.Leave
import com.foxnks.dockshift.domain.machine.Machine
import com.foxnks.dockshift.domain.machine.MachineCategory
import com.foxnks.dockshift.domain.rotation.RotationAnchor
import com.foxnks.dockshift.domain.settings.AppSettings
import com.foxnks.dockshift.domain.settings.CalendarView
import com.foxnks.dockshift.domain.settings.ThemeMode
import com.foxnks.dockshift.domain.settings.WorkSchedule
import com.foxnks.dockshift.domain.shift.MachineAssignment
import com.foxnks.dockshift.domain.shift.Shift
import com.foxnks.dockshift.domain.shift.ShiftType
import com.foxnks.dockshift.domain.worker.WorkerProfile
import com.foxnks.dockshift.domain.worker.WorkerRole
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** [JsonBackupFormat]: what is written is read back exactly; foreign files are refused. */
class JsonBackupFormatTest {

    private val format = JsonBackupFormat()

    private val rtg = Machine(3, MachineCategory.RTG, "12", location = "Πλατεία 2")
    private val qc = Machine(9, MachineCategory.QC, "4", active = false)

    /** Everything a backup can hold, with values different from the defaults. */
    private val contents = BackupContents(
        createdAt = LocalDateTime.of(2026, 10, 14, 18, 5, 30),
        appVersion = "2.0.0-alpha01",
        shifts = listOf(
            Shift(
                date = LocalDate.of(2026, 10, 13),
                type = ShiftType.MORNING,
                overtime = true,
                machines = listOf(MachineAssignment(rtg), MachineAssignment(qc, duringOvertime = true)),
                comments = "Βλάβη στο σπρέντερ",
            ),
            Shift(LocalDate.of(2026, 10, 14), ShiftType.DAY_OFF),
        ),
        machines = listOf(rtg, qc),
        leaves = listOf(Leave(0, LocalDate.of(2026, 12, 21), LocalDate.of(2026, 12, 31), "Χριστούγεννα")),
        settings = AppSettings(
            workSchedule = WorkSchedule.MORNING_AFTERNOON,
            rotationAnchor = RotationAnchor(LocalDate.of(2024, 8, 10), ShiftType.AFTERNOON),
            rotationPromptAnswered = true,
            legacyImportAsked = true,
            themeMode = ThemeMode.DARK,
            dynamicColor = false,
            calendarViewPortrait = CalendarView.entries.last(),
            calendarViewLandscape = CalendarView.entries.first(),
            showHolidays = false,
            leaveEntitlements = mapOf(2026 to 24, 2027 to 25),
            portClosedHolidays = setOf(GreekHoliday.entries.first()),
            backupReminder = false,
            worker = WorkerProfile(
                id = "3f2b8c1e-0d4a-4b6e-9a1f-2c3d4e5f6a7b",
                name = "Νίκος",
                email = "foxnks@gmail.com",
                role = WorkerRole.OPERATOR,
                categories = setOf(MachineCategory.RTG, MachineCategory.QC),
            ),
            workerPromptAsked = true,
        ),
    )

    @Test
    fun `round trip keeps everything`() {
        assertEquals(contents, format.decode(format.encode(contents)))
    }

    @Test
    fun `the file says what it is and is readable`() {
        val text = format.encode(contents)
        assertTrue(text.contains("\"format\": \"dockshift-backup\""))
        assertTrue(text.contains("Βλάβη στο σπρέντερ"))
    }

    @Test
    fun `the restored phone is not asked about v1`() {
        val fresh = contents.copy(settings = AppSettings())
        assertTrue(format.decode(format.encode(fresh)).settings.legacyImportAsked)
    }

    @Test
    fun `the restored phone is not asked for the profile`() {
        val fresh = contents.copy(settings = AppSettings())
        assertTrue(format.decode(format.encode(fresh)).settings.workerPromptAsked)
    }

    @Test
    fun `missing settings get the defaults`() {
        val text = """{"format":"dockshift-backup","version":1,"createdAt":"2026-10-14T10:00"}"""
        val decoded = format.decode(text)
        assertTrue(decoded.shifts.isEmpty())
        assertEquals(AppSettings().themeMode, decoded.settings.themeMode)
        assertTrue(decoded.settings.backupReminder)
        assertEquals(WorkerProfile(), decoded.settings.worker)
    }

    @Test
    fun `unknown fields of a newer app are skipped`() {
        val text = """{"format":"dockshift-backup","version":1,"createdAt":"2026-10-14T10:00","future":{"name":"Νίκος"}}"""
        assertTrue(format.decode(text).machines.isEmpty())
    }

    @Test
    fun `foreign, newer and damaged files are refused`() {
        listOf(
            "not json at all",
            """{"format":"something-else","version":1,"createdAt":"2026-10-14T10:00"}""",
            """{"format":"dockshift-backup","version":99,"createdAt":"2026-10-14T10:00"}""",
            """{"format":"dockshift-backup","version":1,"createdAt":"yesterday"}""",
            // A shift with a machine missing from the file
            """{"format":"dockshift-backup","version":1,"createdAt":"2026-10-14T10:00",
               "shifts":[{"date":"2026-10-13","type":"MORNING","machines":[{"machineId":5}]}]}""",
        ).forEach { text ->
            val refused = runCatching { format.decode(text) }.exceptionOrNull()
            assertTrue("Should refuse: $text", refused is BackupFormatException)
        }
        assertFalse(runCatching { format.decode(format.encode(contents)) }.isFailure)
    }
}
