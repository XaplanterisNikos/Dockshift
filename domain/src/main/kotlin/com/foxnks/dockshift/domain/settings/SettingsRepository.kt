package com.foxnks.dockshift.domain.settings

import com.foxnks.dockshift.domain.holidays.GreekHoliday
import com.foxnks.dockshift.domain.rotation.RotationAnchor
import com.foxnks.dockshift.domain.worker.WorkerProfile
import java.time.LocalDate
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

    /**
     * Persists the annual leave entitlement of one year.
     *
     * @param year the year.
     * @param days the days, within `LeaveRules.ENTITLEMENT_RANGE`.
     */
    suspend fun setLeaveEntitlement(year: Int, days: Int)

    /**
     * Persists the holidays on which the port is closed.
     *
     * @param holidays the new value of [AppSettings.portClosedHolidays].
     */
    suspend fun setPortClosedHolidays(holidays: Set<GreekHoliday>)

    /**
     * Records the day of a successful backup.
     *
     * @param date the new value of [AppSettings.lastBackup].
     */
    suspend fun setLastBackup(date: LocalDate)

    /**
     * Turns the backup reminder on or off.
     *
     * @param enabled the new value of [AppSettings.backupReminder].
     */
    suspend fun setBackupReminder(enabled: Boolean)

    /**
     * Persists the user's profile. Its permanent id is never changed here: the stored
     * one is kept whatever [worker] carries.
     *
     * @param worker the new value of [AppSettings.worker].
     */
    suspend fun setWorker(worker: WorkerProfile)

    /**
     * Stores the profile's permanent id, only if none is stored yet (first start).
     *
     * @param id a new id (see `WorkerForm.newId`); ignored when one exists.
     */
    suspend fun ensureWorkerId(id: String)

    /** Records that the first-run "fill in your profile?" question was answered. */
    suspend fun setWorkerPromptAsked()

    /**
     * Replaces every setting at once, when a backup is restored.
     *
     * @param settings the settings of the backup.
     */
    suspend fun restore(settings: AppSettings)
}
