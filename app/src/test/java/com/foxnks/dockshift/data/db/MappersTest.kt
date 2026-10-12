package com.foxnks.dockshift.data.db

import com.foxnks.dockshift.data.db.entity.AssignmentWithMachine
import com.foxnks.dockshift.data.db.entity.MachineEntity
import com.foxnks.dockshift.data.db.entity.ShiftMachineEntity
import com.foxnks.dockshift.data.db.entity.ShiftWithMachines
import com.foxnks.dockshift.domain.machine.Machine
import com.foxnks.dockshift.domain.machine.MachineCategory
import com.foxnks.dockshift.domain.shift.MachineAssignment
import com.foxnks.dockshift.domain.shift.Shift
import com.foxnks.dockshift.domain.shift.ShiftType
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** Conversions between database rows and domain models (pure JVM, no database). */
class MappersTest {

    private val rtg12 = Machine(id = 5, category = MachineCategory.RTG, number = "12")
    private val qc31 = Machine(id = 9, category = MachineCategory.QC, number = "31")

    @Test
    fun `every shift type has a unique code that reads back`() {
        val codes = ShiftType.entries.map(ShiftTypeCodes::toCode)

        assertEquals("codes must be unique", codes.size, codes.toSet().size)
        ShiftType.entries.forEach { type ->
            assertEquals(type, ShiftTypeCodes.fromCode(ShiftTypeCodes.toCode(type)))
        }
    }

    @Test
    fun `shift survives a round trip through its rows`() {
        val shift = Shift(
            date = LocalDate.of(2026, 10, 13),
            type = ShiftType.NIGHT,
            overtime = true,
            machines = listOf(MachineAssignment(rtg12), MachineAssignment(qc31, duringOvertime = true)),
            comments = "Spreader fault at 03:00",
        )

        // Rebuild what Room would return, deliberately in reverse order
        val machineRows = mapOf(rtg12.id to rtg12, qc31.id to qc31).mapValues { (_, m) ->
            MachineEntity(id = m.id, category = m.category.name, number = m.number, active = m.active)
        }
        val rows = ShiftWithMachines(
            shift = shift.toEntity(),
            assignments = shift.toAssignmentEntities().reversed().map {
                AssignmentWithMachine(assignment = it, machine = machineRows.getValue(it.machineId))
            },
        )

        assertEquals(shift, rows.toDomain())
    }

    @Test
    fun `date is stored as ISO text`() {
        val entity = Shift(LocalDate.of(2026, 3, 7), ShiftType.DAY_OFF).toEntity()

        assertEquals("2026-03-07", entity.date)
    }

    @Test
    fun `machine rows are numbered in order`() {
        val shift = Shift(
            date = LocalDate.of(2026, 10, 13),
            type = ShiftType.MORNING,
            machines = listOf(MachineAssignment(qc31), MachineAssignment(rtg12), MachineAssignment(qc31)),
        )

        assertEquals(
            listOf(
                ShiftMachineEntity("2026-10-13", 0, qc31.id, false),
                ShiftMachineEntity("2026-10-13", 1, rtg12.id, false),
                ShiftMachineEntity("2026-10-13", 2, qc31.id, false),
            ),
            shift.toAssignmentEntities(),
        )
    }

    @Test
    fun `unsaved machine cannot be stored`() {
        val unsaved = Machine(id = 0, category = MachineCategory.SC, number = "97")
        val shift = Shift(LocalDate.of(2026, 10, 13), ShiftType.MORNING, machines = listOf(MachineAssignment(unsaved)))

        assertThrows(IllegalArgumentException::class.java) { shift.toAssignmentEntities() }
    }

    @Test
    fun `machines sort by category order then numerically`() {
        val machines = listOf(
            Machine(1, MachineCategory.QC, "6"),
            Machine(2, MachineCategory.RTG, "10"),
            Machine(3, MachineCategory.SC, "100"),
            Machine(4, MachineCategory.RTG, "9"),
        )

        assertEquals(
            listOf("SC 100", "RTG 9", "RTG 10", "QC 6"),
            machines.sortedWith(machineDisplayOrder).map { it.label },
        )
    }
}
