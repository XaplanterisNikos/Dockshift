package com.foxnks.dockshift.data.db

import com.foxnks.dockshift.domain.machine.MachineCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The v1 machine locations used as starting values. */
class MachinePositionsTest {

    @Test
    fun `machines with their own location`() {
        assertEquals("Προβλήτα ΙΙ Μπλόκ 15", MachinePositions.locationOf(MachineCategory.RTG, "12"))
        assertEquals("Προβλήτα ΙΙΙ Μπλόκ 31 - 32", MachinePositions.locationOf(MachineCategory.RMG, "18"))
        assertEquals("Προβλήτα Ι Δυτικά", MachinePositions.locationOf(MachineCategory.QC, "43"))
    }

    @Test
    fun `shared parking areas`() {
        assertEquals(MachinePositions.SC_PARKING, MachinePositions.locationOf(MachineCategory.SC, "97"))
        assertEquals(MachinePositions.FL_PARKING, MachinePositions.locationOf(MachineCategory.FL, "560"))
    }

    @Test
    fun `trucks park by even or odd fleet number`() {
        assertEquals(MachinePositions.TRUCKS_EVEN, MachinePositions.locationOf(MachineCategory.TRUCKS, "6250"))
        assertEquals(MachinePositions.TRUCKS_ODD, MachinePositions.locationOf(MachineCategory.TRUCKS, "6251"))
        assertNull(MachinePositions.locationOf(MachineCategory.TRUCKS, "not a number"))
    }

    @Test
    fun `every seeded RTG, RMG and QC has a location`() {
        listOf(MachineCategory.RTG, MachineCategory.RMG, MachineCategory.QC).forEach { category ->
            MachineSeed.machines.getValue(category).forEach { number ->
                assertEquals("$category $number", true, MachinePositions.locationOf(category, number) != null)
            }
        }
    }

    @Test
    fun `numerals use Greek letters only`() {
        // v1 had a Latin "I" mixed into some Greek numerals ("ΙΙI"); it must not come back
        MachineCategory.entries.forEach { category ->
            MachineSeed.machines.getValue(category).forEach { number ->
                val location = MachinePositions.locationOf(category, number).orEmpty()
                assertEquals("$category $number: $location", false, location.contains('I'))
            }
        }
    }
}
