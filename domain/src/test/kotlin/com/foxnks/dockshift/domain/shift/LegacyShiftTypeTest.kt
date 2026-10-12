package com.foxnks.dockshift.domain.shift

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Every label Dockshift v1 ever wrote must keep importing. If one of these tests
 * fails, real users' CSV files would stop importing.
 */
class LegacyShiftTypeTest {

    @Test
    fun `greek labels`() {
        assertEquals(ShiftType.MORNING, LegacyShiftType.parse("Πρωί"))
        assertEquals(ShiftType.AFTERNOON, LegacyShiftType.parse("Απόγευμα"))
        assertEquals(ShiftType.NIGHT, LegacyShiftType.parse("Νύχτα"))
        assertEquals(ShiftType.DAY_OFF, LegacyShiftType.parse("Ρεπό"))
        assertEquals(ShiftType.ANNUAL_LEAVE, LegacyShiftType.parse("Άδεια"))
        assertEquals(ShiftType.SICK_LEAVE, LegacyShiftType.parse("Αναρρωτική Άδεια"))
    }

    @Test
    fun `english labels`() {
        assertEquals(ShiftType.MORNING, LegacyShiftType.parse("Morning"))
        assertEquals(ShiftType.AFTERNOON, LegacyShiftType.parse("Afternoon"))
        assertEquals(ShiftType.NIGHT, LegacyShiftType.parse("Night"))
        assertEquals(ShiftType.DAY_OFF, LegacyShiftType.parse("DayOff"))
        assertEquals(ShiftType.ANNUAL_LEAVE, LegacyShiftType.parse("Annual Leave"))
    }

    @Test
    fun `sick leave in both spellings`() {
        // Older v1 data has the typo; v1.11 writes the correct spelling
        assertEquals(ShiftType.SICK_LEAVE, LegacyShiftType.parse("Seak Leave"))
        assertEquals(ShiftType.SICK_LEAVE, LegacyShiftType.parse("Sick Leave"))
    }

    @Test
    fun `whitespace and letter case are ignored`() {
        assertEquals(ShiftType.MORNING, LegacyShiftType.parse("  morning "))
        assertEquals(ShiftType.NIGHT, LegacyShiftType.parse("ΝΎΧΤΑ"))
        assertEquals(ShiftType.DAY_OFF, LegacyShiftType.parse("DAYOFF"))
    }

    @Test
    fun `unknown labels are rejected, not guessed`() {
        assertNull(LegacyShiftType.parse("Πρω"))
        assertNull(LegacyShiftType.parse(""))
        assertNull(LegacyShiftType.parse("none"))
    }
}
