package com.foxnks.dockshift.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.foxnks.dockshift.domain.holidays.GreekHoliday
import com.foxnks.dockshift.domain.rotation.RotationAnchor
import com.foxnks.dockshift.domain.settings.AppSettings
import com.foxnks.dockshift.domain.settings.CalendarView
import com.foxnks.dockshift.domain.settings.SettingsRepository
import com.foxnks.dockshift.domain.settings.ThemeMode
import com.foxnks.dockshift.domain.settings.WorkSchedule
import com.foxnks.dockshift.domain.shift.ShiftType
import java.time.LocalDate
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
                workSchedule = WorkSchedule.entries.firstOrNull { it.name == preferences[Keys.WORK_SCHEDULE] }
                    ?: defaults.workSchedule,
                rotationAnchor = anchorOf(preferences[Keys.ROTATION_ANCHOR]),
                rotationPromptAnswered = preferences[Keys.ROTATION_PROMPT_ANSWERED] ?: defaults.rotationPromptAnswered,
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
                leaveEntitlements = entitlementsOf(preferences[Keys.LEAVE_ENTITLEMENTS]),
                // Stored by name; unknown names (e.g. from a newer version) are skipped
                portClosedHolidays = preferences[Keys.PORT_CLOSED_HOLIDAYS].orEmpty()
                    .mapNotNull { name -> GreekHoliday.entries.firstOrNull { it.name == name } }
                    .toSet(),
            )
        }

    override suspend fun setWorkSchedule(schedule: WorkSchedule) {
        // edit() writes atomically and on a background thread
        dataStore.edit { preferences -> preferences[Keys.WORK_SCHEDULE] = schedule.name }
    }

    override suspend fun setRotationAnchor(anchor: RotationAnchor?) {
        dataStore.edit { preferences ->
            if (anchor == null) {
                preferences.remove(Keys.ROTATION_ANCHOR)
            } else {
                // Stored as "2024-08-10|AFTERNOON": readable, and stable across app versions
                preferences[Keys.ROTATION_ANCHOR] = "${anchor.cycleStart}$ANCHOR_SEPARATOR${anchor.firstType.name}"
            }
        }
    }

    override suspend fun setRotationPromptAnswered() {
        dataStore.edit { preferences -> preferences[Keys.ROTATION_PROMPT_ANSWERED] = true }
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

    override suspend fun setLeaveEntitlement(year: Int, days: Int) {
        dataStore.edit { preferences ->
            val all = entitlementsOf(preferences[Keys.LEAVE_ENTITLEMENTS]) + (year to days)
            // Stored as "2026:22,2027:24": readable and easy to extend
            preferences[Keys.LEAVE_ENTITLEMENTS] = all.entries.sortedBy { it.key }.joinToString(",") { "${it.key}:${it.value}" }
        }
    }

    override suspend fun setPortClosedHolidays(holidays: Set<GreekHoliday>) {
        dataStore.edit { preferences -> preferences[Keys.PORT_CLOSED_HOLIDAYS] = holidays.map { it.name }.toSet() }
    }

    /** "2026:22,2027:24" back to a map; unreadable parts are skipped. */
    private fun entitlementsOf(text: String?): Map<Int, Int> =
        text.orEmpty().split(",").mapNotNull { part ->
            val (year, days) = part.split(":").takeIf { it.size == 2 } ?: return@mapNotNull null
            val y = year.toIntOrNull() ?: return@mapNotNull null
            val d = days.toIntOrNull() ?: return@mapNotNull null
            y to d
        }.toMap()

    /**
     * A stored rotation place back to the value, or null when it was never saved or
     * cannot be read (then nothing is predicted until the user sets it again).
     */
    private fun anchorOf(text: String?): RotationAnchor? {
        val parts = text?.split(ANCHOR_SEPARATOR) ?: return null
        if (parts.size != 2) return null
        val date = runCatching { LocalDate.parse(parts[0]) }.getOrNull() ?: return null
        val type = ShiftType.entries.firstOrNull { it.name == parts[1] } ?: return null
        // The anchor itself rejects a type that is not a working shift
        return runCatching { RotationAnchor(date, type) }.getOrNull()
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
        // "rotation_enabled" (an on/off switch until #8) is retired; never reuse that name
        val WORK_SCHEDULE = stringPreferencesKey("work_schedule")
        val ROTATION_ANCHOR = stringPreferencesKey("rotation_anchor")
        val ROTATION_PROMPT_ANSWERED = booleanPreferencesKey("rotation_prompt_answered")
        val LEGACY_IMPORT_ASKED = booleanPreferencesKey("legacy_import_asked")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val CALENDAR_VIEW_PORTRAIT = stringPreferencesKey("calendar_view_portrait")
        val CALENDAR_VIEW_LANDSCAPE = stringPreferencesKey("calendar_view_landscape")
        val SHOW_HOLIDAYS = booleanPreferencesKey("show_holidays")
        val LEAVE_ENTITLEMENTS = stringPreferencesKey("leave_entitlements")
        val PORT_CLOSED_HOLIDAYS = stringSetPreferencesKey("port_closed_holidays")
    }

    private companion object {
        /** Between the date and the type of a stored rotation place. */
        const val ANCHOR_SEPARATOR = "|"
    }
}
