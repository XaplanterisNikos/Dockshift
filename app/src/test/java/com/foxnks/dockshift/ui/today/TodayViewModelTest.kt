package com.foxnks.dockshift.ui.today

import com.foxnks.dockshift.domain.shift.Shift
import com.foxnks.dockshift.domain.shift.ShiftType
import com.foxnks.dockshift.domain.holidays.GreekHoliday
import com.foxnks.dockshift.domain.settings.AppSettings
import com.foxnks.dockshift.testing.FakeSettingsRepository
import com.foxnks.dockshift.testing.InMemoryShiftRepository
import com.foxnks.dockshift.testing.MainDispatcherRule
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.flow.collect
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

/** [TodayViewModel] at fixed times, against shifts in memory. */
class TodayViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val zone = ZoneId.of("Europe/Athens")
    private val tuesday = LocalDate.of(2026, 10, 13)

    /** A clock stopped at [time] in Athens. */
    private fun clockAt(time: LocalDateTime): Clock = Clock.fixed(time.atZone(zone).toInstant(), zone)

    /** Collects the state like the screen does, so WhileSubscribed runs. */
    private fun TestScope.observe(viewModel: TodayViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
    }

    @Test
    fun `shows today's shift and the next seven days`() = runTest {
        val shifts = InMemoryShiftRepository(
            listOf(Shift(tuesday, ShiftType.MORNING), Shift(tuesday.plusDays(2), ShiftType.DAY_OFF)),
        )
        val viewModel = TodayViewModel(shifts, FakeSettingsRepository(), clockAt(tuesday.atTime(10, 0)))
        observe(viewModel)

        val state = viewModel.uiState.value as TodayUiState.Ready
        assertEquals(tuesday, state.currentDate)
        assertFalse(state.nightFromYesterday)
        assertEquals(ShiftType.MORNING, state.current?.type)
        assertEquals(7, state.upcoming.size)
        assertEquals(tuesday.plusDays(1), state.upcoming.first().date)
        assertNull(state.upcoming[0].shift)
        assertEquals(ShiftType.DAY_OFF, state.upcoming[1].shift?.type)
    }

    @Test
    fun `at 02 00 after a night shift, yesterday is still current`() = runTest {
        val shifts = InMemoryShiftRepository(listOf(Shift(tuesday, ShiftType.NIGHT)))
        val viewModel = TodayViewModel(shifts, FakeSettingsRepository(), clockAt(tuesday.plusDays(1).atTime(2, 0)))
        observe(viewModel)

        val state = viewModel.uiState.value as TodayUiState.Ready
        assertEquals(tuesday, state.currentDate)
        assertTrue(state.nightFromYesterday)
        assertEquals(ShiftType.NIGHT, state.current?.type)
    }

    @Test
    fun `a day without a shift shows nothing recorded`() = runTest {
        val viewModel = TodayViewModel(InMemoryShiftRepository(), FakeSettingsRepository(), clockAt(tuesday.atTime(10, 0)))
        observe(viewModel)

        assertNull((viewModel.uiState.value as TodayUiState.Ready).current)
    }

    @Test
    fun `a shift saved elsewhere appears at once`() = runTest {
        val shifts = InMemoryShiftRepository()
        val viewModel = TodayViewModel(shifts, FakeSettingsRepository(), clockAt(tuesday.atTime(10, 0)))
        observe(viewModel)

        shifts.save(Shift(tuesday, ShiftType.AFTERNOON))

        assertEquals(ShiftType.AFTERNOON, (viewModel.uiState.value as TodayUiState.Ready).current?.type)
    }

    @Test
    fun `public holidays are marked on today and the next days`() = runTest {
        // 28 October 2026 is a Wednesday: "today" on Tuesday 27th, the holiday is the first next day
        val viewModel = TodayViewModel(InMemoryShiftRepository(), FakeSettingsRepository(), clockAt(LocalDate.of(2026, 10, 27).atTime(10, 0)))
        observe(viewModel)

        val state = viewModel.uiState.value as TodayUiState.Ready
        assertNull(state.currentHoliday)
        assertEquals(GreekHoliday.OHI_DAY, state.upcoming.first().holiday)
    }

    @Test
    fun `no holiday marks when they are turned off`() = runTest {
        val settings = FakeSettingsRepository(AppSettings(showHolidays = false))
        val viewModel = TodayViewModel(InMemoryShiftRepository(), settings, clockAt(LocalDate.of(2026, 10, 28).atTime(10, 0)))
        observe(viewModel)

        assertNull((viewModel.uiState.value as TodayUiState.Ready).currentHoliday)
    }
}
