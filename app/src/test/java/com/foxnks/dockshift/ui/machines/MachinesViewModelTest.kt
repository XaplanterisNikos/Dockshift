package com.foxnks.dockshift.ui.machines

import com.foxnks.dockshift.domain.machine.Machine
import com.foxnks.dockshift.domain.machine.MachineCategory
import com.foxnks.dockshift.testing.InMemoryMachineRepository
import com.foxnks.dockshift.testing.MainDispatcherRule
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Filtering, adding, editing and deleting in [MachinesViewModel]. */
@OptIn(ExperimentalCoroutinesApi::class) // UnconfinedTestDispatcher is still experimental
class MachinesViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val rtg12 = Machine(1, MachineCategory.RTG, "12", location = "Προβλήτα ΙΙ Μπλόκ 15")
    private val rtg13 = Machine(2, MachineCategory.RTG, "13", location = "Προβλήτα ΙΙ Μπλόκ 16")
    private val retiredRtg40 = Machine(3, MachineCategory.RTG, "40", active = false)
    private val qc6 = Machine(4, MachineCategory.QC, "6", location = "Προβλήτα ΙΙ")

    /** RTG 12 was used in 5 shifts; the others never. */
    private val machines = InMemoryMachineRepository(listOf(rtg12, rtg13, retiredRtg40, qc6), usage = mapOf(1L to 5))

    /** Collects the state like the screen does, so WhileSubscribed runs. */
    private fun TestScope.observe(viewModel: MachinesViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
    }

    /** The current state, expected to be loaded. */
    private val MachinesViewModel.ready get() = uiState.value as MachinesUiState.Ready

    /** The open sheet, expected to be open. */
    private val MachinesViewModel.sheet get() = ready.sheet!!

    /** A view model with the state collected. */
    private fun TestScope.newViewModel() = MachinesViewModel(machines).also { observe(it) }

    @Test
    fun `lists the selected category with usage, inactive ones included`() = runTest {
        val viewModel = newViewModel()

        assertEquals(MachineCategory.RTG, viewModel.ready.category)
        assertEquals(listOf("12" to 5, "13" to 0, "40" to 0), viewModel.ready.rows.map { it.machine.number to it.shifts })
    }

    @Test
    fun `search matches number or location`() = runTest {
        val viewModel = newViewModel()

        viewModel.onQueryChanged("13")
        assertEquals(listOf("13"), viewModel.ready.rows.map { it.machine.number })

        viewModel.onQueryChanged("μπλόκ 15")
        assertEquals(listOf("12"), viewModel.ready.rows.map { it.machine.number })
    }

    @Test
    fun `location suggestions are the distinct locations in use`() = runTest {
        val viewModel = newViewModel()

        assertEquals(listOf("Προβλήτα ΙΙ", "Προβλήτα ΙΙ Μπλόκ 15", "Προβλήτα ΙΙ Μπλόκ 16"), viewModel.ready.locations)
    }

    @Test
    fun `adding a new machine saves it and shows it`() = runTest {
        val viewModel = newViewModel()
        viewModel.onCategorySelected(MachineCategory.QC)
        viewModel.onQueryChanged("6")

        viewModel.onAddClicked()
        viewModel.onFormChanged(viewModel.sheet.form.copy(number = " 50 ", location = "Προβλήτα ΙΙ"))
        viewModel.onSave()

        assertTrue(machines.stored.any { it.label == "QC 50" && it.location == "Προβλήτα ΙΙ" })
        assertNull(viewModel.ready.sheet)
        assertEquals(MachineMessage.Added("QC 50"), viewModel.ready.message)
        // The search is cleared, so the new machine is in view
        assertTrue(viewModel.ready.rows.any { it.machine.label == "QC 50" })
    }

    @Test
    fun `adding an existing active number shows an error and saves nothing`() = runTest {
        val viewModel = newViewModel()

        viewModel.onAddClicked()
        viewModel.onFormChanged(viewModel.sheet.form.copy(number = "12"))
        viewModel.onSave()

        assertEquals(MachineFormError.Duplicate(rtg12), viewModel.sheet.error)
        assertEquals(4, machines.stored.size)
    }

    @Test
    fun `typing again clears the error`() = runTest {
        val viewModel = newViewModel()
        viewModel.onAddClicked()
        viewModel.onSave()
        assertEquals(MachineFormError.BlankNumber, viewModel.sheet.error)

        viewModel.onFormChanged(viewModel.sheet.form.copy(number = "4"))

        assertNull(viewModel.sheet.error)
    }

    @Test
    fun `adding an inactive number reactivates the same machine`() = runTest {
        val viewModel = newViewModel()

        viewModel.onAddClicked()
        viewModel.onFormChanged(viewModel.sheet.form.copy(number = "40"))
        viewModel.onSave()

        val reactivated = machines.stored.single { it.id == retiredRtg40.id }
        assertTrue(reactivated.active)
        assertEquals(4, machines.stored.size)
        assertEquals(MachineMessage.Reactivated("RTG 40"), viewModel.ready.message)
    }

    @Test
    fun `renumbering and moving keeps the same machine`() = runTest {
        val viewModel = newViewModel()

        viewModel.onMachineClicked(viewModel.ready.rows.first())
        viewModel.onFormChanged(viewModel.sheet.form.copy(number = "46", location = "Μπλόκ 1"))
        viewModel.onSave()

        assertEquals(rtg12.copy(number = "46", location = "Μπλόκ 1"), machines.stored.single { it.id == rtg12.id })
        assertNull(viewModel.ready.sheet)
    }

    @Test
    fun `the category of a stored machine cannot change`() = runTest {
        val viewModel = newViewModel()

        viewModel.onMachineClicked(viewModel.ready.rows.first())
        viewModel.onFormChanged(viewModel.sheet.form.copy(category = MachineCategory.RMG))

        assertEquals(MachineCategory.RTG, viewModel.sheet.form.category)
    }

    @Test
    fun `deactivating and reactivating from the sheet`() = runTest {
        val viewModel = newViewModel()

        viewModel.onMachineClicked(viewModel.ready.rows.first())
        viewModel.onFormChanged(viewModel.sheet.form.copy(active = false))
        viewModel.onSave()
        assertFalse(machines.stored.single { it.id == rtg12.id }.active)

        viewModel.onMachineClicked(viewModel.ready.rows.first { it.machine.id == rtg12.id })
        viewModel.onFormChanged(viewModel.sheet.form.copy(active = true))
        viewModel.onSave()
        assertTrue(machines.stored.single { it.id == rtg12.id }.active)
    }

    @Test
    fun `an unused machine can be deleted for good`() = runTest {
        val viewModel = newViewModel()

        viewModel.onMachineClicked(viewModel.ready.rows.single { it.machine.id == rtg13.id })
        viewModel.onDeleteRequested()
        assertTrue(viewModel.sheet.confirmDelete)
        viewModel.onDeleteConfirmed()

        assertTrue(machines.stored.none { it.id == rtg13.id })
        assertEquals(MachineMessage.Deleted("RTG 13"), viewModel.ready.message)
    }

    @Test
    fun `a machine with shifts is never offered for deletion`() = runTest {
        val viewModel = newViewModel()

        viewModel.onMachineClicked(viewModel.ready.rows.single { it.machine.id == rtg12.id })
        viewModel.onDeleteRequested()

        assertFalse(viewModel.sheet.confirmDelete)
        assertEquals(5, viewModel.sheet.shifts)
    }

    @Test
    fun `a shown message is cleared`() = runTest {
        val viewModel = newViewModel()
        viewModel.onAddClicked()
        viewModel.onFormChanged(viewModel.sheet.form.copy(number = "46"))
        viewModel.onSave()

        viewModel.onMessageShown()

        assertNull(viewModel.ready.message)
    }
}
