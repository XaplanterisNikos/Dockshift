package com.foxnks.dockshift

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.foxnks.dockshift.ui.home.HomeRoute
import com.foxnks.dockshift.ui.theme.DockshiftTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * The app's single activity. It only hosts the Compose UI; screens and
 * navigation live in Compose, not in separate activities as in v1.
 *
 * [AndroidEntryPoint] lets Hilt inject into this activity and provide
 * ViewModels to the composables it hosts (hiltViewModel()).
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Draw behind the status and navigation bars; Scaffold adds the needed insets
        enableEdgeToEdge()
        setContent {
            DockshiftTheme {
                HomeRoute()
            }
        }
    }
}
