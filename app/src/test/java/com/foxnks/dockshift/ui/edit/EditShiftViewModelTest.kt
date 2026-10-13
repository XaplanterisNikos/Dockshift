package com.foxnks.dockshift.ui.edit

import com.foxnks.dockshift.domain.machine.Machine
import com.foxnks.dockshift.domain.machine.MachineCategory
import com.foxnks.dockshift.domain.rotation.RotationAnchor
import com.foxnks.dockshift.domain.rotation.RotationSchedule
import com.foxnks.dockshift.domain.settings.AppSettings
import com.foxnks.dockshift.domain.settings.WorkSchedule
import com.foxnks.dockshift.domain.shift.MachineAssignment
import com.foxnks.dockshift.domain.shift.Shift
import com.foxnks.dockshift.domain.shift.ShiftType
import com.foxnks.dockshift.testing.FakeSettingsRepository
import com.foxnks.dockshift.testing.InMemoryMachineRepository
import com.foxnks.dockshift.testing.InMemoryShiftRepository
import com.foxnks.dockshift.testing.MainDispatcherRule
import com.foxnks.dockshift.domain.worker.WorkerProfile
import com.foxnks.dockshift.domain.worker.WorkerRole
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset
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

    /** The clock says 10:00 on [day]: a morning shift is running. */
    private val clock = Clock.fixed(day.atTime(10, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC)

    /** The current state, expected to be editing. */
    private val EditShiftViewModel.editing get() = uiState.value as EditShiftUiState.Editing

    @Test
    fun `a day without a shift starts a new one`() = runTest {
        val viewModel = EditShiftViewModel(InMemoryShiftRepository(), machines, FakeSettingsRepository(), clock)

        viewModel.start(day)

        assertFalse(viewModel.editing.isExisting)
        assertEquals(day, viewModel.editing.draft.date)
    }

    @Test
    fun `a stored shift is loaded for editing`() = runTest {
        val stored = Shift(day, ShiftType.NIGHT, machines = listOf(MachineAssignment(rtg12)), comments = "σχόλιο")
        val viewModel = EditShiftViewModel(InMemoryShiftRepository(listOf(stored)), machines, FakeSettingsRepository(), clock)

        viewModel.start(day)

        assertTrue(viewModel.editing.isExisting)
        assertEquals(stored, viewModel.editing.draft.toShift())
    }

    @Test
    fun `saving stores the shift and finishes`() = runTest {
        val shifts = InMemoryShiftRepository()
        val viewModel = EditShiftViewModel(shifts, machines, FakeSettingsRepository(), clock)
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
        val viewModel = EditShiftViewModel(shifts, machines, FakeSettingsRepository(), clock)
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
        val viewModel = EditShiftViewModel(shifts, machines, FakeSettingsRepository(), clock)
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
        val viewModel = EditShiftViewModel(shifts, machines, FakeSettingsRepository(), clock)
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
        val viewModel = EditShiftViewModel(shifts, machines, FakeSettingsRepository(), clock)
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
        val viewModel = EditShiftViewModel(InMemoryShiftRepository(), machines, FakeSettingsRepository(), clock)
        viewModel.start(day)

        viewModel.onDeleteRequested()

        assertFalse(viewModel.editing.confirmDelete)
    }

    @Test
    fun `start runs only once, so rotation does not reload over the edits`() = runTest {
        val viewModel = EditShiftViewModel(InMemoryShiftRepository(), machines, FakeSettingsRepository(), clock)
        viewModel.start(day)
        viewModel.onTypeSelected(ShiftType.NIGHT)

        viewModel.start(day)

        assertEquals(ShiftType.NIGHT, viewModel.editing.draft.type)
    }

    @Test
    fun `a machine missing from the picker is added and put in the shift`() = runTest {
        val viewModel = EditShiftViewModel(InMemoryShiftRepository(), machines, FakeSettingsRepository(), clock)
        viewModel.start(day)
        viewModel.onTypeSelected(ShiftType.MORNING)

        viewModel.onMachineQuickAdded(MachineCategory.RTG, " 46 ")

        assertEquals("RTG 46", viewModel.editing.draft.machines.single().machine.label)
        assertTrue(machines.stored.any { it.label == "RTG 46" && it.active })
    }

    @Test
    fun `quick add ignores a blank number`() = runTest {
        val viewModel = EditShiftViewModel(InMemoryShiftRepository(), machines, FakeSettingsRepository(), clock)
        viewModel.start(day)

        viewModel.onMachineQuickAdded(MachineCategory.RTG, "  ")

        assertTrue(viewModel.editing.draft.machines.isEmpty())
        assertEquals(1, machines.stored.size)
    }

    @Test
    fun `a new shift today starts with the shift running now`() = runTest {
        val viewModel = EditShiftViewModel(InMemoryShiftRepository(), machines, FakeSettingsRepository(), clock)

        viewModel.start(day)

        assertTrue(viewModel.editing.typeChosen)
        assertEquals(ShiftType.MORNING, viewModel.editing.draft.type)
    }

    @Test
    fun `a new shift on a predicted day starts with the prediction`() = runTest {
        val anchor = RotationAnchor(LocalDate.of(2024, 8, 10), ShiftType.AFTERNOON)
        val settings = FakeSettingsRepository(AppSettings(rotationAnchor = anchor))
        val viewModel = EditShiftViewModel(InMemoryShiftRepository(), machines, settings, clock)

        viewModel.start(day.plusDays(5))

        assertEquals(RotationSchedule.typeOn(anchor, day.plusDays(5)), viewModel.editing.draft.type)
    }

    @Test
    fun `outside the schedule nothing is suggested, the screen says so and offers every type`() = runTest {
        // An afternoon-only operator adding today's shift at 10:00 in the morning
        val settings = FakeSettingsRepository(AppSettings(workSchedule = WorkSchedule.AFTERNOON_ONLY))
        val shifts = InMemoryShiftRepository()
        val viewModel = EditShiftViewModel(shifts, machines, settings, clock)

        viewModel.start(day)

        assertFalse(viewModel.editing.typeChosen)
        assertEquals(ShiftType.MORNING, viewModel.editing.outsideSchedule)
        assertEquals(ShiftType.entries, viewModel.editing.offeredTypes)

        // Nothing is saved before a type is picked
        viewModel.onSave()
        assertTrue(shifts.stored.isEmpty())

        viewModel.onTypeSelected(ShiftType.MORNING)
        viewModel.onSave()
        assertEquals(ShiftType.MORNING, shifts.stored.getValue(day).type)
    }

    @Test
    fun `a limited schedule offers only its working types until asked for more`() = runTest {
        val settings = FakeSettingsRepository(AppSettings(workSchedule = WorkSchedule.MORNING_AFTERNOON))
        val viewModel = EditShiftViewModel(InMemoryShiftRepository(), machines, settings, clock)
        viewModel.start(day.plusDays(3))

        assertFalse(ShiftType.NIGHT in viewModel.editing.offeredTypes)
        assertTrue(ShiftType.DAY_OFF in viewModel.editing.offeredTypes)
        assertTrue(viewModel.editing.hasHiddenTypes)

        viewModel.onShowAllTypes()

        assertTrue(ShiftType.NIGHT in viewModel.editing.offeredTypes)
        assertFalse(viewModel.editing.hasHiddenTypes)
    }

    @Test
    fun `a stored shift outside the schedule stays editable`() = runTest {
        val settings = FakeSettingsRepository(AppSettings(workSchedule = WorkSchedule.MORNING_ONLY))
        val viewModel = EditShiftViewModel(InMemoryShiftRepository(listOf(Shift(day, ShiftType.NIGHT))), machines, settings, clock)

        viewModel.start(day)

        assertTrue(ShiftType.NIGHT in viewModel.editing.offeredTypes)
    }

    @Test
    fun `the picker offers the user's categories, all without a profile`() = runTest {
        val settings = FakeSettingsRepository()
        val viewModel = EditShiftViewModel(InMemoryShiftRepository(), machines, settings, clock)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.machineCategories.collect() }
        assertEquals(MachineCategory.entries, viewModel.machineCategories.value)

        settings.setWorker(WorkerProfile(role = WorkerRole.DRIVER, categories = setOf(MachineCategory.TRUCKS)))

        assertEquals(listOf(MachineCategory.TRUCKS), viewModel.machineCategories.value)
    }
}
