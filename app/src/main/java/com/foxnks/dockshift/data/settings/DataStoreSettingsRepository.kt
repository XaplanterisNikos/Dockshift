package com.foxnks.dockshift.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import com.foxnks.dockshift.domain.settings.AppSettings
import com.foxnks.dockshift.domain.settings.SettingsRepository
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/**
 * [SettingsRepository] backed by Preferences DataStore.
 *
 * Only this class knows the storage keys; the rest of the app sees [AppSettings].
 *
 * @param dataStore the app's settings file, provided by Hilt (see `DataStoreModule`).
 */
class DataStoreSettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {

    override val settings: Flow<AppSettings> = dataStore.data
        .catch { error ->
            // A corrupted or unreadable file must not crash the app: fall back to
            // the defaults. Any other error is a bug and is rethrown.
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { preferences ->
            // A key that was never saved is missing: use the domain's default for it
            val defaults = AppSettings()
            AppSettings(
                rotationEnabled = preferences[Keys.ROTATION_ENABLED] ?: defaults.rotationEnabled,
            )
        }

    override suspend fun setRotationEnabled(enabled: Boolean) {
        // edit() writes atomically and on a background thread
        dataStore.edit { preferences -> preferences[Keys.ROTATION_ENABLED] = enabled }
    }

    /**
     * Storage keys. Their names are part of the saved file: renaming one would
     * silently reset that setting for every user, so they must never change.
     */
    private object Keys {
        val ROTATION_ENABLED = booleanPreferencesKey("rotation_enabled")
    }
}
