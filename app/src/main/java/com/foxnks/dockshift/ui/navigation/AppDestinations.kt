package com.foxnks.dockshift.ui.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/*
 * The app's screens as navigation keys. Each key is a small @Serializable value,
 * so the back stack is saved and restored even if Android kills the app in the
 * background.
 */

/** Today's shift and the next days (the start screen). */
@Serializable
data object TodayKey : NavKey

/** All stored shifts, filtered by year and month. */
@Serializable
data object ShiftListKey : NavKey

/**
 * Add or edit the shift of one day (one shift per day, so the day identifies it).
 *
 * @property date ISO date, e.g. "2026-10-13" (text, so it is trivially serializable).
 */
@Serializable
data class EditShiftKey(val date: String) : NavKey

/** The month calendar: every day in its shift color, swiped month by month. */
@Serializable
data object CalendarKey : NavKey

/** The terminal's vessels: being worked now, and expected. */
@Serializable
data object VesselsKey : NavKey

/** The central menu: the app's other sections, and Settings. */
@Serializable
data object MenuKey : NavKey

/** The machine list: add, rename, move, deactivate. Opened from the menu. */
@Serializable
data object MachinesKey : NavKey

/**
 * The faults of one machine: open ones, reporting, history. Opened from a machine card
 * on "Today" or from the machine's edit sheet.
 *
 * @property machineId the machine.
 */
@Serializable
data class FaultsKey(val machineId: Long) : NavKey

/** Statistics of shifts, overtime and machines. Opened from the menu. */
@Serializable
data object StatisticsKey : NavKey

/** Planned leave: entitlement, leaves of a year, closed-port holidays. Opened from the menu. */
@Serializable
data object LeavesKey : NavKey

/**
 * Add or edit a leave.
 *
 * @property id the leave to edit, or 0 for a new one.
 * @property day ISO first day of a new leave, or null for today.
 */
@Serializable
data class EditLeaveKey(val id: Long, val day: String? = null) : NavKey

/** Data & backup: backups, restoring, the v1 import, delete all. Opened from the menu or the reminder. */
@Serializable
data object BackupKey : NavKey

/** The user's profile. Opened from the menu, or from the first-run question. */
@Serializable
data object WorkerKey : NavKey

/** Settings. Opened from the menu. */
@Serializable
data object SettingsKey : NavKey
