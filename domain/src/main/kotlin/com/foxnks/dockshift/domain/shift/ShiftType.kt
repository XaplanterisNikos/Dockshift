package com.foxnks.dockshift.domain.shift

/**
 * What a day was (or will be) for the operator.
 *
 * @property isWorkShift true for the three working shifts. Only these can have
 * machines and overtime, and only these count as worked shifts in statistics.
 */
enum class ShiftType(val isWorkShift: Boolean) {
    /** Morning shift (Πρωί) */
    MORNING(isWorkShift = true),

    /** Afternoon shift (Απόγευμα) */
    AFTERNOON(isWorkShift = true),

    /** Night shift (Νύχτα) */
    NIGHT(isWorkShift = true),

    /** Rest day (Ρεπό) */
    DAY_OFF(isWorkShift = false),

    /** Annual leave (Άδεια) */
    ANNUAL_LEAVE(isWorkShift = false),

    /** Sick leave (Αναρρωτική) */
    SICK_LEAVE(isWorkShift = false),

    /** Public holiday off (Αργία) */
    PUBLIC_HOLIDAY(isWorkShift = false),

    /** Training day (Εκπαίδευση): at work, but not driving a machine */
    TRAINING(isWorkShift = false),

    /** Strike (Απεργία) */
    STRIKE(isWorkShift = false),

    /** Parental leave (Γονική άδεια) */
    PARENTAL_LEAVE(isWorkShift = false),
}
