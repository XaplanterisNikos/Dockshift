package com.foxnks.dockshift.domain.legacy

import com.foxnks.dockshift.domain.legacy.LegacyProblem.NumberColumn
import com.foxnks.dockshift.domain.machine.MachineCategory
import com.foxnks.dockshift.domain.shift.ShiftType
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The v1 → v2 translation rules of [LegacyImportPlanner]. */
class LegacyImportPlannerTest {

    /** Plain notes, so tests read clearly and do not depend on translations. */
    private val notes = object : LegacyNotes {
        override fun containerMoves(moves: Int) = "moves=$moves"
        override fun machineOnNonWorkingDay(machineLabel: String) = "machine=$machineLabel"
    }
    private val planner = LegacyImportPlanner(notes)

    private val rtg12 = LegacyMachine(MachineCategory.RTG, "12")

    /** Parses CSV text and plans it against an empty app. */
    private fun plan(
        csv: String,
        existingDates: Set<LocalDate> = emptySet(),
        knownMachines: Set<LegacyMachine> = emptySet(),
    ) = planner.plan(LegacyCsvParser.parse(csv), existingDates, knownMachines)

    @Test
    fun `typical v1 rows become shifts`() {
        val result = plan(
            "1,Δευτέρα,3,10,2022,Πρωί,RTG,12,null,0\n" +
                "2,Τρίτη,4,10,2022,Ρεπό,none,none,null,0\n",
        )

        assertTrue(result.canImport)
        assertEquals(
            listOf(
                LegacyShift(LocalDate.of(2022, 10, 3), ShiftType.MORNING, rtg12, comments = null),
                LegacyShift(LocalDate.of(2022, 10, 4), ShiftType.DAY_OFF, machine = null, comments = null),
            ),
            result.shifts,
        )
    }

    @Test
    fun `greek and english labels can be mixed in one file`() {
        // Seen in real data: the phone's language changed while v1 was in use
        val result = plan(
            "1,Thursday,6,10,2022,Night,RMG,3,null,0\n" +
                "2,Παρασκευή,7,10,2022,Νύχτα,RMG,3,null,0\n",
        )

        assertEquals(listOf(ShiftType.NIGHT, ShiftType.NIGHT), result.shifts.map { it.type })
    }

    @Test
    fun `shifts are sorted by date even if the file is not`() {
        val result = plan(
            "1,Τρίτη,4,10,2022,Ρεπό,none,none,null,0\n" +
                "2,Δευτέρα,3,10,2022,Ρεπό,none,none,null,0\n",
        )

        assertEquals(LocalDate.of(2022, 10, 3), result.firstDate)
        assertEquals(LocalDate.of(2022, 10, 4), result.lastDate)
    }

    @Test
    fun `comment text is kept, null and empty become no comment`() {
        val result = plan(
            "1,Δευτέρα,3,10,2022,Πρωί,RTG,12,βλάβη spreader,0\n" +
                "2,Τρίτη,4,10,2022,Πρωί,RTG,12,null,0\n" +
                "3,Τετάρτη,5,10,2022,Πρωί,RTG,12,,0\n",
        )

        assertEquals(listOf("βλάβη spreader", null, null), result.shifts.map { it.comments })
    }

    @Test
    fun `container moves are kept as a note`() {
        val result = plan(
            "1,Δευτέρα,3,10,2022,Πρωί,RTG,12,null,34\n" +
                "2,Τρίτη,4,10,2022,Πρωί,RTG,12,βλάβη,5\n",
        )

        assertEquals(listOf("moves=34", "βλάβη\nmoves=5"), result.shifts.map { it.comments })
    }

    @Test
    fun `machine on a non working day becomes a note`() {
        val result = plan("1,Δευτέρα,3,10,2022,Ρεπό,RTG,12,null,0\n")

        val shift = result.shifts.single()
        assertNull(shift.machine)
        assertEquals("machine=RTG 12", shift.comments)
    }

    @Test
    fun `first row wins when a date appears twice`() {
        val result = plan(
            "1,Δευτέρα,3,10,2022,Πρωί,RTG,12,first,0\n" +
                "2,Δευτέρα,3,10,2022,Νύχτα,SC,97,second,0\n",
        )

        assertEquals("first", result.shifts.single().comments)
        assertEquals(1, result.duplicatesInFile)
    }

    @Test
    fun `days the app already has are left alone`() {
        val existing = setOf(LocalDate.of(2022, 10, 3))
        val result = plan(
            "1,Δευτέρα,3,10,2022,Πρωί,RTG,12,null,0\n" +
                "2,Τρίτη,4,10,2022,Ρεπό,none,none,null,0\n",
            existingDates = existing,
        )

        assertEquals(listOf(LocalDate.of(2022, 10, 4)), result.shifts.map { it.date })
        assertEquals(1, result.alreadyInApp)
    }

    @Test
    fun `machines the app does not know are listed as new`() {
        val result = plan(
            "1,Δευτέρα,3,10,2022,Πρωί,RTG,12,null,0\n" +
                "2,Τρίτη,4,10,2022,Πρωί,TRUCKS,6999,null,0\n",
            knownMachines = setOf(rtg12),
        )

        assertEquals(setOf(LegacyMachine(MachineCategory.TRUCKS, "6999")), result.newMachines)
    }

    @Test
    fun `every problem is reported with its line, and nothing can be imported`() {
        val result = plan(
            "1,Δευτέρα,3,10,2022,Πρωί,RTG,12,null,0\n" +
                "2,Τρίτη,X,10,2022,Πρωί,RTG,12,null,0\n" +
                "3,Τρίτη,31,2,2022,Πρωί,RTG,12,null,0\n" +
                "4,Τρίτη,4,10,2022,Πρω,RTG,12,null,0\n" +
                "5,Τρίτη,5,10,2022,Πρωί,CRANE,1,null,0\n" +
                "6,Τρίτη,6,10,2022,Πρωί,RTG,12,null,lots\n",
        )

        assertFalse(result.canImport)
        assertEquals(
            listOf(
                LegacyProblem.NotANumber(2, NumberColumn.DAY, "X"),
                LegacyProblem.InvalidDate(3, day = 31, month = 2, year = 2022),
                LegacyProblem.UnknownShiftType(4, "Πρω"),
                LegacyProblem.UnknownMachineCategory(5, "CRANE"),
                LegacyProblem.NotANumber(6, NumberColumn.CONTAINERS, "lots"),
            ),
            result.problems,
        )
    }

    @Test
    fun `a broken file is a plan with one problem`() {
        val result = LegacyImportPlan.broken(LegacyProblem.UnterminatedQuote(7))

        assertFalse(result.canImport)
        assertEquals(7, result.problems.single().line)
    }

    @Test
    fun `an empty file has nothing to import`() {
        val result = plan("")

        assertFalse(result.canImport)
        assertTrue(result.problems.isEmpty())
    }
}
