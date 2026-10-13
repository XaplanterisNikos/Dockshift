package com.foxnks.dockshift.ui.calendar

import com.foxnks.dockshift.domain.calendar.DayStatus
import com.foxnks.dockshift.domain.settings.CalendarView
import com.foxnks.dockshift.domain.shift.Shift
import com.foxnks.dockshift.domain.shift.ShiftType
import com.foxnks.dockshift.testing.FakeSettingsRepository
import com.foxnks.dockshift.testing.InMemoryShiftRepository
import com.foxnks.dockshift.testing.MainDispatcherRule
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** [CalendarViewModel] on a fixed date, against shifts and settings in memory. */
class CalendarViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val zone = ZoneId.of("Europe/Athens")
    private val today = LocalDate.of(2026, 10, 13)
    private val october = YearMonth.of(2026, 10)
    private val december = YearMonth.of(2026, 12)

    /** A clock stopped at noon of [date] in Athens. */
    private fun clockOn(date: LocalDate): Clock = Clock.fixed(date.atTime(12, 0).atZone(zone).toInstant(), zone)

    private fun viewModel(
        shifts: InMemoryShiftRepository = InMemoryShiftRepository(),
        settings: FakeSettingsRepository = FakeSettingsRepository(),
    ) = CalendarViewModel(shifts, settings, clockOn(today))

    /** Collects the state like the screen does, so WhileSubscribed runs. */
    private fun TestScope.observe(viewModel: CalendarViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
    }

    @Test
    fun `opens on the current month with today selected`() = runTest {
        val viewModel = viewModel(InMemoryShiftRepository(listOf(Shift(today, ShiftType.NIGHT))))
        observe(viewModel)

        val state = viewModel.uiState.value
        assertEquals(today, state.today)
        assertEquals(october, state.visibleMonth)
        assertEquals(today, state.selectedDate)
        assertEquals(ShiftType.NIGHT, (state.selectedDay?.status as DayStatus.Recorded).shift.type)
    }

    @Test
    fun `the neighbour months are loaded too`() = runTest {
        val shifts = InMemoryShiftRepository(listOf(Shift(LocalDate.of(2026, 11, 20), ShiftType.MORNING)))
        val viewModel = viewModel(shifts)
        observe(viewModel)

        val state = viewModel.uiState.value
        assertEquals(setOf(october.minusMonths(1), october, october.plusMonths(1)), state.months.keys)
        // 20 November is not on October's page, but the next page is ready for the swipe
        val november = state.page(october.plusMonths(1))
        assertTrue(november.day(LocalDate.of(2026, 11, 20))?.status is DayStatus.Recorded)
    }

    @Test
    fun `swiping to another month reads it and selects its first day`() = runTest {
        val shifts = InMemoryShiftRepository(listOf(Shift(LocalDate.of(2026, 12, 25), ShiftType.PUBLIC_HOLIDAY)))
        val viewModel = viewModel(shifts)
        observe(viewModel)

        viewModel.onMonthShown(december)

        val state = viewModel.uiState.value
        assertEquals(december, state.visibleMonth)
        assertEquals(LocalDate.of(2026, 12, 1), state.selectedDate)
        assertEquals(mapOf(ShiftType.PUBLIC_HOLIDAY to 1), state.visible?.summary?.countByType)
    }

    @Test
    fun `back on the current month, today is selected again`() = runTest {
        val viewModel = viewModel()
        observe(viewModel)

        viewModel.onMonthShown(december)
        viewModel.onMonthShown(october)

        assertEquals(today, viewModel.uiState.value.selectedDate)
    }

    @Test
    fun `a faded day of the next month stays selected after the swipe`() = runTest {
        val viewModel = viewModel()
        observe(viewModel)

        // 2 November is shown faded on October's page
        viewModel.onDaySelected(LocalDate.of(2026, 11, 2))
        viewModel.onMonthShown(october.plusMonths(1))

        assertEquals(LocalDate.of(2026, 11, 2), viewModel.uiState.value.selectedDate)
    }

    @Test
    fun `the today button selects today`() = runTest {
        val viewModel = viewModel()
        observe(viewModel)
        viewModel.onDaySelected(LocalDate.of(2026, 10, 2))

        viewModel.onToday()

        assertEquals(today, viewModel.uiState.value.selectedDate)
    }

    @Test
    fun `a shift saved elsewhere appears at once`() = runTest {
        val shifts = InMemoryShiftRepository()
        val viewModel = viewModel(shifts)
        observe(viewModel)

        shifts.save(Shift(today, ShiftType.AFTERNOON, overtime = true))

        val state = viewModel.uiState.value
        assertTrue(state.selectedDay?.status is DayStatus.Recorded)
        assertEquals(1, state.visible?.summary?.overtimeShifts)
    }

    @Test
    fun `default panels are the summary upright and the day sideways`() = runTest {
        val viewModel = viewModel()
        observe(viewModel)

        assertEquals(CalendarView.SUMMARY, viewModel.uiState.value.view(landscape = false))
        assertEquals(CalendarView.DAY_DETAILS, viewModel.uiState.value.view(landscape = true))
    }

    @Test
    fun `the view button switches and remembers only its own orientation`() = runTest {
        val settings = FakeSettingsRepository()
        val viewModel = viewModel(settings = settings)
        observe(viewModel)

        viewModel.onToggleView(landscape = false)

        assertEquals(CalendarView.DAY_DETAILS, settings.current.calendarViewPortrait)
        assertEquals(CalendarView.DAY_DETAILS, settings.current.calendarViewLandscape)
        assertEquals(CalendarView.DAY_DETAILS, viewModel.uiState.value.view(landscape = false))

        viewModel.onToggleView(landscape = true)

        assertEquals(CalendarView.SUMMARY, settings.current.calendarViewLandscape)
        assertEquals(CalendarView.DAY_DETAILS, settings.current.calendarViewPortrait)
    }
}
