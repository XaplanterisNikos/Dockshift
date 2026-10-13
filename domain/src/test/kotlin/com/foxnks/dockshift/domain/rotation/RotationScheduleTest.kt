package com.foxnks.dockshift.domain.rotation

import com.foxnks.dockshift.domain.shift.Shift
import com.foxnks.dockshift.domain.shift.ShiftType
import com.foxnks.dockshift.domain.shift.ShiftType.AFTERNOON
import com.foxnks.dockshift.domain.shift.ShiftType.DAY_OFF
import com.foxnks.dockshift.domain.shift.ShiftType.MORNING
import com.foxnks.dockshift.domain.shift.ShiftType.NIGHT
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The rotating schedule against the real schedule it was derived from. */
class RotationScheduleTest {

    /** The sample operator: four-day rest from 10/8/2024, then afternoons. */
    private val sample = RotationAnchor(LocalDate.of(2024, 8, 10), AFTERNOON)

    private fun d(year: Int, month: Int, day: Int): LocalDate = LocalDate.of(year, month, day)

    /** Asserts the rotation's type for every day from [from], in order. */
    private fun assertDays(from: LocalDate, vararg expected: ShiftType) {
        expected.forEachIndexed { i, type ->
            val date = from.plusDays(i.toLong())
            assertEquals("$date", type, RotationSchedule.typeOn(sample, date))
        }
    }

    @Test
    fun `the first cycle of the sample, day by day`() {
        assertDays(
            d(2024, 8, 10),
            DAY_OFF, DAY_OFF, DAY_OFF, DAY_OFF,
            AFTERNOON, AFTERNOON, AFTERNOON, AFTERNOON, AFTERNOON, AFTERNOON,
            DAY_OFF, DAY_OFF,
            MORNING, MORNING, MORNING, MORNING, MORNING, MORNING,
            DAY_OFF, DAY_OFF,
            NIGHT, NIGHT, NIGHT, NIGHT, NIGHT, NIGHT,
            DAY_OFF, DAY_OFF,
            AFTERNOON, AFTERNOON, DAY_OFF, AFTERNOON, AFTERNOON, AFTERNOON, AFTERNOON, AFTERNOON,
            DAY_OFF,
            MORNING, MORNING, MORNING, MORNING, DAY_OFF, DAY_OFF, MORNING, MORNING, MORNING, MORNING, MORNING, MORNING,
        )
    }

    @Test
    fun `the next cycle starts one type further, with nights`() {
        // 28/9/2024 is 49 days later: four-day rest, then nights
        assertDays(d(2024, 9, 28), DAY_OFF, DAY_OFF, DAY_OFF, DAY_OFF, NIGHT)
    }

    @Test
    fun `the end of May 2027, as read from the real schedule`() {
        assertEquals(NIGHT, RotationSchedule.typeOn(sample, d(2027, 5, 27)))
        assertDays(d(2027, 5, 28), DAY_OFF, DAY_OFF, NIGHT, NIGHT, NIGHT, NIGHT, NIGHT, NIGHT)
        // 5/6/2027: four-day rest, and the pattern starts over with afternoons
        assertDays(d(2027, 6, 5), DAY_OFF, DAY_OFF, DAY_OFF, DAY_OFF, AFTERNOON)
    }

    @Test
    fun `the whole pattern repeats every 147 days, also backwards`() {
        (0 until 147L).forEach { i ->
            val day = d(2025, 3, 1).plusDays(i)
            val type = RotationSchedule.typeOn(sample, day)
            assertEquals(type, RotationSchedule.typeOn(sample, day.plusDays(147)))
            assertEquals(type, RotationSchedule.typeOn(sample, day.minusDays(147 * 3)))
        }
    }

    @Test
    fun `every cycle has 35 working days and 14 days off`() {
        val days = (0 until 49L).map { RotationSchedule.typeOn(sample, d(2026, 1, 1).plusDays(it)) }
        assertEquals(14, days.count { it == DAY_OFF })
        assertEquals(35, days.count { it.isWorkShift })
    }

    @Test
    fun `an anchor from another cycle gives the same days`() {
        val later = RotationSchedule.latestAnchor(sample, d(2026, 10, 13))
        (0 until 200L).forEach { i ->
            val day = d(2026, 6, 1).plusDays(i)
            assertEquals(RotationSchedule.typeOn(sample, day), RotationSchedule.typeOn(later, day))
        }
    }

    @Test
    fun `the latest anchor is the last four-day rest on or before the day`() {
        val latest = RotationSchedule.latestAnchor(sample, d(2024, 10, 1))
        assertEquals(RotationAnchor(d(2024, 9, 28), NIGHT), latest)
        // On the first rest day itself
        assertEquals(sample, RotationSchedule.latestAnchor(sample, d(2024, 8, 10)))
    }

    @Test
    fun `detects the place from three weeks of recorded days`() {
        // Three weeks of the sample from 20/8/2024: rest, mornings, rest, nights, rest, afternoons
        val shifts = (0 until 21L).map { i ->
            val day = d(2024, 8, 20).plusDays(i)
            Shift(day, RotationSchedule.typeOn(sample, day))
        }

        val found = RotationSchedule.detect(shifts)!!
        assertEquals(RotationAnchor(d(2024, 8, 10), AFTERNOON), found)
    }

    @Test
    fun `leave and sickness are skipped when detecting`() {
        val shifts = (0 until 21L).map { i ->
            val day = d(2024, 8, 20).plusDays(i)
            // A sick day in the middle of the mornings says nothing about the rotation
            if (i == 4L) Shift(day, ShiftType.SICK_LEAVE) else Shift(day, RotationSchedule.typeOn(sample, day))
        }

        assertEquals(RotationAnchor(d(2024, 8, 10), AFTERNOON), RotationSchedule.detect(shifts))
    }

    @Test
    fun `two weeks that fit two places are not guessed`() {
        // Rest, 6 mornings, rest, 6 nights also happens one block earlier in another cycle
        val shifts = (0 until 14L).map { i ->
            val day = d(2024, 8, 20).plusDays(i)
            Shift(day, RotationSchedule.typeOn(sample, day))
        }
        assertNull(RotationSchedule.detect(shifts))
    }

    @Test
    fun `too few days fit several places, so nothing is guessed`() {
        assertNull(RotationSchedule.detect(listOf(Shift(d(2024, 8, 22), MORNING), Shift(d(2024, 8, 23), MORNING))))
    }

    @Test
    fun `shifts that fit no place are not detected`() {
        // Morning, night, morning on three days in a row never happens in the rotation
        val shifts = listOf(Shift(d(2026, 1, 5), MORNING), Shift(d(2026, 1, 6), NIGHT), Shift(d(2026, 1, 7), MORNING))
        assertNull(RotationSchedule.detect(shifts))
    }

    @Test
    fun `only days off are not enough`() {
        assertNull(RotationSchedule.detect((1..10).map { Shift(d(2026, 1, it), DAY_OFF) }))
    }
}
