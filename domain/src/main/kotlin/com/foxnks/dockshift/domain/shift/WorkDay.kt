package com.foxnks.dockshift.domain.shift

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Which calendar day the operator is "on" right now.
 *
 * A night shift starts at 23:00 and ends at 07:00 the next morning, but it is
 * recorded on the day it started. So at 02:00 on Wednesday, someone who started a
 * night shift on Tuesday is still in Tuesday's shift: the home screen must keep
 * showing Tuesday until the shift ends.
 */
object WorkDay {

    /** When a night shift ends; before this time the previous night may still be running. */
    val NIGHT_SHIFT_END: LocalTime = LocalTime.of(7, 0)

    /**
     * @param now the current date and time.
     * @param previousDayType the type of the shift stored for the day before [now], or null.
     * @return the day whose shift is current: the previous day while its night shift is
     * still running, otherwise today.
     */
    fun current(now: LocalDateTime, previousDayType: ShiftType?): LocalDate {
        val today = now.toLocalDate()
        val nightStillRunning = previousDayType == ShiftType.NIGHT && now.toLocalTime().isBefore(NIGHT_SHIFT_END)
        return if (nightStillRunning) today.minusDays(1) else today
    }
}
