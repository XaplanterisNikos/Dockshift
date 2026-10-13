package com.foxnks.dockshift.domain.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks the default settings, which are product decisions: changing one of them
 * must be a deliberate change to this test, never an accident.
 */
class AppSettingsTest {

    @Test
    fun `nothing is predicted by default`() {
        // All shifts are allowed, but no place in the rotation is set: no prediction
        // until the user sets it (or accepts the one-time suggestion)
        assertEquals(WorkSchedule.ROTATING, AppSettings().workSchedule)
        assertNull(AppSettings().rotationAnchor)
        assertFalse(AppSettings().rotationPromptAnswered)
    }

    @Test
    fun `first-run import question has not been asked by default`() {
        // A fresh install must ask about data from the first version
        assertFalse(AppSettings().legacyImportAsked)
    }

    @Test
    fun `appearance follows the phone by default`() {
        // Light/dark like the system, colors from the wallpaper where supported
        assertEquals(ThemeMode.SYSTEM, AppSettings().themeMode)
        assertTrue(AppSettings().dynamicColor)
    }

    @Test
    fun `calendar shows the summary upright and the day details sideways by default`() {
        // Upright there is room below the grid for totals; sideways, room beside it for a day
        assertEquals(CalendarView.SUMMARY, AppSettings().calendarViewPortrait)
        assertEquals(CalendarView.DAY_DETAILS, AppSettings().calendarViewLandscape)
    }

    @Test
    fun `public holidays are shown by default`() {
        // Useful to everyone, and only a mark: they never become shifts
        assertTrue(AppSettings().showHolidays)
    }
}
