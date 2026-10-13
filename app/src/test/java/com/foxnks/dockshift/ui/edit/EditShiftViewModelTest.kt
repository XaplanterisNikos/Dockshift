package com.foxnks.dockshift.ui.edit

import com.foxnks.dockshift.domain.machine.Machine
import com.foxnks.dockshift.domain.machine.MachineCategory
import com.foxnks.dockshift.domain.shift.MachineAssignment
import com.foxnks.dockshift.domain.shift.Shift
import com.foxnks.dockshift.domain.shift.ShiftType
import com.foxnks.dockshift.testing.InMemoryMachineRepository
import com.foxnks.dockshift.testing.InMemoryShiftRepository
import com.foxnks.dockshift.testing.MainDispatcherRule
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Loading, saving, moving and deleting in [EditShiftViewModel]. */
class EditShiftViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val day = LocalDate.of(2026, 10, 13)
    private val rtg12 = Machine(1, MachineCategory.RTG, "12", location = "Προβλήτα ΙΙ Μπλόκ 15")
    private val machines = InMemoryMachineRepository(listOf(rtg12))

    /** The current state, expected to be editing. */
    private val EditShiftViewModel.editing get() = uiState.value as EditShiftUiState.Editing

    @Test
    fun `a day without a shift starts a new one`() = runTest {
        val viewModel = EditShiftViewModel(InMemoryShiftRepository(), machines)

        viewModel.start(day)

        assertFalse(viewModel.editing.isExisting)
        assertEquals(day, viewModel.editing.draft.date)
    }

    @Test
    fun `a stored shift is loaded for editing`() = runTest {
        val stored = Shift(day, ShiftType.NIGHT, machines = listOf(MachineAssignment(rtg12)), comments = "σχόλιο")
        val viewModel = EditShiftViewModel(InMemoryShiftRepository(listOf(stored)), machines)

        viewModel.start(day)

        assertTrue(viewModel.editing.isExisting)
        assertEquals(stored, viewModel.editing.draft.toShift())
    }

    @Test
    fun `saving stores the shift and finishes`() = runTest {
        val shifts = InMemoryShiftRepository()
        val viewModel = EditShiftViewModel(shifts, machines)
        viewModel.start(day)

        viewModel.onTypeSelected(ShiftType.AFTERNOON)
        viewModel.onOvertimeToggled(true)
        viewModel.onMachineAdded(rtg12)
        viewModel.onMachineOvertimeToggled(0)
        viewModel.onSave()

        val saved = shifts.stored.getValue(day)
        assertEquals(ShiftType.AFTERNOON, saved.type)
        assertTrue(saved.overtime)
        assertTrue(saved.machines.single().duringOvertime)
        assertTrue(viewModel.editing.finished)
    }

    @Test
    fun `moving a shift to a free day leaves the old day empty`() = runTest {
        val shifts = InMemoryShiftRepository(listOf(Shift(day, ShiftType.MORNING)))
        val viewModel = EditShiftViewModel(shifts, machines)
        viewModel.start(day)

        viewModel.onDateSelected(day.plusDays(1))
        viewModel.onSave()

        assertNull(shifts.stored[day])
        assertEquals(ShiftType.MORNING, shifts.stored.getValue(day.plusDays(1)).type)
    }

    @Test
    fun `moving onto a day that has a shift asks before replacing`() = runTest {
        val next = Shift(day.plusDays(1), ShiftType.DAY_OFF)
        val shifts = InMemoryShiftRepository(listOf(Shift(day, ShiftType.MORNING), next))
        val viewModel = EditShiftViewModel(shifts, machines)
        viewModel.start(day)
        viewModel.onDateSelected(day.plusDays(1))

        viewModel.onSave()

        // Nothing changed yet: the question is shown
        assertEquals(day.plusDays(1), viewModel.editing.confirmReplace)
        assertEquals(next, shifts.stored[day.plusDays(1)])
        assertFalse(viewModel.editing.finished)

        viewModel.onConfirmReplace()

        assertEquals(ShiftType.MORNING, shifts.stored.getValue(day.plusDays(1)).type)
        assertNull(shifts.stored[day])
        assertTrue(viewModel.editing.finished)
    }

    @Test
    fun `cancelling the replace question keeps editing`() = runTest {
        val shifts = InMemoryShiftRepository(listOf(Shift(day, ShiftType.MORNING), Shift(day.plusDays(1), ShiftType.DAY_OFF)))
        val viewModel = EditShiftViewModel(shifts, machines)
        viewModel.start(day)
        viewModel.onDateSelected(day.plusDays(1))
        viewModel.onSave()

        viewModel.onCancelReplace()

        assertNull(viewModel.editing.confirmReplace)
        assertFalse(viewModel.editing.finished)
        assertEquals(2, shifts.stored.size)
    }

    @Test
    fun `delete asks first, then removes the shift`() = runTest {
        val shifts = InMemoryShiftRepository(listOf(Shift(day, ShiftType.MORNING)))
        val viewModel = EditShiftViewModel(shifts, machines)
        viewModel.start(day)

        viewModel.onDeleteRequested()
        assertTrue(viewModel.editing.confirmDelete)
        assertEquals(1, shifts.stored.size)

        viewModel.onConfirmDelete()

        assertTrue(shifts.stored.isEmpty())
        assertTrue(viewModel.editing.finished)
    }

    @Test
    fun `a new shift cannot be deleted`() = runTest {
        val viewModel = EditShiftViewModel(InMemoryShiftRepository(), machines)
        viewModel.start(day)

        viewModel.onDeleteRequested()

        assertFalse(viewModel.editing.confirmDelete)
    }

    @Test
    fun `start runs only once, so rotation does not reload over the edits`() = runTest {
        val viewModel = EditShiftViewModel(InMemoryShiftRepository(), machines)
        viewModel.start(day)
        viewModel.onTypeSelected(ShiftType.NIGHT)

        viewModel.start(day)

        assertEquals(ShiftType.NIGHT, viewModel.editing.draft.type)
    }
}
