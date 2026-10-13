package com.foxnks.dockshift.domain.leave

import com.foxnks.dockshift.domain.calendar.CalendarMonth
import com.foxnks.dockshift.domain.calendar.DayStatus
import com.foxnks.dockshift.domain.holidays.GreekHoliday
import com.foxnks.dockshift.domain.shift.Shift
import com.foxnks.dockshift.domain.shift.ShiftType
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Planned leaves: their days, how they count, the yearly balance and their place in the calendar. */
class LeaveTest {

    private fun d(month: Int, day: Int, year: Int = 2026): LocalDate = LocalDate.of(year, month, day)

    private val christmas = Leave(1, d(12, 21), d(12, 26), "Χριστούγεννα")
    private val summer = Leave(2, d(8, 3), d(8, 16), "Καλοκαιρινή")

    @Test(expected = IllegalArgumentException::class)
    fun `a leave cannot end before it starts`() {
        Leave(0, d(8, 10), d(8, 9))
    }

    @Test
    fun `days, membership and overlap`() {
        assertEquals(6, christmas.days.size)
        assertTrue(d(12, 25) in christmas)
        assertFalse(d(12, 27) in christmas)
        assertTrue(christmas.overlaps(Leave(9, d(12, 26), d(12, 31))))
        assertFalse(christmas.overlaps(summer))
    }

    @Test
    fun `every day counts while the port works on holidays`() {
        // The port works on public holidays unless the user says otherwise
        assertEquals(6, LeaveRules.countedDays(christmas, portClosed = emptySet()))
    }

    @Test
    fun `a holiday on which the port is closed does not count`() {
        // 21–26/12 with the port closed on Christmas Day: 5 days
        assertEquals(5, LeaveRules.countedDays(christmas, setOf(GreekHoliday.CHRISTMAS)))
        // ...and on the second day of Christmas as well: 4 days
        assertEquals(4, LeaveRules.countedDays(christmas, setOf(GreekHoliday.CHRISTMAS, GreekHoliday.SECOND_DAY_OF_CHRISTMAS)))
    }

    @Test
    fun `a leave across New Year counts in each year`() {
        val newYear = Leave(3, d(12, 30), LocalDate.of(2027, 1, 3))
        assertEquals(2, LeaveRules.countedDays(newYear, emptySet(), year = 2026))
        assertEquals(3, LeaveRules.countedDays(newYear, emptySet(), year = 2027))
        // 1 January closed: one day less in 2027
        assertEquals(2, LeaveRules.countedDays(newYear, setOf(GreekHoliday.NEW_YEAR), year = 2027))
    }

    @Test
    fun `the entitlement of a year carries on until changed`() {
        val byYear = mapOf(2025 to 22, 2027 to 24)
        assertEquals(22, LeaveRules.entitlementFor(2025, byYear))
        assertEquals(22, LeaveRules.entitlementFor(2026, byYear))
        assertEquals(24, LeaveRules.entitlementFor(2030, byYear))
        assertNull(LeaveRules.entitlementFor(2024, byYear))
    }

    @Test
    fun `the balance splits taken and planned days and shows what remains`() {
        val balance = LeaveBalance.of(2026, 22, listOf(summer, christmas), setOf(GreekHoliday.CHRISTMAS), today = d(10, 13))

        assertEquals(14, balance.taken)
        assertEquals(5, balance.planned)
        assertEquals(19, balance.used)
        assertEquals(3, balance.remaining)
    }

    @Test
    fun `planning more than the entitlement shows a negative remainder`() {
        val balance = LeaveBalance.of(2026, 10, listOf(summer), emptySet(), today = d(1, 1))
        assertEquals(-4, balance.remaining)
    }

    @Test
    fun `without an entitlement nothing remains to compute`() {
        assertNull(LeaveBalance.of(2026, null, listOf(summer), emptySet(), d(1, 1)).remaining)
    }

    @Test
    fun `problems before saving`() {
        assertEquals(LeaveProblem.EndBeforeStart, LeaveProblem.of(d(8, 10), d(8, 9), 0, emptyList()))
        assertEquals(LeaveProblem.Overlaps(summer), LeaveProblem.of(d(8, 15), d(8, 20), 0, listOf(summer, christmas)))
        // A leave is never compared with itself
        assertNull(LeaveProblem.of(d(8, 1), d(8, 16), summer.id, listOf(summer)))
        assertNull(LeaveProblem.of(d(9, 1), d(9, 5), 0, listOf(summer, christmas)))
    }

    @Test
    fun `in the calendar a stored shift wins, then the leave, then the prediction`() {
        val page = CalendarMonth.of(
            YearMonth.of(2026, 8),
            listOf(Shift(d(8, 5), ShiftType.MORNING)),
            leaves = listOf(summer),
            forecast = { ShiftType.NIGHT },
        )

        assertTrue(page.day(d(8, 5))?.status is DayStatus.Recorded)
        assertEquals(DayStatus.OnLeave(summer), page.day(d(8, 6))?.status)
        assertEquals(DayStatus.Forecast(ShiftType.NIGHT), page.day(d(8, 20))?.status)
        // 13 leave days in August (one is a stored morning) count as annual leave
        assertEquals(13, page.summary.countByType[ShiftType.ANNUAL_LEAVE])
    }
}
