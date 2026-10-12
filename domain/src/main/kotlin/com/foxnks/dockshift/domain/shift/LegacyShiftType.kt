package com.foxnks.dockshift.domain.shift

import java.util.Locale

/**
 * Reads the shift values stored by Dockshift v1 (v1.10/v1.11).
 *
 * v1 saved the shift as the *translated label* shown at the time of entry, so the
 * same day can appear in Greek or in English depending on the phone's language.
 * Older versions also misspelled sick leave as "Seak Leave"; v1.11 shows
 * "Sick Leave". All of these must keep importing forever.
 */
object LegacyShiftType {

    /** Every known v1 label, lowercased, mapped to its type. */
    private val labels: Map<String, ShiftType> = mapOf(
        "πρωί" to ShiftType.MORNING,
        "morning" to ShiftType.MORNING,
        "απόγευμα" to ShiftType.AFTERNOON,
        "afternoon" to ShiftType.AFTERNOON,
        "νύχτα" to ShiftType.NIGHT,
        "night" to ShiftType.NIGHT,
        "ρεπό" to ShiftType.DAY_OFF,
        "dayoff" to ShiftType.DAY_OFF,
        "άδεια" to ShiftType.ANNUAL_LEAVE,
        "annual leave" to ShiftType.ANNUAL_LEAVE,
        "αναρρωτική άδεια" to ShiftType.SICK_LEAVE,
        "seak leave" to ShiftType.SICK_LEAVE,
        "sick leave" to ShiftType.SICK_LEAVE,
    )

    /**
     * Converts a v1 label to a [ShiftType].
     *
     * Surrounding whitespace and letter case are ignored.
     *
     * @param label the value of the "shiftDay" column of a v1 CSV.
     * @return the matching type, or null if the label is unknown (the caller
     * reports exactly which row and value failed).
     */
    fun parse(label: String): ShiftType? =
        labels[label.trim().lowercase(Locale.ROOT)]
}
