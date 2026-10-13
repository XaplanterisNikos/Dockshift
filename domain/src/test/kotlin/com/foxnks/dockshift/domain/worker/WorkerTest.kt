package com.foxnks.dockshift.domain.worker

import com.foxnks.dockshift.domain.machine.MachineCategory
import com.foxnks.dockshift.domain.machine.MachineCategory.FL
import com.foxnks.dockshift.domain.machine.MachineCategory.QC
import com.foxnks.dockshift.domain.machine.MachineCategory.RMG
import com.foxnks.dockshift.domain.machine.MachineCategory.RTG
import com.foxnks.dockshift.domain.machine.MachineCategory.SC
import com.foxnks.dockshift.domain.machine.MachineCategory.TRUCKS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The worker profile: roles, picker categories and the form rules. */
class WorkerTest {

    @Test
    fun `a driver drives only trucks, an operator everything else`() {
        assertEquals(setOf(TRUCKS), WorkerRole.DRIVER.categories)
        assertEquals(setOf(SC, RTG, RMG, FL, QC), WorkerRole.OPERATOR.categories)
        assertEquals(MachineCategory.entries.toSet(), WorkerRole.BOTH.categories)
    }

    @Test
    fun `an empty profile limits nothing`() {
        assertEquals(MachineCategory.entries, WorkerProfile().machineCategories)
        assertFalse(WorkerProfile(id = "x").isFilled)
    }

    @Test
    fun `the picker offers the ticked categories in display order`() {
        val profile = WorkerProfile(role = WorkerRole.OPERATOR, categories = setOf(QC, RTG, SC))
        assertEquals(listOf(SC, RTG, QC), profile.machineCategories)
    }

    @Test
    fun `a role without ticked categories offers all of its own`() {
        assertEquals(listOf(TRUCKS), WorkerProfile(role = WorkerRole.DRIVER).machineCategories)
    }

    @Test
    fun `valid emails`() {
        listOf("foxnks@gmail.com", "nick.x@mail.example.gr", "a+b_c%d-e@x-y.co", "  foxnks@gmail.com ")
            .forEach { assertTrue(it, WorkerForm.isValidEmail(it)) }
    }

    @Test
    fun `invalid emails`() {
        listOf(
            "", "foxnks", "foxnks@", "@gmail.com", "foxnks@gmail", "foxnks@gmail.c", "fox nks@gmail.com",
            "fox@@gmail.com", ".fox@gmail.com", "fox.@gmail.com", "fo..x@gmail.com", "fox@-gmail.com",
            "fox@gmail..com", "fox@gmail.com.", "φοξ@gmail.com", "fox@gmail.123",
        ).forEach { assertFalse(it, WorkerForm.isValidEmail(it)) }
    }

    @Test
    fun `nothing is required`() {
        assertTrue(WorkerForm.problems("", "", null, emptySet()).isEmpty())
    }

    @Test
    fun `form problems`() {
        assertEquals(setOf(WorkerProblem.EMAIL_INVALID), WorkerForm.problems("Νίκος", "nikos@", null, emptySet()))
        assertEquals(setOf(WorkerProblem.NAME_TOO_LONG), WorkerForm.problems("Ν".repeat(61), "", null, emptySet()))
        // Ticked categories of another role do not count
        assertEquals(setOf(WorkerProblem.NO_CATEGORY), WorkerForm.problems("", "", WorkerRole.DRIVER, setOf(QC)))
    }

    @Test
    fun `build trims and keeps only the role's categories`() {
        val profile = WorkerForm.build("id-1", "  Νίκος ", " foxnks@gmail.com ", WorkerRole.DRIVER, setOf(TRUCKS, QC))
        assertEquals(WorkerProfile("id-1", "Νίκος", "foxnks@gmail.com", WorkerRole.DRIVER, setOf(TRUCKS)), profile)
        assertTrue(WorkerForm.build("id-1", "", "", null, setOf(QC)).categories.isEmpty())
    }

    @Test
    fun `new ids are UUIDs and differ`() {
        val first = WorkerForm.newId()
        assertTrue(Regex("^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$").matches(first))
        assertNotEquals(first, WorkerForm.newId())
    }
}
