package com.foxnks.dockshift

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.foxnks.dockshift.ui.theme.DockshiftTheme

/**
 * The app's single activity. It only hosts the Compose UI; screens and
 * navigation will live in Compose, not in separate activities as in v1.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Draw behind the status and navigation bars; Scaffold adds the needed insets
        enableEdgeToEdge()
        setContent {
            DockshiftTheme {
                PlaceholderScreen()
            }
        }
    }
}

/**
 * Temporary start screen of the v2 skeleton: app name and an "under construction"
 * message. It is replaced by the real "today" screen in a later issue.
 *
 * @param modifier optional modifier from the caller.
 */
@Composable
fun PlaceholderScreen(modifier: Modifier = Modifier) {
    Scaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
        // innerPadding keeps the content clear of the system bars (edge-to-edge)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                text = stringResource(R.string.home_placeholder),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

/** Android Studio preview of [PlaceholderScreen]; not part of the running app. */
@Preview(showBackground = true)
@Composable
private fun PlaceholderScreenPreview() {
    DockshiftTheme {
        PlaceholderScreen()
    }
}
