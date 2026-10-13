package com.foxnks.dockshift.domain.settings

/**
 * The user's app-wide settings.
 *
 * Default values are the product decisions themselves, so they live here in the
 * domain and not in the storage code: a fresh install, or a setting that was never
 * saved, always gets exactly these values.
 *
 * @property rotationEnabled whether the rotating schedule ("Κυλιόμενο πρόγραμμα")
 * predicts shifts. Off by default: not every operator works the rotation (some work
 * only mornings, or mornings/afternoons), so the app computes nothing until the
 * user turns it on.
 * @property legacyImportAsked whether the first-run question "do you have data from
 * the first version?" was already answered, so it is asked only once.
 * @property themeMode light/dark appearance; follows the phone by default.
 * @property dynamicColor whether app colors come from the wallpaper (Android 12+).
 * On by default; shift colors are fixed and never follow the wallpaper.
 * @property calendarViewPortrait what the calendar shows under the month in portrait:
 * the month summary by default (a phone held upright has room below the grid).
 * @property calendarViewLandscape what the calendar shows beside the month in
 * landscape: the selected day's details by default (room on the side).
 */
data class AppSettings(
    val rotationEnabled: Boolean = false,
    val legacyImportAsked: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val calendarViewPortrait: CalendarView = CalendarView.SUMMARY,
    val calendarViewLandscape: CalendarView = CalendarView.DAY_DETAILS,
)
