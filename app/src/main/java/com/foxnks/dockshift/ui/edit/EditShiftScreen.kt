package com.foxnks.dockshift.ui.edit

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.foxnks.dockshift.R
import com.foxnks.dockshift.domain.machine.Machine
import com.foxnks.dockshift.domain.machine.MachineCategory
import com.foxnks.dockshift.domain.shift.MachineAssignment
import com.foxnks.dockshift.domain.shift.ShiftDraft
import com.foxnks.dockshift.domain.shift.ShiftType
import com.foxnks.dockshift.ui.shift.MachineLine
import com.foxnks.dockshift.ui.shift.ShiftFormat
import com.foxnks.dockshift.ui.shift.label
import com.foxnks.dockshift.ui.theme.DockshiftTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Entry point of the add/edit shift screen.
 *
 * @param date the day to edit, from the navigation key.
 * @param onDone called after saving, deleting, or tapping back: closes the screen.
 * @param viewModel provided by Hilt, one per screen entry.
 */
@Composable
fun EditShiftRoute(date: LocalDate, onDone: () -> Unit, viewModel: EditShiftViewModel = hiltViewModel()) {
    // Runs once per screen entry; the ViewModel ignores repeats
    LaunchedEffect(date) { viewModel.start(date) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val machines by viewModel.activeMachines.collectAsStateWithLifecycle()

    val editing = uiState as? EditShiftUiState.Editing
    // Saved or deleted: leave the screen
    LaunchedEffect(editing?.finished) { if (editing?.finished == true) onDone() }

    EditShiftScreen(
        uiState = uiState,
        machines = machines,
        actions = EditShiftActions(
            onBack = onDone,
            onSave = viewModel::onSave,
            onDelete = viewModel::onDeleteRequested,
            onDateSelected = viewModel::onDateSelected,
            onTypeSelected = viewModel::onTypeSelected,
            onOvertimeToggled = viewModel::onOvertimeToggled,
            onMachineAdded = viewModel::onMachineAdded,
            onMachineRemoved = viewModel::onMachineRemoved,
            onMachineMovedUp = viewModel::onMachineMovedUp,
            onMachineMovedDown = viewModel::onMachineMovedDown,
            onMachineOvertimeToggled = viewModel::onMachineOvertimeToggled,
            onCommentsChanged = viewModel::onCommentsChanged,
            onConfirmReplace = viewModel::onConfirmReplace,
            onCancelReplace = viewModel::onCancelReplace,
            onConfirmDelete = viewModel::onConfirmDelete,
            onCancelDelete = viewModel::onCancelDelete,
        ),
    )
}

/** Every user action of the edit screen, bundled to keep the screen's signature short. */
data class EditShiftActions(
    val onBack: () -> Unit,
    val onSave: () -> Unit,
    val onDelete: () -> Unit,
    val onDateSelected: (LocalDate) -> Unit,
    val onTypeSelected: (ShiftType) -> Unit,
    val onOvertimeToggled: (Boolean) -> Unit,
    val onMachineAdded: (Machine) -> Unit,
    val onMachineRemoved: (Int) -> Unit,
    val onMachineMovedUp: (Int) -> Unit,
    val onMachineMovedDown: (Int) -> Unit,
    val onMachineOvertimeToggled: (Int) -> Unit,
    val onCommentsChanged: (String) -> Unit,
    val onConfirmReplace: () -> Unit,
    val onCancelReplace: () -> Unit,
    val onConfirmDelete: () -> Unit,
    val onCancelDelete: () -> Unit,
)

/**
 * The form: date, type, overtime, machines (in order, each regular or overtime),
 * comment. Save in the top bar; delete for stored shifts.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditShiftScreen(uiState: EditShiftUiState, machines: List<Machine>, actions: EditShiftActions) {
    val editing = uiState as? EditShiftUiState.Editing
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(if (editing?.isExisting == true) R.string.edit_title_edit else R.string.edit_title_new),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = actions.onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.navigate_back))
                    }
                },
                actions = {
                    if (editing?.isExisting == true) {
                        IconButton(onClick = actions.onDelete) {
                            Icon(painterResource(R.drawable.ic_delete), stringResource(R.string.edit_delete))
                        }
                    }
                    TextButton(onClick = actions.onSave, enabled = editing != null) {
                        Text(stringResource(R.string.edit_save))
                    }
                },
            )
        },
    ) { innerPadding ->
        if (editing == null) {
            Box(Modifier.fillMaxSize().padding(innerPadding), Alignment.Center) { CircularProgressIndicator() }
        } else {
            EditForm(
                draft = editing.draft,
                machines = machines,
                actions = actions,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    // Keep the comment field above the keyboard
                    .imePadding()
                    .verticalScroll(rememberScrollState()),
            )
            Dialogs(editing, actions)
        }
    }
}

/** The fields of the form. */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun EditForm(draft: ShiftDraft, machines: List<Machine>, actions: EditShiftActions, modifier: Modifier) {
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var showMachinePicker by rememberSaveable { mutableStateOf(false) }

    Column(modifier) {
        // Date
        ListItem(
            headlineContent = { Text(ShiftFormat.long(draft.date)) },
            overlineContent = { Text(stringResource(R.string.edit_date)) },
            trailingContent = {
                OutlinedButton(onClick = { showDatePicker = true }) { Text(stringResource(R.string.edit_change)) }
            },
        )
        HorizontalDivider()

        // Type: one chip per type, colored like the type when selected
        SectionTitle(R.string.edit_type)
        val shiftColors = DockshiftTheme.shiftColors
        FlowRow(
            modifier = Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ShiftType.entries.forEach { type ->
                FilterChip(
                    selected = type == draft.type,
                    onClick = { actions.onTypeSelected(type) },
                    label = { Text(type.label()) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = shiftColors.container(type),
                        selectedLabelColor = shiftColors.onContainer(type),
                    ),
                )
            }
        }

        // Overtime and machines exist only on working shifts
        if (draft.allowsMachines) {
            HorizontalDivider(Modifier.padding(top = 8.dp))
            ListItem(
                headlineContent = { Text(stringResource(R.string.edit_overtime)) },
                trailingContent = { Switch(checked = draft.overtime, onCheckedChange = null) },
                modifier = Modifier.toggleable(
                    value = draft.overtime,
                    role = Role.Switch,
                    onValueChange = actions.onOvertimeToggled,
                ),
            )
            HorizontalDivider()

            SectionTitle(R.string.edit_machines)
            if (draft.machines.isEmpty()) {
                Text(
                    text = stringResource(R.string.edit_no_machines),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            draft.machines.forEachIndexed { index, assignment ->
                MachineRow(
                    assignment = assignment,
                    index = index,
                    count = draft.machines.size,
                    shiftHasOvertime = draft.overtime,
                    actions = actions,
                )
            }
            OutlinedButton(
                onClick = { showMachinePicker = true },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Icon(painterResource(R.drawable.ic_add), contentDescription = null)
                Text(stringResource(R.string.edit_add_machine), Modifier.padding(start = 8.dp))
            }
        }
        HorizontalDivider(Modifier.padding(top = 8.dp))

        // Comment
        OutlinedTextField(
            value = draft.comments,
            onValueChange = actions.onCommentsChanged,
            label = { Text(stringResource(R.string.edit_comments)) },
            minLines = 3,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        )
    }

    if (showDatePicker) {
        DateDialog(
            initial = draft.date,
            onPicked = {
                actions.onDateSelected(it)
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false },
        )
    }
    if (showMachinePicker) {
        MachinePickerSheet(
            machines = machines,
            // Start where the last machine was: overtime is often on the same kind
            initialCategory = draft.machines.lastOrNull()?.machine?.category ?: MachineCategory.RTG,
            onPick = {
                actions.onMachineAdded(it)
                showMachinePicker = false
            },
            onDismiss = { showMachinePicker = false },
        )
    }
}

/**
 * One machine of the form, with its controls: overtime mark (when the shift has
 * overtime), move up/down, remove.
 *
 * @param assignment the machine and its overtime mark.
 * @param index its position in the shift.
 * @param count how many machines the shift has (to disable "move down" on the last).
 * @param shiftHasOvertime whether the overtime mark can be set.
 * @param actions the screen's actions.
 */
@Composable
private fun MachineRow(
    assignment: MachineAssignment,
    index: Int,
    count: Int,
    shiftHasOvertime: Boolean,
    actions: EditShiftActions,
) {
    Row(
        modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The machine text takes the free width; the controls keep their size
        MachineLine(assignment, Modifier.weight(1f))
        if (shiftHasOvertime) {
            FilterChip(
                selected = assignment.duringOvertime,
                onClick = { actions.onMachineOvertimeToggled(index) },
                label = { Text(stringResource(R.string.overtime)) },
            )
        }
        IconButton(onClick = { actions.onMachineMovedUp(index) }, enabled = index > 0) {
            Icon(painterResource(R.drawable.ic_arrow_upward), stringResource(R.string.edit_move_up))
        }
        IconButton(onClick = { actions.onMachineMovedDown(index) }, enabled = index < count - 1) {
            Icon(painterResource(R.drawable.ic_arrow_downward), stringResource(R.string.edit_move_down))
        }
        IconButton(onClick = { actions.onMachineRemoved(index) }) {
            Icon(painterResource(R.drawable.ic_close), stringResource(R.string.edit_remove_machine))
        }
    }
}

/** Material 3 date picker; the picker works in UTC milliseconds, converted here. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateDialog(initial: LocalDate, onPicked: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { millis ->
                    onPicked(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                } ?: onDismiss()
            }) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    ) {
        DatePicker(state = state)
    }
}

/** The replace and delete questions. */
@Composable
private fun Dialogs(editing: EditShiftUiState.Editing, actions: EditShiftActions) {
    editing.confirmReplace?.let { day ->
        AlertDialog(
            onDismissRequest = actions.onCancelReplace,
            title = { Text(stringResource(R.string.edit_replace_title)) },
            text = { Text(stringResource(R.string.edit_replace_text, ShiftFormat.medium(day))) },
            confirmButton = { TextButton(onClick = actions.onConfirmReplace) { Text(stringResource(R.string.edit_replace)) } },
            dismissButton = { TextButton(onClick = actions.onCancelReplace) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (editing.confirmDelete) {
        val day = editing.originalDate ?: editing.draft.date
        AlertDialog(
            onDismissRequest = actions.onCancelDelete,
            title = { Text(stringResource(R.string.edit_delete_title)) },
            text = { Text(stringResource(R.string.edit_delete_text, ShiftFormat.medium(day))) },
            confirmButton = { TextButton(onClick = actions.onConfirmDelete) { Text(stringResource(R.string.edit_delete)) } },
            dismissButton = { TextButton(onClick = actions.onCancelDelete) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/** Small colored heading above a group of fields. */
@Composable
private fun SectionTitle(@StringRes text: Int) {
    Text(
        text = stringResource(text),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp),
    )
}
