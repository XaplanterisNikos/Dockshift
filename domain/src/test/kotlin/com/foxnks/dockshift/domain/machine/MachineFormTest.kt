package com.foxnks.dockshift.domain.machine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The rules of the machine list: uniqueness, reactivation, renaming, clean values. */
class MachineFormTest {

    private val rtg12 = Machine(1, MachineCategory.RTG, "12", location = "Προβλήτα ΙΙ Μπλόκ 15")
    private val rtg13 = Machine(2, MachineCategory.RTG, "13")
    private val retiredRtg40 = Machine(3, MachineCategory.RTG, "40", active = false, location = "Μπλόκ 9")
    private val rmg12 = Machine(4, MachineCategory.RMG, "12")
    private val all = listOf(rtg12, rtg13, retiredRtg40, rmg12)

    @Test
    fun `a new unique number is saved trimmed, with a clean location`() {
        val plan = MachineForm(category = MachineCategory.RTG, number = " 46 ", location = "  Προβλήτα   ΙΙ  ").plan(all)

        assertEquals(
            MachineSavePlan.Save(Machine(0, MachineCategory.RTG, "46", location = "Προβλήτα ΙΙ")),
            plan,
        )
    }

    @Test
    fun `a blank location is stored as no location`() {
        val plan = MachineForm(category = MachineCategory.QC, number = "50", location = "   ").plan(all)

        assertNull((plan as MachineSavePlan.Save).machine.location)
    }

    @Test
    fun `adding a number that exists active is a duplicate`() {
        val plan = MachineForm(category = MachineCategory.RTG, number = "12").plan(all)

        assertEquals(MachineSavePlan.Duplicate(rtg12), plan)
    }

    @Test
    fun `the same number in another category is not a duplicate`() {
        // RMG 12 exists, RTG 12 too; FL 12 is a different machine
        val plan = MachineForm(category = MachineCategory.FL, number = "12").plan(all)

        assertTrue(plan is MachineSavePlan.Save)
    }

    @Test
    fun `adding a number that exists inactive reactivates that machine`() {
        val plan = MachineForm(category = MachineCategory.RTG, number = "40").plan(all)

        // Same id: the machine keeps its shift history; no location typed, old one kept
        assertEquals(MachineSavePlan.Reactivate(retiredRtg40.copy(active = true)), plan)
    }

    @Test
    fun `reactivating with a typed location replaces the old one`() {
        val plan = MachineForm(category = MachineCategory.RTG, number = "40", location = "Μπλόκ 3").plan(all)

        assertEquals("Μπλόκ 3", (plan as MachineSavePlan.Reactivate).machine.location)
    }

    @Test
    fun `renaming onto another machine's number is a duplicate`() {
        val plan = MachineForm.from(rtg13).copy(number = "12").plan(all)

        assertEquals(MachineSavePlan.Duplicate(rtg12), plan)
    }

    @Test
    fun `renaming onto an inactive machine's number is a duplicate, never a merge`() {
        val plan = MachineForm.from(rtg13).copy(number = "40").plan(all)

        assertEquals(MachineSavePlan.Duplicate(retiredRtg40), plan)
    }

    @Test
    fun `renaming keeps the machine's identity`() {
        val plan = MachineForm.from(rtg12).copy(number = "46").plan(all)

        // Same id: every past shift of RTG 12 now shows RTG 46
        assertEquals(MachineSavePlan.Save(rtg12.copy(number = "46")), plan)
    }

    @Test
    fun `saving a machine with its own number is not a duplicate`() {
        val plan = MachineForm.from(rtg12).copy(location = "Μπλόκ 1").plan(all)

        assertEquals(MachineSavePlan.Save(rtg12.copy(location = "Μπλόκ 1")), plan)
    }

    @Test
    fun `deactivating and reactivating is a plain save`() {
        val deactivated = MachineForm.from(rtg12).copy(active = false).plan(all)
        val reactivated = MachineForm.from(retiredRtg40).copy(active = true).plan(all)

        assertFalse((deactivated as MachineSavePlan.Save).machine.active)
        assertTrue((reactivated as MachineSavePlan.Save).machine.active)
    }

    @Test
    fun `a blank number is rejected`() {
        assertEquals(MachineSavePlan.BlankNumber, MachineForm(category = MachineCategory.SC, number = "  ").plan(all))
    }

    @Test
    fun `a too long number is rejected`() {
        val tooLong = "1".repeat(MachineForm.MAX_NUMBER_LENGTH + 1)

        assertEquals(MachineSavePlan.NumberTooLong, MachineForm(category = MachineCategory.SC, number = tooLong).plan(all))
    }

    @Test
    fun `a form from a machine shows its values`() {
        val form = MachineForm.from(retiredRtg40)

        assertFalse(form.isNew)
        assertEquals("40", form.number)
        assertEquals("Μπλόκ 9", form.location)
        assertFalse(form.active)
        assertTrue(MachineForm(category = MachineCategory.SC).isNew)
    }
}
