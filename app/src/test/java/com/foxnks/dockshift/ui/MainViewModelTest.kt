package com.foxnks.dockshift.ui

import com.foxnks.dockshift.domain.settings.AppSettings
import com.foxnks.dockshift.domain.settings.SettingsRepository
import com.foxnks.dockshift.domain.settings.ThemeMode
import com.foxnks.dockshift.testing.FakeSettingsRepository
import com.foxnks.dockshift.testing.MainDispatcherRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    @Test
    fun `the splash screen waits until the settings are read`() = runTest {
        // Settings that are not read yet: nothing emitted until the test says so
        val file = MutableSharedFlow<AppSettings>()
        val repository = object : SettingsRepository by FakeSettingsRepository() {
            override val settings: Flow<AppSettings> = file
        }
        val viewModel = MainViewModel(repository)
        assertFalse(viewModel.ready.value)

        file.emit(AppSettings(themeMode = ThemeMode.DARK))

        assertTrue(viewModel.ready.value)
        assertEquals(ThemeMode.DARK, viewModel.appearance.value.themeMode)
    }

    @Test
    fun `the profile gets a permanent id once`() = runTest {
        val repository = FakeSettingsRepository()
        MainViewModel(repository)
        val id = repository.current.worker.id
        assertTrue(id.isNotBlank())

        // Next start: the same id stays
        MainViewModel(repository)
        assertEquals(id, repository.current.worker.id)
    }
}
