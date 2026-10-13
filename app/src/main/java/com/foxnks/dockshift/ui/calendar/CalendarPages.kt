package com.foxnks.dockshift.ui.calendar

import java.time.YearMonth
import java.time.temporal.ChronoUnit

/**
 * Maps the pager's page numbers to months.
 *
 * The calendar can be swiped freely in both directions, but a pager needs a fixed
 * number of pages. Three centuries of months is "free" for any real use, and a fixed
 * first month means a page number always means the same month (no drift when the
 * app stays open across the turn of a month).
 */
object CalendarPages {

    /** The month of page 0. */
    val FIRST: YearMonth = YearMonth.of(1900, 1)

    /** Pages from January 1900 to December 2199. */
    const val COUNT: Int = 300 * 12

    /**
     * The page that shows [month], kept inside the pager's range.
     *
     * @param month any month.
     */
    fun pageOf(month: YearMonth): Int =
        ChronoUnit.MONTHS.between(FIRST, month).toInt().coerceIn(0, COUNT - 1)

    /**
     * The month a page shows.
     *
     * @param page a page number from 0 to [COUNT] - 1.
     */
    fun monthOf(page: Int): YearMonth = FIRST.plusMonths(page.toLong())
}
