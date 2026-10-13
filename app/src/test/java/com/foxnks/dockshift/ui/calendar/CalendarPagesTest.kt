package com.foxnks.dockshift.ui.calendar

import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Test

/** Page numbers of the month pager. */
class CalendarPagesTest {

    @Test
    fun `page and month convert both ways`() {
        listOf(YearMonth.of(1900, 1), YearMonth.of(2022, 6), YearMonth.of(2026, 10), YearMonth.of(2199, 12)).forEach {
            assertEquals(it, CalendarPages.monthOf(CalendarPages.pageOf(it)))
        }
    }

    @Test
    fun `neighbour months are neighbour pages`() {
        val october = CalendarPages.pageOf(YearMonth.of(2026, 10))
        assertEquals(october + 1, CalendarPages.pageOf(YearMonth.of(2026, 11)))
        assertEquals(october - 10, CalendarPages.pageOf(YearMonth.of(2025, 12)))
    }

    @Test
    fun `months outside the range stay on the first or last page`() {
        assertEquals(0, CalendarPages.pageOf(YearMonth.of(1850, 1)))
        assertEquals(CalendarPages.COUNT - 1, CalendarPages.pageOf(YearMonth.of(2300, 1)))
    }
}
