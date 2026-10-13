package com.foxnks.dockshift.ui.settings

import com.foxnks.dockshift.domain.settings.AppSettings
import com.foxnks.dockshift.domain.settings.ThemeMode
import com.foxnks.dockshift.domain.shift.Shift
import com.foxnks.dockshift.domain.shift.ShiftType
import com.foxnks.dockshift.testing.FakeSettingsRepository
import com.foxnks.dockshift.testing.InMemoryShiftRepository
import java.time.LocalDate
import com.foxnks.dockshift.testing.MainDispatcherRule
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** [SettingsViewModel] against an in-memory settings store. */
class SettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    /** Collects the state like the screen does, so WhileSubscribed runs. */
    private fun TestScope.observe(viewModel: SettingsViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
    }

    @Test
    fun `state is Loading before anyone observes it`() {
        assertEquals(SettingsUiState.Loading, SettingsViewModel(FakeSettingsRepository(), InMemoryShiftRepository()).uiState.value)
    }

    @Test
    fun `fresh install shows the defaults`() = runTest {
        val viewModel = SettingsViewModel(FakeSettingsRepository(), InMemoryShiftRepository())
        observe(viewModel)

        assertEquals(
            SettingsUiState.Ready(themeMode = ThemeMode.SYSTEM, dynamicColor = true, rotationEnabled = false),
            viewModel.uiState.value,
        )
    }

    @Test
    fun `shows saved values`() = runTest {
        val saved = AppSettings(themeMode = ThemeMode.DARK, dynamicColor = false, rotationEnabled = true)
        val viewModel = SettingsViewModel(FakeSettingsRepository(saved), InMemoryShiftRepository())
        observe(viewModel)

        assertEquals(
            SettingsUiState.Ready(themeMode = ThemeMode.DARK, dynamicColor = false, rotationEnabled = true),
            viewModel.uiState.value,
        )
    }

    @Test
    fun `every change is saved immediately`() = runTest {
        val repository = FakeSettingsRepository()
        val viewModel = SettingsViewModel(repository, InMemoryShiftRepository())
        observe(viewModel)

        viewModel.onThemeModeSelected(ThemeMode.LIGHT)
        viewModel.onDynamicColorToggled(false)
        viewModel.onRotationToggled(true)

        // Saved in the store...
        assertEquals(ThemeMode.LIGHT, repository.current.themeMode)
        assertFalse(repository.current.dynamicColor)
        assertTrue(repository.current.rotationEnabled)
        // ...and shown on screen, through the store
        assertEquals(
            SettingsUiState.Ready(themeMode = ThemeMode.LIGHT, dynamicColor = false, rotationEnabled = true),
            viewModel.uiState.value,
        )
    }

    @Test
    fun `the holidays switch is saved and shown`() = runTest {
        val repository = FakeSettingsRepository()
        val viewModel = SettingsViewModel(repository, InMemoryShiftRepository())
        observe(viewModel)
        assertTrue((viewModel.uiState.value as SettingsUiState.Ready).showHolidays)

        viewModel.onShowHolidaysToggled(false)

        assertFalse(repository.current.showHolidays)
        assertFalse((viewModel.uiState.value as SettingsUiState.Ready).showHolidays)
    }

    @Test
    fun `delete all removes every shift and the count follows`() = runTest {
        val shifts = InMemoryShiftRepository(
            listOf(Shift(LocalDate.of(2026, 10, 13), ShiftType.MORNING), Shift(LocalDate.of(2026, 10, 14), ShiftType.DAY_OFF)),
        )
        val viewModel = SettingsViewModel(FakeSettingsRepository(), shifts)
        observe(viewModel)
        assertEquals(2, (viewModel.uiState.value as SettingsUiState.Ready).storedShifts)

        viewModel.onDeleteAllShifts()

        assertTrue(shifts.stored.isEmpty())
        assertEquals(0, (viewModel.uiState.value as SettingsUiState.Ready).storedShifts)
    }
}
