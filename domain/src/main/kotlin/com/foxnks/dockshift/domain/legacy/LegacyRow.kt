package com.foxnks.dockshift.domain.legacy

/**
 * One data row of a Dockshift v1 backup file (`dataShift_foxnks.csv`), after CSV
 * parsing and the v1 repairs, but before any interpretation: every value is the
 * raw text of its column, so the planner can report bad values precisely.
 *
 * v1 column order: id, dayOfWeek, day, month, year, shiftDay, category, machines,
 * comments, containers. The v1 id and the day of the week are not needed (the
 * id is meaningless outside v1, the weekday follows from the date) and are not kept.
 *
 * @property line the file line where this row starts (1-based), for error messages.
 * @property day day of month, e.g. "3".
 * @property month month number, e.g. "10".
 * @property year four-digit year, e.g. "2022".
 * @property shiftLabel the translated shift label v1 stored, e.g. "Πρωί" or "DayOff".
 * @property category machine category, e.g. "RTG", or "none".
 * @property machine machine number, e.g. "12", or "none".
 * @property comments free text; v1 wrote "null" for no comment.
 * @property containers number of container moves (no longer used by v2).
 */
data class LegacyRow(
    val line: Int,
    val day: String,
    val month: String,
    val year: String,
    val shiftLabel: String,
    val category: String,
    val machine: String,
    val comments: String,
    val containers: String,
)
