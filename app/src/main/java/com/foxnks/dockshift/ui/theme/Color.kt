package com.foxnks.dockshift.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/*
 * Dockshift's own Material 3 color schemes, used where wallpaper colors
 * (Dynamic Color) are unavailable (Android 10-11) or turned off in Settings.
 *
 * Generated from the seed color #1B6A80 (harbour blue) with the Material 3
 * "tonal spot" algorithm, the same one the Material Theme Builder uses.
 * To change the palette, regenerate every role from a new seed rather than
 * editing single values, so all roles keep their designed contrast.
 */

/** Seed color the schemes below were generated from. */
val DockshiftSeed = Color(0xFF1B6A80)

/** Light scheme. */
val DockshiftLightColors = lightColorScheme(
    primary = Color(0xFF24677B),
    onPrimary = Color(0xFFF1FAFF),
    primaryContainer = Color(0xFFA5E3FA),
    onPrimaryContainer = Color(0xFF025467),
    secondary = Color(0xFF4C626B),
    onSecondary = Color(0xFFF1FAFF),
    secondaryContainer = Color(0xFFCFE6F0),
    onSecondaryContainer = Color(0xFF3F555D),
    tertiary = Color(0xFF4B5F84),
    onTertiary = Color(0xFFF8F8FF),
    tertiaryContainer = Color(0xFFBACFF9),
    onTertiaryContainer = Color(0xFF314669),
    error = Color(0xFFA83836),
    onError = Color(0xFFFFF7F6),
    errorContainer = Color(0xFFFA746F),
    onErrorContainer = Color(0xFF6E0A12),
    background = Color(0xFFF7FAFC),
    onBackground = Color(0xFF2B3437),
    surface = Color(0xFFF7FAFC),
    onSurface = Color(0xFF2B3437),
    surfaceVariant = Color(0xFFDBE4E8),
    onSurfaceVariant = Color(0xFF576064),
    outline = Color(0xFF737C80),
    outlineVariant = Color(0xFFAAB3B8),
    inverseSurface = Color(0xFF0B0F11),
    inverseOnSurface = Color(0xFF9A9DA0),
    inversePrimary = Color(0xFFA5E3FA),
    surfaceTint = Color(0xFF24677B),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFF7FAFC),
    surfaceDim = Color(0xFFD2DBE0),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFEFF4F7),
    surfaceContainer = Color(0xFFE8EFF2),
    surfaceContainerHigh = Color(0xFFE2E9ED),
    surfaceContainerHighest = Color(0xFFDBE4E8),
)

/** Dark scheme. */
val DockshiftDarkColors = darkColorScheme(
    primary = Color(0xFFA0CDDF),
    onPrimary = Color(0xFF144654),
    primaryContainer = Color(0xFF2A5867),
    onPrimaryContainer = Color(0xFFBCEAFD),
    secondary = Color(0xFFB3CAD4),
    onSecondary = Color(0xFF2E434B),
    secondaryContainer = Color(0xFF293F46),
    onSecondaryContainer = Color(0xFFACC3CD),
    tertiary = Color(0xFFCDDDFF),
    onTertiary = Color(0xFF3A4F72),
    tertiaryContainer = Color(0xFFBACFF9),
    onTertiaryContainer = Color(0xFF314669),
    error = Color(0xFFFA746F),
    onError = Color(0xFF490006),
    errorContainer = Color(0xFF871F21),
    onErrorContainer = Color(0xFFFF9993),
    background = Color(0xFF0B0F11),
    onBackground = Color(0xFFDEE7EB),
    surface = Color(0xFF0B0F11),
    onSurface = Color(0xFFDEE7EB),
    surfaceVariant = Color(0xFF1E272B),
    onSurfaceVariant = Color(0xFFA3ADB1),
    outline = Color(0xFF6E777B),
    outlineVariant = Color(0xFF40494D),
    inverseSurface = Color(0xFFF7FAFC),
    inverseOnSurface = Color(0xFF515658),
    inversePrimary = Color(0xFF386574),
    surfaceTint = Color(0xFFA0CDDF),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF252D31),
    surfaceDim = Color(0xFF0B0F11),
    surfaceContainerLowest = Color(0xFF000000),
    surfaceContainerLow = Color(0xFF0F1416),
    surfaceContainer = Color(0xFF141B1D),
    surfaceContainerHigh = Color(0xFF192124),
    surfaceContainerHighest = Color(0xFF1E272B),
)
