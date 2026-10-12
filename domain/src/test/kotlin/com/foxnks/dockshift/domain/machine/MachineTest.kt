package com.foxnks.dockshift.domain.machine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** Rules of [Machine]. */
class MachineTest {

    @Test
    fun `label shows category and number`() {
        assertEquals("RTG 12", Machine(id = 1, category = MachineCategory.RTG, number = "12").label)
    }

    @Test
    fun `blank number is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            Machine(id = 0, category = MachineCategory.QC, number = "  ")
        }
    }
}
