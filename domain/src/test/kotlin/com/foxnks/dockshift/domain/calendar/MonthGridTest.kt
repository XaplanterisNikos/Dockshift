package com.foxnks.dockshift.domain.calendar

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The 6×7 page of a month, Monday first. */
class MonthGridTest {

    @Test
    fun `every page has 42 consecutive days starting on Monday`() {
        // A few years of months, including every kind of first weekday
        var month = YearMonth.of(2024, 1)
        repeat(48) {
            val dates = MonthGrid.dates(month)
            assertEquals(MonthGrid.CELLS, dates.size)
            assertEquals("$month", DayOfWeek.MONDAY, dates.first().dayOfWeek)
            dates.zipWithNext().forEach { (a, b) -> assertEquals(a.plusDays(1), b) }
            assertTrue("$month must be fully shown", month.atDay(1) in dates && month.atEndOfMonth() in dates)
            month = month.plusMonths(1)
        }
    }

    @Test
    fun `a month starting on Monday has no leading days`() {
        // 1 June 2026 is a Monday
        assertEquals(LocalDate.of(2026, 6, 1), MonthGrid.dates(YearMonth.of(2026, 6)).first())
    }

    @Test
    fun `a month starting on Sunday shows six days of the previous month`() {
        // 1 November 2026 is a Sunday: Monday 26 October opens the page
        val dates = MonthGrid.dates(YearMonth.of(2026, 11))
        assertEquals(LocalDate.of(2026, 10, 26), dates.first())
        assertEquals(LocalDate.of(2026, 11, 1), dates[6])
    }

    @Test
    fun `October 2026 starts on Thursday`() {
        val dates = MonthGrid.dates(YearMonth.of(2026, 10))
        assertEquals(LocalDate.of(2026, 9, 28), dates.first())
        assertEquals(LocalDate.of(2026, 10, 1), dates[3])
        assertEquals(LocalDate.of(2026, 11, 8), dates.last())
    }

    @Test
    fun `leap February is complete`() {
        val dates = MonthGrid.dates(YearMonth.of(2028, 2))
        assertTrue(LocalDate.of(2028, 2, 29) in dates)
    }

    @Test
    fun `range covers exactly the page`() {
        val month = YearMonth.of(2026, 10)
        val range = MonthGrid.range(month)
        assertEquals(MonthGrid.dates(month).first(), range.start)
        assertEquals(MonthGrid.dates(month).last(), range.endInclusive)
    }

    @Test
    fun `week days run Monday to Sunday`() {
        assertEquals(
            listOf(
                DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY,
                DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY,
            ),
            MonthGrid.weekDays(),
        )
    }

    @Test
    fun `another first day of week shifts the page`() {
        val dates = MonthGrid.dates(YearMonth.of(2026, 10), DayOfWeek.SUNDAY)
        assertEquals(DayOfWeek.SUNDAY, dates.first().dayOfWeek)
        assertEquals(LocalDate.of(2026, 9, 27), dates.first())
    }
}
