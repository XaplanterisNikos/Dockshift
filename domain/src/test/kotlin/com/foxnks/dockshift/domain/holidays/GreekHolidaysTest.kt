package com.foxnks.dockshift.domain.holidays

import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Orthodox Easter and the Greek public holidays, checked against known dates. */
class GreekHolidaysTest {

    @Test
    fun `orthodox Easter matches the published dates`() {
        mapOf(
            2005 to LocalDate.of(2005, 5, 1),
            2010 to LocalDate.of(2010, 4, 4),
            2020 to LocalDate.of(2020, 4, 19),
            2021 to LocalDate.of(2021, 5, 2),
            2022 to LocalDate.of(2022, 4, 24),
            2023 to LocalDate.of(2023, 4, 16),
            2024 to LocalDate.of(2024, 5, 5),
            2025 to LocalDate.of(2025, 4, 20),
            2026 to LocalDate.of(2026, 4, 12),
            2027 to LocalDate.of(2027, 5, 2),
            2028 to LocalDate.of(2028, 4, 16),
        ).forEach { (year, easter) -> assertEquals("$year", easter, OrthodoxEaster.of(year)) }
    }

    @Test
    fun `Easter is always a Sunday, in every year the calendar shows`() {
        (1900..2199).forEach { year ->
            assertEquals("$year", DayOfWeek.SUNDAY, OrthodoxEaster.of(year).dayOfWeek)
        }
    }

    @Test
    fun `movable holidays of 2026`() {
        assertEquals(LocalDate.of(2026, 2, 23), GreekHoliday.CLEAN_MONDAY.dateIn(2026))
        assertEquals(LocalDate.of(2026, 4, 10), GreekHoliday.GOOD_FRIDAY.dateIn(2026))
        assertEquals(LocalDate.of(2026, 4, 13), GreekHoliday.EASTER_MONDAY.dateIn(2026))
        assertEquals(LocalDate.of(2026, 6, 1), GreekHoliday.WHIT_MONDAY.dateIn(2026))
    }

    @Test
    fun `movable holidays fall on the right week days`() {
        (2000..2100).forEach { year ->
            assertEquals(DayOfWeek.MONDAY, GreekHoliday.CLEAN_MONDAY.dateIn(year).dayOfWeek)
            assertEquals(DayOfWeek.FRIDAY, GreekHoliday.GOOD_FRIDAY.dateIn(year).dayOfWeek)
            assertEquals(DayOfWeek.MONDAY, GreekHoliday.EASTER_MONDAY.dateIn(year).dayOfWeek)
            assertEquals(DayOfWeek.MONDAY, GreekHoliday.WHIT_MONDAY.dateIn(year).dayOfWeek)
        }
    }

    @Test
    fun `every year has the 13 holidays in date order`() {
        val holidays = GreekHolidays.of(2026)
        assertEquals(13, holidays.size)
        assertEquals(GreekHoliday.entries.toSet(), holidays.map { it.second }.toSet())
        assertEquals(holidays.sortedBy { it.first }, holidays)
    }

    @Test
    fun `Labour Day is always the first of May`() {
        // Even in 2024, when it was moved by decree to 7 May: a product decision
        assertEquals(LocalDate.of(2024, 5, 1), GreekHoliday.LABOUR_DAY.dateIn(2024))
    }

    @Test
    fun `between returns one holiday per day within the range`() {
        val october = GreekHolidays.between(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31))
        assertEquals(mapOf(LocalDate.of(2026, 10, 28) to GreekHoliday.OHI_DAY), october)
    }

    @Test
    fun `between spans the turn of the year`() {
        val holidays = GreekHolidays.between(LocalDate.of(2026, 12, 20), LocalDate.of(2027, 1, 10))
        assertEquals(
            listOf(GreekHoliday.CHRISTMAS, GreekHoliday.SECOND_DAY_OF_CHRISTMAS, GreekHoliday.NEW_YEAR, GreekHoliday.EPIPHANY),
            holidays.values.toList(),
        )
    }

    @Test
    fun `when Easter falls on the first of May, Easter is shown`() {
        val day = LocalDate.of(2005, 5, 1)
        assertEquals(GreekHoliday.EASTER, GreekHolidays.between(day, day)[day])
    }

    @Test
    fun `an empty range has no holidays`() {
        assertTrue(GreekHolidays.between(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 1, 1)).isEmpty())
    }
}
