package com.foxnks.dockshift.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * Root Material 3 theme of Dockshift.
 *
 * Minimal version for the project skeleton: Dynamic Color on Android 12+ and the
 * Material 3 baseline colors as fallback. The app's own palette, typography and
 * shift colors come with the design-system issue.
 *
 * @param darkTheme whether to use the dark color scheme; follows the system setting by default.
 * @param dynamicColor whether to take colors from the user's wallpaper when the device supports it.
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
        // Android 10-11 fallback: Material 3 baseline colors
        darkTheme -> darkColorScheme()
        else -> lightColorScheme()
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}
