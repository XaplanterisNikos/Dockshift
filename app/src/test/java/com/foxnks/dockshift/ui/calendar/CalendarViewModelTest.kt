package com.foxnks.dockshift.ui.calendar

import com.foxnks.dockshift.domain.calendar.DayStatus
import com.foxnks.dockshift.domain.holidays.GreekHoliday
import com.foxnks.dockshift.domain.rotation.RotationAnchor
import com.foxnks.dockshift.domain.rotation.RotationSchedule
import com.foxnks.dockshift.domain.settings.AppSettings
import com.foxnks.dockshift.domain.settings.CalendarView
import com.foxnks.dockshift.domain.settings.WorkSchedule
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
import org.junit.Assert.assertNull
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
    fun `public holidays are marked when the setting is on`() = runTest {
        val viewModel = viewModel()
        observe(viewModel)

        val october = viewModel.uiState.value.visible!!
        assertEquals(GreekHoliday.OHI_DAY, october.day(LocalDate.of(2026, 10, 28))?.holiday)
        assertEquals(listOf(LocalDate.of(2026, 10, 28) to GreekHoliday.OHI_DAY), october.summary.holidays)
    }

    @Test
    fun `turning holidays off removes the marks at once`() = runTest {
        val settings = FakeSettingsRepository()
        val viewModel = viewModel(settings = settings)
        observe(viewModel)

        settings.setShowHolidays(false)

        assertNull(viewModel.uiState.value.visible!!.day(LocalDate.of(2026, 10, 28))?.holiday)
    }

    @Test
    fun `holidays stay hidden when the setting is off from the start`() = runTest {
        val viewModel = viewModel(settings = FakeSettingsRepository(AppSettings(showHolidays = false)))
        observe(viewModel)

        assertTrue(viewModel.uiState.value.visible!!.days.all { it.holiday == null })
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

    @Test
    fun `with a rotation place, the days from today on are predicted in gold`() = runTest {
        val anchor = RotationAnchor(LocalDate.of(2024, 8, 10), ShiftType.AFTERNOON)
        val viewModel = viewModel(
            InMemoryShiftRepository(listOf(Shift(LocalDate.of(2026, 10, 20), ShiftType.ANNUAL_LEAVE))),
            FakeSettingsRepository(AppSettings(rotationAnchor = anchor)),
        )
        observe(viewModel)

        val page = viewModel.uiState.value.visible!!
        assertEquals(DayStatus.Forecast(RotationSchedule.typeOn(anchor, today)), page.day(today)?.status)
        // Yesterday is a gap to fill, not a prediction; a stored day stays as stored
        assertEquals(DayStatus.Empty, page.day(today.minusDays(1))?.status)
        assertTrue(page.day(LocalDate.of(2026, 10, 20))?.status is DayStatus.Recorded)
    }

    @Test
    fun `recording the month's predictions saves them as shifts and keeps stored days`() = runTest {
        val anchor = RotationAnchor(LocalDate.of(2024, 8, 10), ShiftType.AFTERNOON)
        val shifts = InMemoryShiftRepository(listOf(Shift(LocalDate.of(2026, 10, 20), ShiftType.ANNUAL_LEAVE)))
        val viewModel = viewModel(shifts, FakeSettingsRepository(AppSettings(rotationAnchor = anchor)))
        observe(viewModel)

        viewModel.onRecordForecasts()

        // 13–31 October: 19 days, one of them already stored
        assertEquals(19, shifts.stored.keys.count { it.month.value == 10 })
        assertEquals(ShiftType.ANNUAL_LEAVE, shifts.stored.getValue(LocalDate.of(2026, 10, 20)).type)
        assertEquals(RotationSchedule.typeOn(anchor, LocalDate.of(2026, 10, 31)), shifts.stored.getValue(LocalDate.of(2026, 10, 31)).type)
        // Now recorded, so no longer predicted
        assertEquals(0, viewModel.uiState.value.visible!!.summary.forecastDays)
    }

    @Test
    fun `a limited schedule predicts nothing`() = runTest {
        val anchor = RotationAnchor(LocalDate.of(2024, 8, 10), ShiftType.AFTERNOON)
        val settings = FakeSettingsRepository(AppSettings(rotationAnchor = anchor, workSchedule = WorkSchedule.MORNING_ONLY))
        val viewModel = viewModel(settings = settings)
        observe(viewModel)

        assertEquals(0, viewModel.uiState.value.visible!!.summary.forecastDays)
    }
}
