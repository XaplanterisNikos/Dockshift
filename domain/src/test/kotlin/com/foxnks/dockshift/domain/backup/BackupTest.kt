package com.foxnks.dockshift.domain.backup

import com.foxnks.dockshift.domain.faults.FaultType
import com.foxnks.dockshift.domain.faults.MachineFault
import com.foxnks.dockshift.domain.leave.Leave
import com.foxnks.dockshift.domain.machine.Machine
import com.foxnks.dockshift.domain.machine.MachineCategory
import com.foxnks.dockshift.domain.settings.AppSettings
import com.foxnks.dockshift.domain.shift.MachineAssignment
import com.foxnks.dockshift.domain.shift.Shift
import com.foxnks.dockshift.domain.shift.ShiftType
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** Backup summary, soundness check and reminder. */
class BackupTest {

    private val today = LocalDate.of(2026, 10, 14)
    private val rtg = Machine(1, MachineCategory.RTG, "12")
    private val qc = Machine(2, MachineCategory.QC, "31")

    private fun contents(
        shifts: List<Shift> = listOf(
            Shift(LocalDate.of(2022, 6, 1), ShiftType.MORNING, machines = listOf(MachineAssignment(rtg))),
            Shift(LocalDate.of(2026, 10, 13), ShiftType.NIGHT, machines = listOf(MachineAssignment(qc))),
        ),
        machines: List<Machine> = listOf(rtg, qc),
    ) = BackupContents(
        createdAt = LocalDateTime.of(2026, 10, 14, 0, 10),
        appVersion = "2.0.0",
        shifts = shifts,
        machines = machines,
        leaves = listOf(Leave(1, LocalDate.of(2026, 12, 21), LocalDate.of(2026, 12, 26))),
        settings = AppSettings(),
    )

    @Test
    fun `summary of a backup`() {
        val summary = BackupSummary.of(contents())
        assertEquals(2, summary.shifts)
        assertEquals(LocalDate.of(2022, 6, 1), summary.firstShift)
        assertEquals(LocalDate.of(2026, 10, 13), summary.lastShift)
        assertEquals(1, summary.leaves)
        assertEquals(2, summary.machines)
    }

    @Test
    fun `a sound backup passes the check`() {
        assertNull(BackupCheck.problemOf(contents()))
    }

    @Test
    fun `a shift using a machine missing from the backup is refused`() {
        assertNotNull(BackupCheck.problemOf(contents(machines = listOf(rtg))))
    }

    @Test
    fun `machines sharing an identity are refused`() {
        assertNotNull(BackupCheck.problemOf(contents(machines = listOf(rtg, qc.copy(id = 1)))))
    }

    @Test
    fun `a fault on a machine missing from the backup is refused`() {
        val fault = MachineFault(
            0, 99, FaultType.LIGHTS, LocalDate.of(2026, 10, 1),
        )
        assertNotNull(BackupCheck.problemOf(contents().copy(faults = listOf(fault))))
        assertNull(BackupCheck.problemOf(contents().copy(faults = listOf(fault.copy(machineId = rtg.id)))))
    }

    @Test
    fun `two shifts on one day are refused`() {
        val day = LocalDate.of(2026, 1, 1)
        assertNotNull(BackupCheck.problemOf(contents(shifts = listOf(Shift(day, ShiftType.MORNING), Shift(day, ShiftType.NIGHT)))))
    }

    @Test
    fun `the reminder is due every 15 days`() {
        assertNull(BackupReminder.dueDays(true, today.minusDays(14), 10, today))
        assertEquals(15, BackupReminder.dueDays(true, today.minusDays(15), 10, today))
        assertEquals(40, BackupReminder.dueDays(true, today.minusDays(40), 10, today))
    }

    @Test
    fun `without any backup the reminder is due at once`() {
        assertEquals(Int.MAX_VALUE, BackupReminder.dueDays(true, null, 10, today))
    }

    @Test
    fun `no reminder when turned off or with nothing to lose`() {
        assertNull(BackupReminder.dueDays(false, null, 10, today))
        assertNull(BackupReminder.dueDays(true, null, 0, today))
    }

    @Test
    fun `file name carries the day`() {
        assertEquals("dockshift-backup-2026-10-14.json", BackupReminder.fileName(today))
    }
}
