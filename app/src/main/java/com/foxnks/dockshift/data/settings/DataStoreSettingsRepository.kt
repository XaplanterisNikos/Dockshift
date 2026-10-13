package com.foxnks.dockshift.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.foxnks.dockshift.domain.settings.AppSettings
import com.foxnks.dockshift.domain.settings.CalendarView
import com.foxnks.dockshift.domain.settings.SettingsRepository
import com.foxnks.dockshift.domain.settings.ThemeMode
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
                legacyImportAsked = preferences[Keys.LEGACY_IMPORT_ASKED] ?: defaults.legacyImportAsked,
                // Stored by name; an unknown name (e.g. from a newer version) falls back to the default
                themeMode = ThemeMode.entries.firstOrNull { it.name == preferences[Keys.THEME_MODE] }
                    ?: defaults.themeMode,
                dynamicColor = preferences[Keys.DYNAMIC_COLOR] ?: defaults.dynamicColor,
                calendarViewPortrait = calendarView(preferences[Keys.CALENDAR_VIEW_PORTRAIT])
                    ?: defaults.calendarViewPortrait,
                calendarViewLandscape = calendarView(preferences[Keys.CALENDAR_VIEW_LANDSCAPE])
                    ?: defaults.calendarViewLandscape,
                showHolidays = preferences[Keys.SHOW_HOLIDAYS] ?: defaults.showHolidays,
            )
        }

    override suspend fun setRotationEnabled(enabled: Boolean) {
        // edit() writes atomically and on a background thread
        dataStore.edit { preferences -> preferences[Keys.ROTATION_ENABLED] = enabled }
    }

    override suspend fun setLegacyImportAsked() {
        dataStore.edit { preferences -> preferences[Keys.LEGACY_IMPORT_ASKED] = true }
    }

    override suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { preferences -> preferences[Keys.THEME_MODE] = mode.name }
    }

    override suspend fun setDynamicColor(enabled: Boolean) {
        dataStore.edit { preferences -> preferences[Keys.DYNAMIC_COLOR] = enabled }
    }

    override suspend fun setCalendarView(landscape: Boolean, view: CalendarView) {
        val key = if (landscape) Keys.CALENDAR_VIEW_LANDSCAPE else Keys.CALENDAR_VIEW_PORTRAIT
        dataStore.edit { preferences -> preferences[key] = view.name }
    }

    override suspend fun setShowHolidays(enabled: Boolean) {
        dataStore.edit { preferences -> preferences[Keys.SHOW_HOLIDAYS] = enabled }
    }

    /**
     * A stored [CalendarView] name back to the value, or null when it was never saved
     * or is unknown (e.g. written by a newer version), so the caller uses the default.
     */
    private fun calendarView(name: String?): CalendarView? =
        CalendarView.entries.firstOrNull { it.name == name }

    /**
     * Storage keys. Their names are part of the saved file: renaming one would
     * silently reset that setting for every user, so they must never change.
     */
    private object Keys {
        val ROTATION_ENABLED = booleanPreferencesKey("rotation_enabled")
        val LEGACY_IMPORT_ASKED = booleanPreferencesKey("legacy_import_asked")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val CALENDAR_VIEW_PORTRAIT = stringPreferencesKey("calendar_view_portrait")
        val CALENDAR_VIEW_LANDSCAPE = stringPreferencesKey("calendar_view_landscape")
        val SHOW_HOLIDAYS = booleanPreferencesKey("show_holidays")
    }
}
