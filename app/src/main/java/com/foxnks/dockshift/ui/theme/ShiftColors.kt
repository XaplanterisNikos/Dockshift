package com.foxnks.dockshift.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.foxnks.dockshift.domain.shift.ShiftType

/**
 * Shift colors for the current light/dark mode, ready for Compose.
 *
 * Read it inside the theme with `DockshiftTheme.shiftColors`, e.g.
 * `DockshiftTheme.shiftColors.container(ShiftType.NIGHT)`.
 *
 * @property darkTheme whether the dark variants are used.
 */
@Immutable
class ShiftColors(private val darkTheme: Boolean) {

    /** Background (fill) color of [type]: calendar cells, chips, chart bars. */
    fun container(type: ShiftType): Color =
        ShiftPalette.of(type).let { Color(if (darkTheme) it.dark else it.light) }

    /** Text and icon color to draw on top of [container]. */
    fun onContainer(type: ShiftType): Color =
        ShiftPalette.of(type).let { Color(if (darkTheme) it.onDark else it.onLight) }
}

/**
 * Provides [ShiftColors] down the composition. Static, because it only changes
 * when the whole theme changes (light ↔ dark), so nothing finer-grained is needed.
 */
val LocalShiftColors = staticCompositionLocalOf { ShiftColors(darkTheme = false) }
