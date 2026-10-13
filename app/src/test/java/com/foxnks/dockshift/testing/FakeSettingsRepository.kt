package com.foxnks.dockshift.testing

import com.foxnks.dockshift.domain.settings.AppSettings
import com.foxnks.dockshift.domain.settings.SettingsRepository
import com.foxnks.dockshift.domain.settings.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * In-memory [SettingsRepository] for tests: no files, no Android, fully controllable.
 *
 * @param initial the settings the fake starts with.
 */
class FakeSettingsRepository(initial: AppSettings = AppSettings()) : SettingsRepository {

    private val state = MutableStateFlow(initial)

    /** The latest settings, readable directly so tests can assert what was saved. */
    val current: AppSettings get() = state.value

    override val settings: Flow<AppSettings> = state.asStateFlow()

    override suspend fun setRotationEnabled(enabled: Boolean) {
        state.update { it.copy(rotationEnabled = enabled) }
    }

    override suspend fun setLegacyImportAsked() {
        state.update { it.copy(legacyImportAsked = true) }
    }

    override suspend fun setThemeMode(mode: ThemeMode) {
        state.update { it.copy(themeMode = mode) }
    }

    override suspend fun setDynamicColor(enabled: Boolean) {
        state.update { it.copy(dynamicColor = enabled) }
    }
}
