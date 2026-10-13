package com.foxnks.dockshift.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalContext

/**
 * Root theme of Dockshift: Material 3 plus the fixed shift colors.
 *
 * - App colors: from the wallpaper on Android 12+ when [dynamicColor] is on,
 *   otherwise Dockshift's own harbour-blue schemes ([DockshiftLightColors]).
 * - Shift colors: always the fixed [ShiftPalette], light or dark variant.
 * - Typography: Material 3 defaults on the phone's system font, which supports Greek
 *   fully and follows the user's font-size setting.
 *
 * @param darkTheme whether to use dark colors; follows the system by default.
 * @param dynamicColor whether to take app colors from the wallpaper where supported.
 * @param content the UI that this theme applies to.
 */
@Composable
fun DockshiftTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        // Dynamic Color exists only from Android 12 (API 31)
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DockshiftDarkColors
        else -> DockshiftLightColors
    }

    CompositionLocalProvider(LocalShiftColors provides ShiftColors(darkTheme)) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content,
        )
    }
}

/** Dockshift's own theme values, next to `MaterialTheme`'s. */
object DockshiftTheme {

    /** Shift colors of the current light/dark mode. */
    val shiftColors: ShiftColors
        @Composable
        @ReadOnlyComposable
        get() = LocalShiftColors.current
}
