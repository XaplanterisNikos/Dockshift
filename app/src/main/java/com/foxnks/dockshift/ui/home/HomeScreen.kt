package com.foxnks.dockshift.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.foxnks.dockshift.R
import com.foxnks.dockshift.ui.legacy.LegacyImportSection
import com.foxnks.dockshift.ui.theme.DockshiftTheme

/**
 * Entry point of the home screen: connects the [HomeViewModel] to [HomeScreen].
 *
 * This is the only composable that knows the ViewModel exists; everything below it
 * receives plain values and callbacks, so it can be previewed and tested in isolation.
 *
 * @param modifier optional modifier from the caller.
 * @param viewModel provided by Hilt; tests or previews never need to pass it.
 */
@Composable
fun HomeRoute(
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    // Stops collecting while the app is in the background, saving work and battery
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    HomeScreen(
        uiState = uiState,
        onRotationToggled = viewModel::onRotationToggled,
        modifier = modifier,
        // Temporary home of the v1 import, until the Settings screen exists
        footer = { LegacyImportSection() },
    )
}

/**
 * Temporary home screen of the v2 skeleton: app name, an "under construction"
 * message and the rotating schedule switch, which proves the full path
 * screen → ViewModel → repository → DataStore and back. The switch moves to the
 * settings screen when the real screens arrive.
 *
 * @param uiState what to show.
 * @param onRotationToggled called with the new value when the user flips the switch.
 * @param modifier optional modifier from the caller.
 * @param footer extra content below the settings (a slot, so this screen stays free
 * of ViewModels and its previews need no Hilt).
 */
@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onRotationToggled: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    footer: @Composable () -> Unit = {},
) {
    Scaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
        // innerPadding keeps the content clear of the system bars (edge-to-edge)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp),
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
            Spacer(modifier = Modifier.height(32.dp))

            // Exhaustive `when`: a new state added to HomeUiState will not compile until handled here
            when (uiState) {
                HomeUiState.Loading -> CircularProgressIndicator()
                is HomeUiState.Ready -> RotationSettingRow(
                    checked = uiState.rotationEnabled,
                    onCheckedChange = onRotationToggled,
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
            footer()
        }
    }
}

/**
 * A settings row with a title, a short explanation and a switch.
 * The whole row is the touch target, not only the small switch.
 *
 * @param checked current switch position.
 * @param onCheckedChange called with the new value on tap.
 */
@Composable
private fun RotationSettingRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // Makes the row tappable and announces it as a switch to TalkBack
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.setting_rotation_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.setting_rotation_summary),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // onCheckedChange = null: the row already handles taps, so the switch must not do it twice
        Switch(checked = checked, onCheckedChange = null)
    }
}

/** Android Studio preview of [HomeScreen] with loaded settings; not part of the running app. */
@Preview(showBackground = true)
@Composable
private fun HomeScreenReadyPreview() {
    DockshiftTheme {
        HomeScreen(uiState = HomeUiState.Ready(rotationEnabled = false), onRotationToggled = {})
    }
}

/** Android Studio preview of [HomeScreen] while settings are loading. */
@Preview(showBackground = true)
@Composable
private fun HomeScreenLoadingPreview() {
    DockshiftTheme {
        HomeScreen(uiState = HomeUiState.Loading, onRotationToggled = {})
    }
}
