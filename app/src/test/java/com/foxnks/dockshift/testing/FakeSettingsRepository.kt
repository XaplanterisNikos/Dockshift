package com.foxnks.dockshift.testing

import com.foxnks.dockshift.domain.settings.AppSettings
import com.foxnks.dockshift.domain.settings.SettingsRepository
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
}
