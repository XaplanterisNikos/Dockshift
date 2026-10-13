package com.foxnks.dockshift.domain.rotation

import com.foxnks.dockshift.domain.settings.AppSettings
import com.foxnks.dockshift.domain.settings.WorkSchedule
import com.foxnks.dockshift.domain.shift.ShiftType
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Shift hours, predictions from the settings, and the suggested type of a new shift. */
class ShiftSuggestionTest {

    private val today = LocalDate.of(2026, 10, 13)
    private val sample = RotationAnchor(LocalDate.of(2024, 8, 10), ShiftType.AFTERNOON)
    private val rotating = AppSettings(rotationAnchor = sample)

    @Test
    fun `shift hours`() {
        assertEquals(ShiftType.NIGHT, ShiftHours.typeAt(LocalTime.of(6, 59)))
        assertEquals(ShiftType.MORNING, ShiftHours.typeAt(LocalTime.of(7, 0)))
        assertEquals(ShiftType.MORNING, ShiftHours.typeAt(LocalTime.of(14, 59)))
        assertEquals(ShiftType.AFTERNOON, ShiftHours.typeAt(LocalTime.of(15, 0)))
        assertEquals(ShiftType.AFTERNOON, ShiftHours.typeAt(LocalTime.of(22, 59)))
        assertEquals(ShiftType.NIGHT, ShiftHours.typeAt(LocalTime.of(23, 0)))
    }

    @Test
    fun `predictions start today`() {
        assertEquals(RotationSchedule.typeOn(sample, today), rotating.forecastOn(today, today))
        assertEquals(RotationSchedule.typeOn(sample, today.plusDays(30)), rotating.forecastOn(today.plusDays(30), today))
        assertNull(rotating.forecastOn(today.minusDays(1), today))
    }

    @Test
    fun `no prediction without a place or outside the rotating schedule`() {
        assertNull(AppSettings().forecastOn(today, today))
        assertNull(rotating.copy(workSchedule = WorkSchedule.MORNING_ONLY).forecastOn(today, today))
    }

    @Test
    fun `the prediction is suggested first`() {
        val suggestion = ShiftSuggestion.of(today, today.atTime(16, 0), today, rotating)
        assertEquals(ShiftSuggestion.Suggested(RotationSchedule.typeOn(sample, today)), suggestion)
    }

    @Test
    fun `today, the shift running now is suggested`() {
        val settings = AppSettings()
        assertEquals(ShiftSuggestion.Suggested(ShiftType.MORNING), ShiftSuggestion.of(today, today.atTime(8, 0), today, settings))
        assertEquals(ShiftSuggestion.Suggested(ShiftType.AFTERNOON), ShiftSuggestion.of(today, today.atTime(16, 0), today, settings))
        assertEquals(ShiftSuggestion.Suggested(ShiftType.NIGHT), ShiftSuggestion.of(today, today.atTime(23, 30), today, settings))
    }

    @Test
    fun `a morning-only operator in the afternoon gets nothing, and is told so`() {
        val settings = AppSettings(workSchedule = WorkSchedule.MORNING_ONLY)
        assertEquals(
            ShiftSuggestion.OutsideSchedule(ShiftType.AFTERNOON),
            ShiftSuggestion.of(today, today.atTime(16, 0), today, settings),
        )
    }

    @Test
    fun `another day of a single-shift schedule gets that shift`() {
        val settings = AppSettings(workSchedule = WorkSchedule.NIGHT_ONLY)
        assertEquals(ShiftSuggestion.Suggested(ShiftType.NIGHT), ShiftSuggestion.of(today.plusDays(3), today.atTime(10, 0), today, settings))
    }

    @Test
    fun `another day of a mixed schedule gets nothing`() {
        val settings = AppSettings(workSchedule = WorkSchedule.MORNING_AFTERNOON)
        assertEquals(ShiftSuggestion.None, ShiftSuggestion.of(today.plusDays(3), today.atTime(10, 0), today, settings))
    }

    @Test
    fun `schedules allow their own working types and every non-working type`() {
        val schedule = WorkSchedule.MORNING_AFTERNOON
        assertTrue(schedule.allows(ShiftType.MORNING))
        assertFalse(schedule.allows(ShiftType.NIGHT))
        assertTrue(schedule.allows(ShiftType.DAY_OFF))
        assertTrue(schedule.allows(ShiftType.ANNUAL_LEAVE))
        assertTrue(WorkSchedule.ROTATING.predicts)
        assertFalse(schedule.predicts)
    }
}
