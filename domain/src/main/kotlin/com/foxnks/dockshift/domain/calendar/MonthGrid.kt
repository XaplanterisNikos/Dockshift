package com.foxnks.dockshift.domain.calendar

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/**
 * The days a month page of the calendar shows: always 6 weeks of 7 days.
 *
 * A month touches 4 to 6 calendar weeks. Always drawing 6 keeps every page the
 * same height, so swiping between months never makes the screen jump. The cells
 * before the 1st and after the last day belong to the neighbouring months and are
 * drawn faded.
 */
object MonthGrid {

    /** Days in a week (columns of the grid). */
    const val DAYS_PER_WEEK = 7

    /** Weeks shown per month (rows of the grid). */
    const val WEEKS = 6

    /** Cells of one page. */
    const val CELLS = DAYS_PER_WEEK * WEEKS

    /**
     * The week starts on Monday, as on Greek calendars and in the v1 app.
     * A parameter below allows another start day, but the app always uses this one.
     */
    val FIRST_DAY_OF_WEEK: DayOfWeek = DayOfWeek.MONDAY

    /**
     * The [CELLS] dates of a month page, row by row.
     *
     * @param month the month the page is about.
     * @param firstDayOfWeek the day of the first column.
     * @return consecutive dates: a few days of the previous month (if the 1st is not
     * on [firstDayOfWeek]), the whole [month], then days of the next month.
     */
    fun dates(month: YearMonth, firstDayOfWeek: DayOfWeek = FIRST_DAY_OF_WEEK): List<LocalDate> {
        val first = month.atDay(1)
        // How many cells of the first row belong to the previous month (0..6)
        val leading = Math.floorMod(first.dayOfWeek.value - firstDayOfWeek.value, DAYS_PER_WEEK)
        val start = first.minusDays(leading.toLong())
        return List(CELLS) { start.plusDays(it.toLong()) }
    }

    /**
     * The first and last date of the page of [month] (both included), e.g. to read
     * the stored shifts of exactly the visible cells.
     */
    fun range(month: YearMonth, firstDayOfWeek: DayOfWeek = FIRST_DAY_OF_WEEK): ClosedRange<LocalDate> {
        val dates = dates(month, firstDayOfWeek)
        return dates.first()..dates.last()
    }

    /**
     * The days of the week in column order, e.g. Monday … Sunday, for the header row.
     *
     * @param firstDayOfWeek the day of the first column.
     */
    fun weekDays(firstDayOfWeek: DayOfWeek = FIRST_DAY_OF_WEEK): List<DayOfWeek> =
        List(DAYS_PER_WEEK) { firstDayOfWeek.plus(it.toLong()) }
}
