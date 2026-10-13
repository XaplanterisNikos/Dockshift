package com.foxnks.dockshift.domain.calendar

import com.foxnks.dockshift.domain.shift.Shift
import com.foxnks.dockshift.domain.shift.ShiftType
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Building a month page and its totals from stored shifts (and, later, predictions). */
class CalendarMonthTest {

    private val october = YearMonth.of(2026, 10)
    private fun oct(day: Int): LocalDate = LocalDate.of(2026, 10, day)

    @Test
    fun `stored shifts become recorded days, the rest are empty`() {
        val page = CalendarMonth.of(october, listOf(Shift(oct(13), ShiftType.MORNING)))

        assertEquals(DayStatus.Recorded(Shift(oct(13), ShiftType.MORNING)), page.day(oct(13))?.status)
        assertEquals(DayStatus.Empty, page.day(oct(14))?.status)
    }

    @Test
    fun `days of the neighbour months are marked outside the month`() {
        val page = CalendarMonth.of(october, emptyList())

        assertFalse(page.day(LocalDate.of(2026, 9, 28))!!.inMonth)
        assertTrue(page.day(oct(1))!!.inMonth)
        assertFalse(page.day(LocalDate.of(2026, 11, 1))!!.inMonth)
        assertEquals(31, page.days.count { it.inMonth })
    }

    @Test
    fun `shifts of neighbour days are shown faded but not counted`() {
        val page = CalendarMonth.of(
            october,
            listOf(Shift(LocalDate.of(2026, 9, 30), ShiftType.NIGHT), Shift(oct(2), ShiftType.NIGHT)),
        )

        assertTrue(page.day(LocalDate.of(2026, 9, 30))!!.status is DayStatus.Recorded)
        assertEquals(mapOf(ShiftType.NIGHT to 1), page.summary.countByType)
    }

    @Test
    fun `shifts outside the page are ignored`() {
        val page = CalendarMonth.of(october, listOf(Shift(LocalDate.of(2027, 1, 5), ShiftType.MORNING)))

        assertNull(page.day(LocalDate.of(2027, 1, 5)))
        assertTrue(page.summary.isEmpty)
    }

    @Test
    fun `a prediction fills only the days without a stored shift`() {
        val page = CalendarMonth.of(
            october,
            listOf(Shift(oct(13), ShiftType.DAY_OFF)),
            forecast = { ShiftType.AFTERNOON },
        )

        // What the user entered always wins over the rotation
        assertEquals(ShiftType.DAY_OFF, (page.day(oct(13))?.status as DayStatus.Recorded).shift.type)
        assertEquals(DayStatus.Forecast(ShiftType.AFTERNOON), page.day(oct(14))?.status)
    }

    @Test
    fun `without a prediction nothing is forecast`() {
        val page = CalendarMonth.of(october, emptyList())

        assertTrue(page.days.all { it.status == DayStatus.Empty })
    }

    @Test
    fun `summary counts types in a fixed order, hours and overtime`() {
        val page = CalendarMonth.of(
            october,
            listOf(
                Shift(oct(1), ShiftType.NIGHT),
                Shift(oct(2), ShiftType.MORNING, overtime = true),
                Shift(oct(3), ShiftType.MORNING),
                Shift(oct(4), ShiftType.DAY_OFF),
                Shift(oct(5), ShiftType.AFTERNOON, overtime = true),
            ),
        )
        val summary = page.summary

        assertEquals(
            listOf(ShiftType.MORNING, ShiftType.AFTERNOON, ShiftType.NIGHT, ShiftType.DAY_OFF),
            summary.countByType.keys.toList(),
        )
        assertEquals(2, summary.countByType[ShiftType.MORNING])
        assertEquals(4, summary.workShifts)
        // 4 × 8 regular hours + 2 × 4 overtime hours
        assertEquals(40, summary.workedHours)
        assertEquals(2, summary.overtimeShifts)
        assertEquals(8, summary.overtimeHours)
        assertEquals(0, summary.forecastDays)
        assertFalse(summary.isEmpty)
    }

    @Test
    fun `forecast days are counted apart from recorded ones`() {
        // Predict only inside October, so the faded days stay empty
        val page = CalendarMonth.of(october, emptyList(), forecast = { if (it.month.value == 10) ShiftType.MORNING else null })

        assertEquals(31, page.summary.forecastDays)
        assertTrue(page.summary.countByType.isEmpty())
        assertEquals(0, page.summary.workShifts)
        assertFalse(page.summary.isEmpty)
    }

    @Test
    fun `an empty month has an empty summary`() {
        assertTrue(CalendarMonth.of(october, emptyList()).summary.isEmpty)
    }
}
