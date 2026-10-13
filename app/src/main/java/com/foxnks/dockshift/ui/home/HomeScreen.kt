package com.foxnks.dockshift.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.foxnks.dockshift.R
import com.foxnks.dockshift.domain.shift.ShiftType
import com.foxnks.dockshift.ui.legacy.LegacyFirstRunPrompt
import com.foxnks.dockshift.ui.shift.ShiftChip
import com.foxnks.dockshift.ui.theme.DockshiftTheme

/**
 * Temporary home screen of the v2 skeleton: app name, an "under construction"
 * message and the shift colors, so the design system can be checked on a real
 * phone in light and dark mode. Replaced by the "today" screen in issue #6.
 *
 * It also hosts the first-run v1 import question.
 *
 * @param onOpenSettings called when the settings icon is tapped.
 * @param modifier optional modifier from the caller.
 * @param firstRunPrompt the first-run import prompt (a slot, so previews need no Hilt).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    firstRunPrompt: @Composable () -> Unit = { LegacyFirstRunPrompt() },
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(painterResource(R.drawable.ic_settings), stringResource(R.string.settings_open))
                    }
                },
            )
        },
    ) { innerPadding ->
        // innerPadding keeps the content clear of the bars (edge-to-edge)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.home_placeholder),
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.height(32.dp))
            Text(
                text = stringResource(R.string.home_shift_colors),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(12.dp))
            // Every shift type in its color; wraps onto as many lines as the width needs
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ShiftType.entries.forEach { ShiftChip(it) }
            }
        }
    }

    firstRunPrompt()
}

/** Android Studio preview of [HomeScreen] in light mode; not part of the running app. */
@Preview(showBackground = true)
@Composable
private fun HomeScreenLightPreview() {
    DockshiftTheme(darkTheme = false, dynamicColor = false) {
        HomeScreen(onOpenSettings = {}, firstRunPrompt = {})
    }
}

/** Android Studio preview of [HomeScreen] in dark mode. */
@Preview(showBackground = true, backgroundColor = 0xFF101416)
@Composable
private fun HomeScreenDarkPreview() {
    DockshiftTheme(darkTheme = true, dynamicColor = false) {
        HomeScreen(onOpenSettings = {}, firstRunPrompt = {})
    }
}
