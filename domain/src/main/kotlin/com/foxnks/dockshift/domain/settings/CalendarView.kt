package com.foxnks.dockshift.domain.settings

/**
 * What the calendar shows next to the month grid. Switched with a button on the
 * calendar itself, and remembered separately for portrait and landscape.
 */
enum class CalendarView {
    /** The month's totals; tapping a day opens it for editing. */
    SUMMARY,

    /** The details of the selected day; tapping a day selects it. */
    DAY_DETAILS,
}
