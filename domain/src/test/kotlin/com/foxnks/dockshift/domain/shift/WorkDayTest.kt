package com.foxnks.dockshift.domain.shift

import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

/** "Today" for someone working nights: the night belongs to the day it started. */
class WorkDayTest {

    private val wednesday = LocalDate.of(2026, 10, 14)
    private val tuesday = wednesday.minusDays(1)

    private fun at(hour: Int, minute: Int = 0) = wednesday.atTime(hour, minute)

    @Test
    fun `during last night's shift, the current day is yesterday`() {
        assertEquals(tuesday, WorkDay.current(at(2), previousDayType = ShiftType.NIGHT))
        assertEquals(tuesday, WorkDay.current(at(6, 59), previousDayType = ShiftType.NIGHT))
    }

    @Test
    fun `once the night shift has ended, it is today again`() {
        assertEquals(wednesday, WorkDay.current(at(7), previousDayType = ShiftType.NIGHT))
        assertEquals(wednesday, WorkDay.current(at(15), previousDayType = ShiftType.NIGHT))
    }

    @Test
    fun `after any other kind of day, early morning is already today`() {
        ShiftType.entries.filter { it != ShiftType.NIGHT }.forEach { type ->
            assertEquals("$type", wednesday, WorkDay.current(at(2), previousDayType = type))
        }
        assertEquals(wednesday, WorkDay.current(at(2), previousDayType = null))
    }

    @Test
    fun `works across a year change`() {
        val newYearsDay = LocalDateTime.of(2027, 1, 1, 3, 0)

        assertEquals(LocalDate.of(2026, 12, 31), WorkDay.current(newYearsDay, ShiftType.NIGHT))
    }
}
