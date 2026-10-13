package com.foxnks.dockshift.ui.theme

import com.foxnks.dockshift.domain.shift.ShiftType

/**
 * Fixed colors of one shift type, as ARGB values.
 *
 * Plain numbers (no Compose types) so they can be checked by plain unit tests.
 *
 * @property light background of the type in light mode.
 * @property onLight text/icons drawn on [light].
 * @property dark background of the type in dark mode.
 * @property onDark text/icons drawn on [dark].
 */
data class ShiftColorValues(
    val light: Long,
    val onLight: Long,
    val dark: Long,
    val onDark: Long,
)

/**
 * The color of every shift type, the same everywhere: calendar, list, charts, widget.
 *
 * These never follow the wallpaper, so "Night" looks the same on every phone.
 * Designed in the OKLCH color space with one lightness per role, so all types
 * have equal weight and the text on them always has high contrast (checked by
 * `ShiftPaletteTest`); only the hue tells them apart. The hues keep the ones
 * colleagues know from v1 where possible (morning cyan, night blue, day off yellow,
 * annual leave red-pink); afternoon moved from blue to orange so it no longer
 * looks like night.
 */
object ShiftPalette {

    /** @return the colors of [type]. Exhaustive: a new type will not compile without colors. */
    fun of(type: ShiftType): ShiftColorValues = when (type) {
        ShiftType.MORNING -> ShiftColorValues(
            light = 0xFF10DEE7, onLight = 0xFF002A2C,
            dark = 0xFF026266, onDark = 0xFFC5F6F8,
        )
        ShiftType.AFTERNOON -> ShiftColorValues(
            light = 0xFFFEB079, onLight = 0xFF361D0A,
            dark = 0xFF824103, onDark = 0xFFFEE6D6,
        )
        ShiftType.NIGHT -> ShiftColorValues(
            light = 0xFFB0C1FE, onLight = 0xFF1C223C,
            dark = 0xFF3846B1, onDark = 0xFFE4EBFF,
        )
        ShiftType.DAY_OFF -> ShiftColorValues(
            light = 0xFFDEC504, onLight = 0xFF2A2403,
            dark = 0xFF615604, onDark = 0xFFF3EDC7,
        )
        ShiftType.ANNUAL_LEAVE -> ShiftColorValues(
            light = 0xFFFEA9AF, onLight = 0xFF39191C,
            dark = 0xFF9D1135, onDark = 0xFFFFE4E5,
        )
        ShiftType.SICK_LEAVE -> ShiftColorValues(
            light = 0xFF7CDF81, onLight = 0xFF132A14,
            dark = 0xFF036819, onDark = 0xFFD7F5D7,
        )
        ShiftType.PUBLIC_HOLIDAY -> ShiftColorValues(
            light = 0xFFD5B2FF, onLight = 0xFF2A1D37,
            dark = 0xFF6B359B, onDark = 0xFFF0E6FE,
        )
        ShiftType.PARENTAL_LEAVE -> ShiftColorValues(
            light = 0xFFFEA2D7, onLight = 0xFF351929,
            dark = 0xFF8B2868, onDark = 0xFFFEE2F1,
        )
        ShiftType.TRAINING -> ShiftColorValues(
            light = 0xFFA6CAE0, onLight = 0xFF052739,
            dark = 0xFF395A6D, onDark = 0xFFD8EFFE,
        )
        ShiftType.STRIKE -> ShiftColorValues(
            light = 0xFFC4C4C4, onLight = 0xFF242424,
            dark = 0xFF555555, onDark = 0xFFEBEBEB,
        )
    }
}
