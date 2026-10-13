package com.foxnks.dockshift.ui.settings

import android.os.Build
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.foxnks.dockshift.R
import com.foxnks.dockshift.domain.settings.ThemeMode
import com.foxnks.dockshift.ui.legacy.LegacyImportSettingsItem
import com.foxnks.dockshift.ui.theme.DockshiftTheme

/**
 * Entry point of the Settings screen: connects [SettingsViewModel] to [SettingsScreen].
 *
 * @param onBack called when the user taps the back arrow.
 * @param viewModel provided by Hilt.
 */
@Composable
fun SettingsRoute(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    SettingsScreen(
        uiState = uiState,
        onBack = onBack,
        onThemeModeSelected = viewModel::onThemeModeSelected,
        onDynamicColorToggled = viewModel::onDynamicColorToggled,
        onRotationToggled = viewModel::onRotationToggled,
        onDeleteAllShifts = viewModel::onDeleteAllShifts,
        dataSection = { LegacyImportSettingsItem() },
    )
}

/**
 * Settings, grouped in sections: Appearance, Schedule, Data.
 *
 * @param uiState what to show.
 * @param onBack back arrow tapped.
 * @param onThemeModeSelected a theme option was picked.
 * @param onDynamicColorToggled the wallpaper-colors switch was flipped.
 * @param onRotationToggled the rotating schedule switch was flipped.
 * @param onDeleteAllShifts "delete all shifts" was confirmed twice.
 * @param dataSection items of the Data section (a slot, so previews need no Hilt).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    onBack: () -> Unit,
    onThemeModeSelected: (ThemeMode) -> Unit,
    onDynamicColorToggled: (Boolean) -> Unit,
    onRotationToggled: (Boolean) -> Unit,
    onDeleteAllShifts: () -> Unit = {},
    dataSection: @Composable () -> Unit = {},
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.navigate_back))
                    }
                },
            )
        },
    ) { innerPadding ->
        when (uiState) {
            SettingsUiState.Loading -> Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            is SettingsUiState.Ready -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState()),
            ) {
                SectionTitle(R.string.settings_section_appearance)
                ThemeOptions(selected = uiState.themeMode, onSelected = onThemeModeSelected)
                // Wallpaper colors exist only from Android 12; on older phones the option would do nothing
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    SwitchItem(
                        title = R.string.setting_dynamic_color_title,
                        summary = R.string.setting_dynamic_color_summary,
                        checked = uiState.dynamicColor,
                        onCheckedChange = onDynamicColorToggled,
                    )
                }
                HorizontalDivider()

                SectionTitle(R.string.settings_section_schedule)
                SwitchItem(
                    title = R.string.setting_rotation_title,
                    summary = R.string.setting_rotation_summary,
                    checked = uiState.rotationEnabled,
                    onCheckedChange = onRotationToggled,
                )
                HorizontalDivider()

                SectionTitle(R.string.settings_section_data)
                dataSection()
                DeleteAllItem(storedShifts = uiState.storedShifts, onConfirmed = onDeleteAllShifts)
            }
        }
    }
}

/** Small colored heading above a group of settings. */
@Composable
private fun SectionTitle(@StringRes text: Int) {
    Text(
        text = stringResource(text),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp),
    )
}

/** The three theme choices as radio buttons; the whole row is the touch target. */
@Composable
private fun ThemeOptions(selected: ThemeMode, onSelected: (ThemeMode) -> Unit) {
    // selectableGroup tells TalkBack these rows are one single-choice group
    Column(Modifier.selectableGroup()) {
        Text(
            text = stringResource(R.string.setting_theme_title),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        ThemeMode.entries.forEach { mode ->
            ListItem(
                headlineContent = { Text(stringResource(mode.labelRes())) },
                // onClick = null: the row handles the tap, the radio button only shows the state
                leadingContent = { RadioButton(selected = mode == selected, onClick = null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(selected = mode == selected, role = Role.RadioButton, onClick = { onSelected(mode) }),
            )
        }
    }
}

/** A setting with a title, a short explanation and a switch; the whole row toggles. */
@Composable
private fun SwitchItem(
    @StringRes title: Int,
    @StringRes summary: Int,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    ListItem(
        headlineContent = { Text(stringResource(title)) },
        supportingContent = { Text(stringResource(summary)) },
        // onCheckedChange = null: the row handles the tap, so the switch must not do it twice
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
        modifier = Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
    )
}

/** Two steps the "delete all" confirmation can be in. */
private enum class DeleteAllStep { NONE, FIRST, SECOND }

/**
 * "Delete all shifts", in red, behind two confirmations: the first says how many
 * shifts will go, the second that it cannot be undone.
 *
 * @param storedShifts how many shifts would be deleted.
 * @param onConfirmed called after the second confirmation.
 */
@Composable
private fun DeleteAllItem(storedShifts: Int, onConfirmed: () -> Unit) {
    var step by rememberSaveable { mutableStateOf(DeleteAllStep.NONE) }

    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_delete_all), color = MaterialTheme.colorScheme.error) },
        supportingContent = { Text(stringResource(R.string.settings_delete_all_summary)) },
        // Nothing to delete: the item stays visible but does nothing
        modifier = Modifier.clickable(enabled = storedShifts > 0) { step = DeleteAllStep.FIRST },
    )

    when (step) {
        DeleteAllStep.NONE -> Unit
        DeleteAllStep.FIRST -> AlertDialog(
            onDismissRequest = { step = DeleteAllStep.NONE },
            title = { Text(stringResource(R.string.delete_all_title)) },
            text = { Text(stringResource(R.string.delete_all_text, storedShifts)) },
            confirmButton = {
                TextButton(onClick = { step = DeleteAllStep.SECOND }) { Text(stringResource(R.string.delete_all_continue)) }
            },
            dismissButton = { TextButton(onClick = { step = DeleteAllStep.NONE }) { Text(stringResource(R.string.cancel)) } },
        )
        DeleteAllStep.SECOND -> AlertDialog(
            onDismissRequest = { step = DeleteAllStep.NONE },
            title = { Text(stringResource(R.string.delete_all_confirm_title)) },
            text = { Text(stringResource(R.string.delete_all_confirm_text)) },
            confirmButton = {
                TextButton(onClick = {
                    step = DeleteAllStep.NONE
                    onConfirmed()
                }) { Text(stringResource(R.string.delete_all_button), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { step = DeleteAllStep.NONE }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/** Name of a theme option. */
@StringRes
private fun ThemeMode.labelRes(): Int = when (this) {
    ThemeMode.SYSTEM -> R.string.setting_theme_system
    ThemeMode.LIGHT -> R.string.setting_theme_light
    ThemeMode.DARK -> R.string.setting_theme_dark
}

/** Android Studio preview of [SettingsScreen]; not part of the running app. */
@Preview(showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    DockshiftTheme {
        SettingsScreen(
            uiState = SettingsUiState.Ready(ThemeMode.SYSTEM, dynamicColor = true, rotationEnabled = false),
            onBack = {},
            onThemeModeSelected = {},
            onDynamicColorToggled = {},
            onRotationToggled = {},
        )
    }
}
