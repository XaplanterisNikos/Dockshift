package com.foxnks.dockshift.domain.rotation

import com.foxnks.dockshift.domain.settings.AppSettings
import com.foxnks.dockshift.domain.shift.ShiftType
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** The hours of the three working shifts. */
object ShiftHours {

    /** Morning shift: 07:00–15:00. */
    val MORNING_START: LocalTime = LocalTime.of(7, 0)

    /** Afternoon shift: 15:00–23:00. */
    val AFTERNOON_START: LocalTime = LocalTime.of(15, 0)

    /** Night shift: 23:00–07:00 (recorded on the day it starts). */
    val NIGHT_START: LocalTime = LocalTime.of(23, 0)

    /**
     * The shift that is running at [time].
     *
     * @param time a time of day.
     */
    fun typeAt(time: LocalTime): ShiftType = when {
        time < MORNING_START -> ShiftType.NIGHT
        time < AFTERNOON_START -> ShiftType.MORNING
        time < NIGHT_START -> ShiftType.AFTERNOON
        else -> ShiftType.NIGHT
    }
}

/**
 * The predicted type of a day, or null.
 *
 * Only the rotating schedule predicts, only once the operator's place is set, and only
 * from [today] on: past days without a shift are gaps to fill, not predictions.
 *
 * @param date the day.
 * @param today the current date.
 */
fun AppSettings.forecastOn(date: LocalDate, today: LocalDate): ShiftType? =
    RotationSchedule.forecastOn(workSchedule, rotationAnchor, date, today)

/** What the add-shift screen offers as the type of a new shift. */
sealed interface ShiftSuggestion {

    /**
     * Start with this type selected.
     *
     * @property type the suggested type.
     */
    data class Suggested(val type: ShiftType) : ShiftSuggestion

    /**
     * The shift running now is not in the operator's schedule: suggest nothing, say so,
     * and let the operator pick any type.
     *
     * @property runningNow the shift running at the current time.
     */
    data class OutsideSchedule(val runningNow: ShiftType) : ShiftSuggestion

    /** Nothing to suggest: the operator picks the type. */
    data object None : ShiftSuggestion

    companion object {
        /**
         * The suggestion for a new shift, as in the first version of the app:
         * 1. the rotation's prediction for that day, when there is one;
         * 2. for the current work day, the shift running now (07–15 morning, 15–23
         *    afternoon, 23–07 night), if the schedule includes it, otherwise
         *    [OutsideSchedule];
         * 3. the only working type of a single-shift schedule;
         * 4. otherwise nothing.
         *
         * @param date the day of the new shift.
         * @param now the current date and time.
         * @param currentWorkDay the day whose shift is current (yesterday while last
         * night's shift still runs, see `WorkDay`).
         * @param settings the schedule and the rotation place.
         */
        fun of(date: LocalDate, now: LocalDateTime, currentWorkDay: LocalDate, settings: AppSettings): ShiftSuggestion {
            settings.forecastOn(date, now.toLocalDate())?.let { return Suggested(it) }
            val schedule = settings.workSchedule
            if (date == currentWorkDay) {
                val running = ShiftHours.typeAt(now.toLocalTime())
                return if (running in schedule.workTypes) Suggested(running) else OutsideSchedule(running)
            }
            return schedule.workTypes.singleOrNull()?.let { Suggested(it) } ?: None
        }
    }
}
