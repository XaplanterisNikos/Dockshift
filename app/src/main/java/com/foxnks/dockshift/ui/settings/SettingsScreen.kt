package com.foxnks.dockshift.ui.settings

import android.os.Build
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.foxnks.dockshift.domain.rotation.RotationAnchor
import com.foxnks.dockshift.domain.rotation.RotationSchedule
import com.foxnks.dockshift.domain.settings.ThemeMode
import com.foxnks.dockshift.domain.settings.WorkSchedule
import com.foxnks.dockshift.domain.shift.ShiftType
import com.foxnks.dockshift.ui.common.DateDialog
import com.foxnks.dockshift.ui.shift.ShiftFormat
import com.foxnks.dockshift.ui.shift.label
import com.foxnks.dockshift.ui.theme.DockshiftTheme
import java.time.LocalDate

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
        onWorkScheduleSelected = viewModel::onWorkScheduleSelected,
        onRotationAnchorSaved = viewModel::onRotationAnchorSaved,
        onRotationAnchorCleared = viewModel::onRotationAnchorCleared,
        onShowHolidaysToggled = viewModel::onShowHolidaysToggled,
    )
}

/**
 * Settings, grouped in sections: Appearance, Schedule. Data (backup, v1 import,
 * delete all) has its own screen in the menu.
 *
 * @param uiState what to show.
 * @param onBack back arrow tapped.
 * @param onThemeModeSelected a theme option was picked.
 * @param onDynamicColorToggled the wallpaper-colors switch was flipped.
 * @param onWorkScheduleSelected a work schedule option was picked.
 * @param onRotationAnchorSaved the rotation place was set: four-day rest start and the shift after it.
 * @param onRotationAnchorCleared the rotation place was removed.
 * @param onShowHolidaysToggled the public holidays switch was flipped.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    onBack: () -> Unit,
    onThemeModeSelected: (ThemeMode) -> Unit,
    onDynamicColorToggled: (Boolean) -> Unit,
    onWorkScheduleSelected: (WorkSchedule) -> Unit = {},
    onRotationAnchorSaved: (LocalDate, ShiftType) -> Unit = { _, _ -> },
    onRotationAnchorCleared: () -> Unit = {},
    onShowHolidaysToggled: (Boolean) -> Unit = {},
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
                WorkScheduleOptions(selected = uiState.workSchedule, onSelected = onWorkScheduleSelected)
                // Only the rotating schedule predicts, so only it needs a place in the cycle
                if (uiState.workSchedule == WorkSchedule.ROTATING) {
                    RotationPlaceItem(uiState, onRotationAnchorSaved, onRotationAnchorCleared)
                }
                SwitchItem(
                    title = R.string.setting_holidays_title,
                    summary = R.string.setting_holidays_summary,
                    checked = uiState.showHolidays,
                    onCheckedChange = onShowHolidaysToggled,
                )
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

/** The work schedule choices as radio buttons; the whole row is the touch target. */
@Composable
private fun WorkScheduleOptions(selected: WorkSchedule, onSelected: (WorkSchedule) -> Unit) {
    Column(Modifier.selectableGroup()) {
        Text(
            text = stringResource(R.string.setting_work_schedule_title),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        WorkSchedule.entries.forEach { schedule ->
            ListItem(
                headlineContent = { Text(stringResource(schedule.labelRes())) },
                leadingContent = { RadioButton(selected = schedule == selected, onClick = null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(selected = schedule == selected, role = Role.RadioButton, onClick = { onSelected(schedule) }),
            )
        }
    }
}

/**
 * "My place in the rotation": the current place (latest four-day rest and the shift
 * after it), or "not set". Tapping opens [RotationPlaceDialog].
 */
@Composable
private fun RotationPlaceItem(
    state: SettingsUiState.Ready,
    onSaved: (LocalDate, ShiftType) -> Unit,
    onCleared: () -> Unit,
) {
    var editing by rememberSaveable { mutableStateOf(false) }
    val anchor = state.rotationAnchor
    ListItem(
        headlineContent = { Text(stringResource(R.string.setting_rotation_place_title)) },
        supportingContent = {
            Text(
                if (anchor == null) {
                    stringResource(R.string.setting_rotation_place_unset)
                } else {
                    stringResource(R.string.setting_rotation_place_value, ShiftFormat.medium(anchor.cycleStart), anchor.firstType.label())
                },
            )
        },
        trailingContent = { Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null) },
        modifier = Modifier.clickable { editing = true },
    )
    if (editing) {
        RotationPlaceDialog(
            // Start from the saved place, or from the one found in the shifts
            initial = anchor ?: state.detectedAnchor,
            fromHistory = anchor == null && state.detectedAnchor != null,
            today = state.today,
            canClear = anchor != null,
            onSave = { start, type ->
                editing = false
                onSaved(start, type)
            },
            onClear = {
                editing = false
                onCleared()
            },
            onDismiss = { editing = false },
        )
    }
}

/**
 * Sets the place in the rotation with two questions: when did your last four-day
 * rest start, and which shift came after it.
 *
 * @param initial the answers to start with (saved or found), or null.
 * @param fromHistory true when [initial] was found from the shifts: say so.
 * @param today the latest day a rest can have started.
 * @param canClear whether a place is saved and can be removed.
 */
@Composable
private fun RotationPlaceDialog(
    initial: RotationAnchor?,
    fromHistory: Boolean,
    today: LocalDate,
    canClear: Boolean,
    onSave: (LocalDate, ShiftType) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    // Kept as text and name so they survive rotation in rememberSaveable
    var start by rememberSaveable { mutableStateOf(initial?.cycleStart?.toString()) }
    var type by rememberSaveable { mutableStateOf(initial?.firstType?.name) }
    var pickDate by rememberSaveable { mutableStateOf(false) }
    val startDate = start?.let { LocalDate.parse(it) }
    val firstType = type?.let { ShiftType.valueOf(it) }
    // A rest that has not started yet cannot be "the last one"
    val valid = startDate != null && firstType != null && startDate <= today

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.setting_rotation_place_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (fromHistory) {
                    Text(
                        text = stringResource(R.string.rotation_place_found),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
                Text(stringResource(R.string.rotation_place_question_rest), style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = { pickDate = true }) {
                    Text(startDate?.let { ShiftFormat.long(it) } ?: stringResource(R.string.rotation_place_pick_date))
                }
                if (startDate != null && startDate > today) {
                    Text(
                        text = stringResource(R.string.rotation_place_future),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Text(stringResource(R.string.rotation_place_question_type), style = MaterialTheme.typography.bodyMedium)
                val shiftColors = DockshiftTheme.shiftColors
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RotationSchedule.TYPE_ORDER.forEach { option ->
                        FilterChip(
                            selected = option == firstType,
                            onClick = { type = option.name },
                            label = { Text(option.label()) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = shiftColors.container(option),
                                selectedLabelColor = shiftColors.onContainer(option),
                            ),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (startDate != null && firstType != null) onSave(startDate, firstType) },
                enabled = valid,
            ) { Text(stringResource(R.string.edit_save)) }
        },
        dismissButton = {
            Row {
                if (canClear) {
                    TextButton(onClick = onClear) {
                        Text(stringResource(R.string.rotation_place_clear), color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
    )

    if (pickDate) {
        DateDialog(
            initial = startDate ?: today,
            onPicked = {
                start = it.toString()
                pickDate = false
            },
            onDismiss = { pickDate = false },
        )
    }
}

/** Name of a work schedule option. */
@StringRes
private fun WorkSchedule.labelRes(): Int = when (this) {
    WorkSchedule.ROTATING -> R.string.work_schedule_rotating
    WorkSchedule.MORNING_ONLY -> R.string.work_schedule_morning_only
    WorkSchedule.AFTERNOON_ONLY -> R.string.work_schedule_afternoon_only
    WorkSchedule.MORNING_AFTERNOON -> R.string.work_schedule_morning_afternoon
    WorkSchedule.AFTERNOON_NIGHT -> R.string.work_schedule_afternoon_night
    WorkSchedule.NIGHT_ONLY -> R.string.work_schedule_night_only
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
            uiState = SettingsUiState.Ready(ThemeMode.SYSTEM, dynamicColor = true, today = LocalDate.of(2026, 10, 13)),
            onBack = {},
            onThemeModeSelected = {},
            onDynamicColorToggled = {},
        )
    }
}
