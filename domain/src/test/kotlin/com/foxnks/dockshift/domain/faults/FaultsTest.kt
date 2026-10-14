package com.foxnks.dockshift.domain.faults

import com.foxnks.dockshift.domain.machine.MachineCategory
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The fault catalog and how a machine's faults are split for its page. */
class FaultsTest {

    private val day = LocalDate.of(2026, 10, 14)

    @Test
    fun `every category has its own list, without repeats`() {
        MachineCategory.entries.forEach { category ->
            val faults = FaultCatalog.forCategory(category)
            assertTrue("$category", faults.isNotEmpty())
            assertEquals("$category", faults.size, faults.toSet().size)
        }
    }

    @Test
    fun `lists follow the operators' order`() {
        assertEquals(
            listOf(
                FaultType.LIGHTS, FaultType.TIRE, FaultType.AIR_LEAK, FaultType.CRASHED,
                FaultType.RADIO, FaultType.TERMINAL, FaultType.HEATING, FaultType.WIPERS,
            ),
            FaultCatalog.forCategory(MachineCategory.TRUCKS),
        )
        assertFalse(FaultType.SPREADER in FaultCatalog.forCategory(MachineCategory.TRUCKS))
        assertTrue(FaultType.TROLLEY in FaultCatalog.forCategory(MachineCategory.RTG))
    }

    @Test
    fun `open faults first, history by latest repair, open ones not offered again`() {
        val lights = MachineFault(1, 7, FaultType.LIGHTS, day.minusDays(3))
        val radio = MachineFault(2, 7, FaultType.RADIO, day.minusDays(1))
        val oldTire = MachineFault(3, 7, FaultType.TIRE, day.minusDays(20), day.minusDays(18))
        val oldLights = MachineFault(4, 7, FaultType.LIGHTS, day.minusDays(10), day.minusDays(5))

        val faults = MachineFaults.of(listOf(radio, oldTire, lights, oldLights))

        assertEquals(listOf(lights, radio), faults.open)
        assertEquals(listOf(oldLights, oldTire), faults.history)
        assertEquals(
            listOf(FaultType.SPREADER, FaultType.TERMINAL, FaultType.GLASS, FaultType.TIRE),
            faults.reportable(listOf(FaultType.SPREADER, FaultType.LIGHTS, FaultType.TERMINAL, FaultType.RADIO, FaultType.GLASS, FaultType.TIRE)),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a fault cannot be fixed before it was reported`() {
        MachineFault(1, 7, FaultType.LIGHTS, day, day.minusDays(1))
    }

    @Test
    fun `fixed on the same day is fine`() {
        assertFalse(MachineFault(1, 7, FaultType.LIGHTS, day, day).isOpen)
    }
}
