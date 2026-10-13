package com.foxnks.dockshift.domain.settings

import com.foxnks.dockshift.domain.holidays.GreekHoliday
import com.foxnks.dockshift.domain.rotation.RotationAnchor
import java.time.LocalDate

/**
 * The user's app-wide settings.
 *
 * Default values are the product decisions themselves, so they live here in the
 * domain and not in the storage code: a fresh install, or a setting that was never
 * saved, always gets exactly these values.
 *
 * @property workSchedule which shifts the operator works ("Ωράριο"). All three, in the
 * rotating schedule, by default: it limits nothing, and nothing is predicted until
 * [rotationAnchor] is set.
 * @property rotationAnchor the operator's place in the rotating schedule, or null when
 * not set (then nothing is predicted).
 * @property rotationPromptAnswered whether the one-time question "your shifts match the
 * rotating schedule, use it?" was already answered (yes or no).
 * @property legacyImportAsked whether the first-run question "do you have data from
 * the first version?" was already answered, so it is asked only once.
 * @property themeMode light/dark appearance; follows the phone by default.
 * @property dynamicColor whether app colors come from the wallpaper (Android 12+).
 * On by default; shift colors are fixed and never follow the wallpaper.
 * @property calendarViewPortrait what the calendar shows under the month in portrait:
 * the month summary by default (a phone held upright has room below the grid).
 * @property calendarViewLandscape what the calendar shows beside the month in
 * landscape: the selected day's details by default (room on the side).
 * @property leaveEntitlements the annual leave days the user is entitled to, by year
 * (22, 24… depending on years of service); empty until set.
 * @property portClosedHolidays the public holidays on which the port is closed: a leave
 * day on one of them does not count. Empty by default (the port works on holidays),
 * the user picks them.
 * @property lastBackup the day of the last backup, or null when none was made yet.
 * @property backupReminder whether to remind about a backup every 15 days. On by
 * default: losing a phone must not mean losing years of shifts.
 * @property showHolidays whether the Greek public holidays are marked (red day number
 * and name) in the calendar, on "Today" and in the shift list. On by default: useful
 * to everyone, and they are only shown, never turned into shifts.
 */
data class AppSettings(
    val workSchedule: WorkSchedule = WorkSchedule.ROTATING,
    val rotationAnchor: RotationAnchor? = null,
    val rotationPromptAnswered: Boolean = false,
    val legacyImportAsked: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val calendarViewPortrait: CalendarView = CalendarView.SUMMARY,
    val calendarViewLandscape: CalendarView = CalendarView.DAY_DETAILS,
    val showHolidays: Boolean = true,
    val leaveEntitlements: Map<Int, Int> = emptyMap(),
    val portClosedHolidays: Set<GreekHoliday> = emptySet(),
    val lastBackup: LocalDate? = null,
    val backupReminder: Boolean = true,
)
