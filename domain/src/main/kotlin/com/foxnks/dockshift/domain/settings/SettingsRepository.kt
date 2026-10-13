package com.foxnks.dockshift.domain.settings

import com.foxnks.dockshift.domain.rotation.RotationAnchor
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
     * Persists which shifts the operator works.
     *
     * @param schedule the new value of [AppSettings.workSchedule].
     */
    suspend fun setWorkSchedule(schedule: WorkSchedule)

    /**
     * Persists the operator's place in the rotating schedule, or removes it.
     *
     * @param anchor the new value of [AppSettings.rotationAnchor]; null removes it.
     */
    suspend fun setRotationAnchor(anchor: RotationAnchor?)

    /** Records that the one-time "use the rotating schedule?" question was answered. */
    suspend fun setRotationPromptAnswered()

    /** Records that the first-run v1 import question was answered (yes or no). */
    suspend fun setLegacyImportAsked()

    /**
     * Persists the light/dark choice.
     *
     * @param mode the new value of [AppSettings.themeMode].
     */
    suspend fun setThemeMode(mode: ThemeMode)

    /**
     * Turns wallpaper-based app colors on or off and persists the choice.
     *
     * @param enabled the new value of [AppSettings.dynamicColor].
     */
    suspend fun setDynamicColor(enabled: Boolean)

    /**
     * Persists what the calendar shows beside the month, for one orientation.
     *
     * @param landscape true for [AppSettings.calendarViewLandscape], false for
     * [AppSettings.calendarViewPortrait].
     * @param view the new value.
     */
    suspend fun setCalendarView(landscape: Boolean, view: CalendarView)

    /**
     * Turns the public holiday marks on or off and persists the choice.
     *
     * @param enabled the new value of [AppSettings.showHolidays].
     */
    suspend fun setShowHolidays(enabled: Boolean)
}
