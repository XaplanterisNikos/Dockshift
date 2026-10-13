package com.foxnks.dockshift.domain.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks the default settings, which are product decisions: changing one of them
 * must be a deliberate change to this test, never an accident.
 */
class AppSettingsTest {

    @Test
    fun `rotating schedule is off by default`() {
        // A fresh install must not predict any shift until the user opts in
        assertFalse(AppSettings().rotationEnabled)
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
}
