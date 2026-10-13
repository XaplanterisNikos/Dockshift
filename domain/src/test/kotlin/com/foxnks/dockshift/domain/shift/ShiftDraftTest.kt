package com.foxnks.dockshift.domain.shift

import com.foxnks.dockshift.domain.machine.Machine
import com.foxnks.dockshift.domain.machine.MachineCategory
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The editing rules of [ShiftDraft]: whatever the user taps, the draft stays saveable. */
class ShiftDraftTest {

    private val day = LocalDate.of(2026, 10, 13)
    private val rtg12 = Machine(1, MachineCategory.RTG, "12")
    private val rtg7 = Machine(2, MachineCategory.RTG, "7")
    private val qc31 = Machine(3, MachineCategory.QC, "31")

    /** A night shift with overtime and three machines, the last one in overtime. */
    private val busyNight = ShiftDraft(day, ShiftType.NIGHT)
        .withOvertime(true)
        .addMachine(rtg12)
        .addMachine(rtg7)
        .addMachine(qc31)
        .toggleOvertimeAt(2)

    @Test
    fun `machines keep the order they were added in`() {
        assertEquals(listOf("RTG 12", "RTG 7", "QC 31"), busyNight.machines.map { it.machine.label })
        assertEquals(listOf(false, false, true), busyNight.machines.map { it.duringOvertime })
    }

    @Test
    fun `switching to a day off drops machines and overtime`() {
        val dayOff = busyNight.withType(ShiftType.DAY_OFF)

        assertTrue(dayOff.machines.isEmpty())
        assertFalse(dayOff.overtime)
        // And it is saveable: Shift's own rules would throw otherwise
        assertEquals(ShiftType.DAY_OFF, dayOff.toShift().type)
    }

    @Test
    fun `switching between working shifts keeps the machines`() {
        assertEquals(3, busyNight.withType(ShiftType.MORNING).machines.size)
    }

    @Test
    fun `turning overtime off clears the overtime marks`() {
        val noOvertime = busyNight.withOvertime(false)

        assertFalse(noOvertime.overtime)
        assertTrue(noOvertime.machines.none { it.duringOvertime })
        noOvertime.toShift()
    }

    @Test
    fun `overtime marks need overtime`() {
        val draft = ShiftDraft(day, ShiftType.MORNING).addMachine(rtg12).toggleOvertimeAt(0)

        assertFalse(draft.machines.single().duringOvertime)
    }

    @Test
    fun `non working days ignore machines and overtime`() {
        val draft = ShiftDraft(day, ShiftType.ANNUAL_LEAVE).addMachine(rtg12).withOvertime(true)

        assertTrue(draft.machines.isEmpty())
        assertFalse(draft.overtime)
    }

    @Test
    fun `machines can be moved and removed`() {
        // 12, 7, 31 → moveUp(2) → 12, 31, 7 → moveDown(0) → 31, 12, 7
        val moved = busyNight.moveMachineUp(2).moveMachineDown(0)

        assertEquals(listOf("QC 31", "RTG 12", "RTG 7"), moved.machines.map { it.machine.label })
        assertEquals(listOf("RTG 12", "QC 31"), busyNight.removeMachineAt(1).machines.map { it.machine.label })
    }

    @Test
    fun `moves past the ends do nothing`() {
        assertEquals(busyNight, busyNight.moveMachineUp(0))
        assertEquals(busyNight, busyNight.moveMachineDown(2))
        assertEquals(busyNight, busyNight.removeMachineAt(9))
    }

    @Test
    fun `blank comment is saved as no comment, text is trimmed`() {
        assertNull(ShiftDraft(day).withComments("   ").toShift().comments)
        assertEquals("βλάβη", ShiftDraft(day).withComments("  βλάβη ").toShift().comments)
    }

    @Test
    fun `a stored shift round-trips through a draft`() {
        val shift = busyNight.withComments("σχόλιο").toShift()

        assertEquals(shift, ShiftDraft.from(shift).toShift())
    }
}
