package com.foxnks.dockshift

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.foxnks.dockshift.domain.settings.ThemeMode
import com.foxnks.dockshift.ui.MainViewModel
import com.foxnks.dockshift.ui.navigation.AppNavigation
import com.foxnks.dockshift.ui.theme.DockshiftTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * The app's single activity. It only hosts the Compose UI; screens live in
 * Compose ([AppNavigation]), not in separate activities as in v1.
 *
 * [AndroidEntryPoint] lets Hilt inject into this activity and provide
 * ViewModels to the composables it hosts (hiltViewModel()).
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /** Activity-scoped: the theme must survive rotation like everything else. */
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Draw behind the system bars from the first frame; bar colors are refined below
        enableEdgeToEdge()

        setContent {
            val appearance by viewModel.appearance.collectAsStateWithLifecycle()
            val darkTheme = when (appearance.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            // The status/navigation bar icons must follow the app's theme, not the
            // phone's, when the user forced light or dark in Settings
            DisposableEffect(darkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme },
                    navigationBarStyle = SystemBarStyle.auto(LIGHT_SCRIM, DARK_SCRIM) { darkTheme },
                )
                onDispose {}
            }

            DockshiftTheme(darkTheme = darkTheme, dynamicColor = appearance.dynamicColor) {
                AppNavigation()
            }
        }
    }

    private companion object {
        /** Navigation bar scrims for 3-button navigation, as in Android's own apps. */
        val LIGHT_SCRIM = Color.argb(0xE6, 0xFF, 0xFF, 0xFF)
        val DARK_SCRIM = Color.argb(0x80, 0x1B, 0x1B, 0x1B)
    }
}
