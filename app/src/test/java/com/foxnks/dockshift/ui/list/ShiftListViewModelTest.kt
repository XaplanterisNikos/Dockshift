package com.foxnks.dockshift.ui.list

import com.foxnks.dockshift.domain.holidays.GreekHoliday
import com.foxnks.dockshift.domain.shift.Shift
import com.foxnks.dockshift.domain.shift.ShiftType
import com.foxnks.dockshift.testing.FakeSettingsRepository
import com.foxnks.dockshift.testing.InMemoryShiftRepository
import com.foxnks.dockshift.testing.MainDispatcherRule
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/** Year/month filters of [ShiftListViewModel]. */
@OptIn(ExperimentalCoroutinesApi::class) // UnconfinedTestDispatcher is still experimental
class ShiftListViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    /** The clock says October 2026. */
    private val clock = Clock.fixed(LocalDate.of(2026, 10, 13).atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC)

    private val shifts = InMemoryShiftRepository(
        listOf(
            Shift(LocalDate.of(2024, 3, 1), ShiftType.MORNING),
            Shift(LocalDate.of(2026, 9, 30), ShiftType.NIGHT),
            Shift(LocalDate.of(2026, 10, 1), ShiftType.DAY_OFF),
            Shift(LocalDate.of(2026, 10, 2), ShiftType.AFTERNOON),
        ),
    )

    /** Collects the state like the screen does, so WhileSubscribed runs. */
    private fun TestScope.observe(viewModel: ShiftListViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
    }

    /** The current state, expected to be loaded. */
    private val ShiftListViewModel.ready get() = uiState.value as ShiftListUiState.Ready

    @Test
    fun `opens on the current year, newest first, grouped by month`() = runTest {
        val viewModel = ShiftListViewModel(shifts, FakeSettingsRepository(), clock)
        observe(viewModel)

        val state = viewModel.ready
        assertEquals(listOf(2026, 2024), state.years)
        assertEquals(2026, state.year)
        assertNull(state.month)
        assertEquals(listOf(YearMonth.of(2026, 10), YearMonth.of(2026, 9)), state.groups.map { it.month })
        assertEquals(listOf(2, 1), state.groups.first().shifts.map { it.date.dayOfMonth })
    }

    @Test
    fun `month filter keeps only that month`() = runTest {
        val viewModel = ShiftListViewModel(shifts, FakeSettingsRepository(), clock)
        observe(viewModel)

        viewModel.onMonthSelected(9)

        assertEquals(listOf(LocalDate.of(2026, 9, 30)), viewModel.ready.groups.flatMap { it.shifts }.map { it.date })
    }

    @Test
    fun `changing year shows that year and resets the month`() = runTest {
        val viewModel = ShiftListViewModel(shifts, FakeSettingsRepository(), clock)
        observe(viewModel)
        viewModel.onMonthSelected(10)

        viewModel.onYearSelected(2024)

        assertEquals(2024, viewModel.ready.year)
        assertNull(viewModel.ready.month)
        assertEquals(1, viewModel.ready.groups.flatMap { it.shifts }.size)
    }

    @Test
    fun `the current year is offered even with no shifts`() = runTest {
        val viewModel = ShiftListViewModel(InMemoryShiftRepository(), FakeSettingsRepository(), clock)
        observe(viewModel)

        assertEquals(listOf(2026), viewModel.ready.years)
        assertEquals(emptyList<MonthGroup>(), viewModel.ready.groups)
    }

    @Test
    fun `public holidays of the shown period come with the list`() = runTest {
        val viewModel = ShiftListViewModel(shifts, FakeSettingsRepository(), clock)
        observe(viewModel)

        viewModel.onMonthSelected(10)

        assertEquals(mapOf(LocalDate.of(2026, 10, 28) to GreekHoliday.OHI_DAY), viewModel.ready.holidays)
    }
}
