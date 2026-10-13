package com.foxnks.dockshift.ui.theme

import com.foxnks.dockshift.domain.shift.ShiftType
import kotlin.math.pow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Readability of the shift colors, checked with the WCAG 2 contrast formula.
 * Plain arithmetic on the ARGB values: no emulator, no Compose.
 */
class ShiftPaletteTest {

    @Test
    fun `text on every shift color is readable in light mode`() {
        ShiftType.entries.forEach { type ->
            val c = ShiftPalette.of(type)
            val ratio = contrast(c.light, c.onLight)
            assertTrue("$type light: $ratio", ratio >= MIN_TEXT_CONTRAST)
        }
    }

    @Test
    fun `text on every shift color is readable in dark mode`() {
        ShiftType.entries.forEach { type ->
            val c = ShiftPalette.of(type)
            val ratio = contrast(c.dark, c.onDark)
            assertTrue("$type dark: $ratio", ratio >= MIN_TEXT_CONTRAST)
        }
    }

    @Test
    fun `forecast gold is readable in both modes`() {
        val gold = ShiftPalette.FORECAST
        val light = contrast(gold.light, gold.onLight)
        val dark = contrast(gold.dark, gold.onDark)
        assertTrue("forecast light: $light", light >= MIN_TEXT_CONTRAST)
        assertTrue("forecast dark: $dark", dark >= MIN_TEXT_CONTRAST)
    }

    @Test
    fun `forecast gold is not the color of any shift type`() {
        // A prediction must never be mistaken for a recorded day
        ShiftType.entries.forEach { type ->
            val c = ShiftPalette.of(type)
            assertTrue("$type light", c.light != ShiftPalette.FORECAST.light)
            assertTrue("$type dark", c.dark != ShiftPalette.FORECAST.dark)
        }
    }

    @Test
    fun `every shift type has its own color`() {
        // Two types sharing a color would be indistinguishable in the calendar
        val light = ShiftType.entries.map { ShiftPalette.of(it).light }
        val dark = ShiftType.entries.map { ShiftPalette.of(it).dark }

        assertEquals(light.size, light.toSet().size)
        assertEquals(dark.size, dark.toSet().size)
    }

    @Test
    fun `colors are fully opaque`() {
        // A translucent shift color would change with whatever is drawn behind it
        ShiftType.entries.forEach { type ->
            val c = ShiftPalette.of(type)
            listOf(c.light, c.onLight, c.dark, c.onDark).forEach { argb ->
                assertEquals("$type alpha", 0xFFL, (argb shr 24) and 0xFF)
            }
        }
        with(ShiftPalette.FORECAST) {
            listOf(light, onLight, dark, onDark).forEach { argb ->
                assertEquals("forecast alpha", 0xFFL, (argb shr 24) and 0xFF)
            }
        }
    }

    /** WCAG 2 contrast ratio between two colors, from 1 (same) to 21 (black on white). */
    private fun contrast(a: Long, b: Long): Double {
        val (lighter, darker) = listOf(luminance(a), luminance(b)).sortedDescending()
        return (lighter + 0.05) / (darker + 0.05)
    }

    /** WCAG 2 relative luminance of an ARGB color. */
    private fun luminance(argb: Long): Double {
        fun channel(shift: Int): Double {
            val c = ((argb shr shift) and 0xFF) / 255.0
            // sRGB → linear light
            return if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    }

    private companion object {
        /** WCAG AA for normal-size text; our pairs reach about 6 to 9. */
        const val MIN_TEXT_CONTRAST = 4.5
    }
}
