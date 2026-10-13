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

/** Settings. */
@Serializable
data object SettingsKey : NavKey
