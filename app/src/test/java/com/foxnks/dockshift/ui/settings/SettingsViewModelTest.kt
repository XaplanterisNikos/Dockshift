package com.foxnks.dockshift.ui.settings

import com.foxnks.dockshift.domain.rotation.RotationAnchor
import com.foxnks.dockshift.domain.rotation.RotationSchedule
import com.foxnks.dockshift.domain.settings.AppSettings
import com.foxnks.dockshift.domain.settings.ThemeMode
import com.foxnks.dockshift.domain.settings.WorkSchedule
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** [SettingsViewModel] against an in-memory settings store. */
class SettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val today = LocalDate.of(2026, 10, 13)

    /** The clock says 13 October 2026. */
    private val clock = Clock.fixed(today.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC)

    /** The sample operator of the rotation: four-day rest from 10/8/2024, then afternoons. */
    private val sample = RotationAnchor(LocalDate.of(2024, 8, 10), ShiftType.AFTERNOON)

    private fun viewModel(
        settings: FakeSettingsRepository = FakeSettingsRepository(),
        shifts: InMemoryShiftRepository = InMemoryShiftRepository(),
    ) = SettingsViewModel(settings, shifts, clock)

    /** Collects the state like the screen does, so WhileSubscribed runs. */
    private fun TestScope.observe(viewModel: SettingsViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
    }

    /** The current state, expected to be loaded. */
    private val SettingsViewModel.ready get() = uiState.value as SettingsUiState.Ready

    @Test
    fun `state is Loading before anyone observes it`() {
        assertEquals(SettingsUiState.Loading, viewModel().uiState.value)
    }

    @Test
    fun `fresh install shows the defaults`() = runTest {
        val viewModel = viewModel()
        observe(viewModel)

        assertEquals(
            SettingsUiState.Ready(themeMode = ThemeMode.SYSTEM, dynamicColor = true, today = today),
            viewModel.uiState.value,
        )
    }

    @Test
    fun `shows saved values`() = runTest {
        val saved = AppSettings(themeMode = ThemeMode.DARK, dynamicColor = false, workSchedule = WorkSchedule.NIGHT_ONLY)
        val viewModel = viewModel(FakeSettingsRepository(saved))
        observe(viewModel)

        assertEquals(ThemeMode.DARK, viewModel.ready.themeMode)
        assertFalse(viewModel.ready.dynamicColor)
        assertEquals(WorkSchedule.NIGHT_ONLY, viewModel.ready.workSchedule)
    }

    @Test
    fun `every change is saved immediately`() = runTest {
        val repository = FakeSettingsRepository()
        val viewModel = viewModel(repository)
        observe(viewModel)

        viewModel.onThemeModeSelected(ThemeMode.LIGHT)
        viewModel.onDynamicColorToggled(false)
        viewModel.onWorkScheduleSelected(WorkSchedule.MORNING_AFTERNOON)

        // Saved in the store...
        assertEquals(ThemeMode.LIGHT, repository.current.themeMode)
        assertFalse(repository.current.dynamicColor)
        assertEquals(WorkSchedule.MORNING_AFTERNOON, repository.current.workSchedule)
        // ...and shown on screen, through the store
        assertEquals(ThemeMode.LIGHT, viewModel.ready.themeMode)
        assertEquals(WorkSchedule.MORNING_AFTERNOON, viewModel.ready.workSchedule)
    }

    @Test
    fun `the holidays switch is saved and shown`() = runTest {
        val repository = FakeSettingsRepository()
        val viewModel = viewModel(repository)
        observe(viewModel)
        assertTrue(viewModel.ready.showHolidays)

        viewModel.onShowHolidaysToggled(false)

        assertFalse(repository.current.showHolidays)
        assertFalse(viewModel.ready.showHolidays)
    }

    @Test
    fun `the rotation place is saved, shown as the latest four-day rest, and can be removed`() = runTest {
        val repository = FakeSettingsRepository()
        val viewModel = viewModel(repository)
        observe(viewModel)

        viewModel.onRotationAnchorSaved(sample.cycleStart, sample.firstType)

        assertEquals(sample, repository.current.rotationAnchor)
        // Setting the place answers the one-time question too
        assertTrue(repository.current.rotationPromptAnswered)
        assertEquals(RotationSchedule.latestAnchor(sample, today), viewModel.ready.rotationAnchor)

        viewModel.onRotationAnchorCleared()

        assertNull(repository.current.rotationAnchor)
        assertNull(viewModel.ready.rotationAnchor)
    }

    @Test
    fun `the place is found from the recent shifts`() = runTest {
        // Three weeks of the sample operator, ending yesterday
        val shifts = (1..21L).map { i ->
            val day = today.minusDays(i)
            Shift(day, RotationSchedule.typeOn(sample, day))
        }
        val viewModel = viewModel(shifts = InMemoryShiftRepository(shifts))
        observe(viewModel)

        assertEquals(RotationSchedule.latestAnchor(sample, today), viewModel.ready.detectedAnchor)
    }
}
