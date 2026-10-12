package com.foxnks.dockshift.data.db

import com.foxnks.dockshift.domain.machine.MachineCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The starting machine list must match the v1 fleet exactly. */
class MachineSeedTest {

    @Test
    fun `same number of machines per category as v1`() {
        val counts = MachineSeed.machines.mapValues { it.value.size }

        assertEquals(
            mapOf(
                MachineCategory.SC to 7,
                MachineCategory.RTG to 45,
                MachineCategory.RMG to 22,
                MachineCategory.FL to 15,
                MachineCategory.TRUCKS to 182,
                MachineCategory.QC to 31,
            ),
            counts,
        )
    }

    @Test
    fun `every category has machines`() {
        assertEquals(MachineCategory.entries.toSet(), MachineSeed.machines.keys)
    }

    @Test
    fun `no duplicate numbers inside a category`() {
        // The database has a unique index on (category, number): a duplicate here
        // would make the very first app start fail
        MachineSeed.machines.forEach { (category, numbers) ->
            assertEquals("$category has duplicates", numbers.size, numbers.toSet().size)
        }
    }

    @Test
    fun `spot check of v1 numbers`() {
        assertTrue("6250" in MachineSeed.machines.getValue(MachineCategory.TRUCKS))
        assertTrue("6410" in MachineSeed.machines.getValue(MachineCategory.TRUCKS))
        // The gaps of v1 must stay gaps
        assertTrue("554" !in MachineSeed.machines.getValue(MachineCategory.FL))
        assertTrue("24" !in MachineSeed.machines.getValue(MachineCategory.QC))
    }
}
