package com.foxnks.dockshift.domain.legacy

import com.foxnks.dockshift.domain.shift.ShiftType
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Optional check against a REAL v1 backup file, run only on a developer's machine.
 *
 * Real files hold personal shift history, so they are never committed to this
 * public repository. To run it, point an environment variable at your own file:
 *
 * ```
 * # PowerShell
 * $env:DOCKSHIFT_V1_CSV = "C:\path\to\dataShift_foxnks.csv"
 * .\gradlew :domain:test --tests "*RealV1FileTest*"
 * ```
 *
 * Without the variable the test is skipped, not failed.
 */
class RealV1FileTest {

    private val notes = object : LegacyNotes {
        override fun containerMoves(moves: Int) = "moves=$moves"
        override fun machineOnNonWorkingDay(machineLabel: String) = "machine=$machineLabel"
    }

    @Test
    fun `real v1 file imports completely`() {
        // Passed in by domain/build.gradle.kts from the DOCKSHIFT_V1_CSV variable
        val path = System.getProperty("dockshift.v1csv").orEmpty()
        assumeTrue("DOCKSHIFT_V1_CSV not set: skipping the real-file test", path.isNotBlank())

        val rows = LegacyCsvParser.parse(File(path).readText(Charsets.UTF_8))
        val plan = LegacyImportPlanner(notes).plan(rows, existingDates = emptySet(), knownMachines = emptySet())

        // Every row is understood...
        assertEquals("problems: ${plan.problems.take(10)}", emptyList<LegacyProblem>(), plan.problems)
        // ...and every row becomes exactly one shift (or is a reported duplicate)
        assertEquals(rows.size, plan.shifts.size + plan.duplicatesInFile)
        // Working shifts keep their machine
        assertTrue(plan.shifts.filter { it.type.isWorkShift }.all { it.machine != null })

        // A summary to compare by eye with what v1 showed
        println("Real v1 file: ${rows.size} rows, ${plan.shifts.size} shifts, ${plan.firstDate} .. ${plan.lastDate}")
        ShiftType.entries.forEach { type ->
            val count = plan.shifts.count { it.type == type }
            if (count > 0) println("  $type: $count")
        }
        println("  duplicates skipped: ${plan.duplicatesInFile}, machines used: ${plan.newMachines.size}")
    }
}
