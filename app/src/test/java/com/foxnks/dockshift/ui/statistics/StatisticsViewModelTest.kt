package com.foxnks.dockshift.ui.statistics

import com.foxnks.dockshift.domain.machine.Machine
import com.foxnks.dockshift.domain.machine.MachineCategory
import com.foxnks.dockshift.domain.settings.AppSettings
import com.foxnks.dockshift.domain.shift.MachineAssignment
import com.foxnks.dockshift.domain.shift.Shift
import com.foxnks.dockshift.domain.shift.ShiftType
import com.foxnks.dockshift.testing.FakeSettingsRepository
import com.foxnks.dockshift.testing.InMemoryShiftRepository
import com.foxnks.dockshift.testing.MainDispatcherRule
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Period filters and totals of [StatisticsViewModel]. */
class StatisticsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    /** The clock says October 2026. */
    private val clock = Clock.fixed(LocalDate.of(2026, 10, 13).atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC)

    private val rtg12 = Machine(1, MachineCategory.RTG, "12")

    private val shifts = InMemoryShiftRepository(
        listOf(
            Shift(LocalDate.of(2025, 5, 2), ShiftType.MORNING),
            Shift(LocalDate.of(2026, 9, 30), ShiftType.NIGHT, overtime = true, machines = listOf(MachineAssignment(rtg12))),
            Shift(LocalDate.of(2026, 10, 2), ShiftType.AFTERNOON, machines = listOf(MachineAssignment(rtg12))),
            // 28 October is a public holiday
            Shift(LocalDate.of(2026, 10, 28), ShiftType.MORNING),
            Shift(LocalDate.of(2026, 10, 29), ShiftType.DAY_OFF),
        ),
    )

    /** Collects the state like the screen does, so WhileSubscribed runs. */
    private fun TestScope.observe(viewModel: StatisticsViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
    }

    /** The current state, expected to be loaded. */
    private val StatisticsViewModel.ready get() = uiState.value as StatisticsUiState.Ready

    @Test
    fun `opens on the whole current year`() = runTest {
        val viewModel = StatisticsViewModel(shifts, FakeSettingsRepository(), clock)
        observe(viewModel)

        assertEquals(listOf(2026, 2025), viewModel.ready.years)
        assertEquals(2026, viewModel.ready.year)
        assertNull(viewModel.ready.month)
        assertEquals(3, viewModel.ready.statistics.workShifts)
        assertEquals(1, viewModel.ready.statistics.overtimeShifts)
        assertEquals(12, viewModel.ready.statistics.months.size)
        assertEquals(2, viewModel.ready.statistics.machines.single().shifts)
    }

    @Test
    fun `a month narrows the totals`() = runTest {
        val viewModel = StatisticsViewModel(shifts, FakeSettingsRepository(), clock)
        observe(viewModel)

        viewModel.onMonthSelected(10)

        assertEquals(2, viewModel.ready.statistics.workShifts)
        assertEquals(0, viewModel.ready.statistics.overtimeShifts)
        assertEquals(1, viewModel.ready.statistics.months.size)
    }

    @Test
    fun `another year resets the month`() = runTest {
        val viewModel = StatisticsViewModel(shifts, FakeSettingsRepository(), clock)
        observe(viewModel)
        viewModel.onMonthSelected(10)

        viewModel.onYearSelected(2025)

        assertNull(viewModel.ready.month)
        assertEquals(1, viewModel.ready.statistics.workShifts)
    }

    @Test
    fun `working on a public holiday is counted when holidays are on`() = runTest {
        val viewModel = StatisticsViewModel(shifts, FakeSettingsRepository(), clock)
        observe(viewModel)

        assertTrue(viewModel.ready.showHolidays)
        assertEquals(1, viewModel.ready.statistics.holidaysWorked)
    }

    @Test
    fun `holidays are not counted when they are turned off`() = runTest {
        val viewModel = StatisticsViewModel(shifts, FakeSettingsRepository(AppSettings(showHolidays = false)), clock)
        observe(viewModel)

        assertEquals(0, viewModel.ready.statistics.holidaysWorked)
    }

    @Test
    fun `a new shift updates the totals at once`() = runTest {
        val viewModel = StatisticsViewModel(shifts, FakeSettingsRepository(), clock)
        observe(viewModel)

        shifts.save(Shift(LocalDate.of(2026, 10, 30), ShiftType.NIGHT, overtime = true))

        assertEquals(4, viewModel.ready.statistics.workShifts)
        assertEquals(2, viewModel.ready.statistics.overtimeShifts)
    }
}
