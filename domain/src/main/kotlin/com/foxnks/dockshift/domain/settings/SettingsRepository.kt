package com.foxnks.dockshift.domain.settings

import kotlinx.coroutines.flow.Flow

/**
 * Read and change the user's [AppSettings].
 *
 * The domain only defines this contract; how settings are stored (DataStore today)
 * is decided in the :app module. ViewModels depend on this interface, never on the
 * storage, so tests can swap in a fake implementation.
 */
interface SettingsRepository {

    /**
     * The current settings, emitted again every time they change.
     * Never fails: unreadable storage results in [AppSettings] defaults.
     */
    val settings: Flow<AppSettings>

    /**
     * Turns the rotating schedule prediction on or off and persists the choice.
     *
     * @param enabled the new value of [AppSettings.rotationEnabled].
     */
    suspend fun setRotationEnabled(enabled: Boolean)

    /** Records that the first-run v1 import question was answered (yes or no). */
    suspend fun setLegacyImportAsked()
}
