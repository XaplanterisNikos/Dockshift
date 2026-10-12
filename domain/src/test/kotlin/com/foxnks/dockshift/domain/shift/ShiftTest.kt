package com.foxnks.dockshift.domain.shift

import com.foxnks.dockshift.domain.machine.Machine
import com.foxnks.dockshift.domain.machine.MachineCategory
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** Rules of [Shift]: what may be combined, and how it counts in statistics. */
class ShiftTest {

    private val day = LocalDate.of(2026, 10, 13)
    private val rtg12 = Machine(id = 1, category = MachineCategory.RTG, number = "12")
    private val qc31 = Machine(id = 2, category = MachineCategory.QC, number = "31")

    @Test
    fun `regular working shift counts as one shift of 8 hours`() {
        val shift = Shift(day, ShiftType.MORNING, machines = listOf(MachineAssignment(rtg12)))

        assertEquals(8, shift.workedHours)
        assertEquals(1.0, shift.shiftUnits, 0.0)
    }

    @Test
    fun `overtime counts as one and a half shifts of 12 hours`() {
        val shift = Shift(
            date = day,
            type = ShiftType.NIGHT,
            overtime = true,
            machines = listOf(
                MachineAssignment(rtg12),
                MachineAssignment(qc31, duringOvertime = true),
            ),
        )

        assertEquals(12, shift.workedHours)
        assertEquals(1.5, shift.shiftUnits, 0.0)
    }

    @Test
    fun `days not worked count as zero`() {
        // Every non-working type, so a new one added later is covered automatically
        ShiftType.entries.filterNot { it.isWorkShift }.forEach { type ->
            val shift = Shift(day, type)
            assertEquals("$type hours", 0, shift.workedHours)
            assertEquals("$type units", 0.0, shift.shiftUnits, 0.0)
        }
    }

    @Test
    fun `machine order is kept as given`() {
        val machines = listOf(MachineAssignment(qc31), MachineAssignment(rtg12), MachineAssignment(qc31))
        val shift = Shift(day, ShiftType.AFTERNOON, machines = machines)

        // The operator went back to QC 31: repeats are allowed and order matters
        assertEquals(machines, shift.machines)
    }

    @Test
    fun `non working day cannot have machines`() {
        assertThrows(IllegalArgumentException::class.java) {
            Shift(day, ShiftType.DAY_OFF, machines = listOf(MachineAssignment(rtg12)))
        }
    }

    @Test
    fun `non working day cannot have overtime`() {
        assertThrows(IllegalArgumentException::class.java) {
            Shift(day, ShiftType.ANNUAL_LEAVE, overtime = true)
        }
    }

    @Test
    fun `overtime machine requires overtime`() {
        assertThrows(IllegalArgumentException::class.java) {
            Shift(
                date = day,
                type = ShiftType.MORNING,
                overtime = false,
                machines = listOf(MachineAssignment(rtg12, duringOvertime = true)),
            )
        }
    }

    @Test
    fun `overtime without a machine marked as overtime is allowed`() {
        // The operator may have stayed on the same machine and not split the list
        val shift = Shift(day, ShiftType.MORNING, overtime = true, machines = listOf(MachineAssignment(rtg12)))

        assertEquals(12, shift.workedHours)
    }
}
