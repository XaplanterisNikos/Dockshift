package com.foxnks.dockshift.ui

import com.foxnks.dockshift.domain.settings.AppSettings
import com.foxnks.dockshift.domain.settings.ThemeMode
import com.foxnks.dockshift.testing.FakeSettingsRepository
import com.foxnks.dockshift.testing.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** The app-wide [Appearance] of [MainViewModel]. */
class MainViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `saved appearance is applied without any screen observing`() = runTest {
        // Eager: the theme must be ready before the first screen draws
        val viewModel = MainViewModel(FakeSettingsRepository(AppSettings(themeMode = ThemeMode.DARK, dynamicColor = false)))

        assertEquals(Appearance(themeMode = ThemeMode.DARK, dynamicColor = false), viewModel.appearance.value)
    }

    @Test
    fun `a change in Settings re-themes the app`() = runTest {
        val repository = FakeSettingsRepository()
        val viewModel = MainViewModel(repository)

        repository.setThemeMode(ThemeMode.LIGHT)

        assertEquals(ThemeMode.LIGHT, viewModel.appearance.value.themeMode)
    }
}
