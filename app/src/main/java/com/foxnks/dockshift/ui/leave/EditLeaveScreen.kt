package com.foxnks.dockshift.ui.leave

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.foxnks.dockshift.R
import com.foxnks.dockshift.domain.leave.LeaveProblem
import com.foxnks.dockshift.ui.common.DateDialog
import com.foxnks.dockshift.ui.shift.ShiftFormat
import java.time.LocalDate

/**
 * Entry point of the add/edit leave screen.
 *
 * @param id the leave to edit, or 0 for a new one.
 * @param day the first day of a new leave, or null.
 * @param onDone called after saving, deleting, or tapping back: closes the screen.
 * @param viewModel provided by Hilt, one per screen entry.
 */
@Composable
fun EditLeaveRoute(id: Long, day: LocalDate?, onDone: () -> Unit, viewModel: EditLeaveViewModel = hiltViewModel()) {
    // Runs once per screen entry; the ViewModel ignores repeats
    LaunchedEffect(id) { viewModel.start(id, day) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val editing = uiState as? EditLeaveUiState.Editing
    // Saved or deleted: leave the screen
    LaunchedEffect(editing?.finished) { if (editing?.finished == true) onDone() }

    EditLeaveScreen(
        uiState = uiState,
        onBack = onDone,
        onSave = viewModel::onSave,
        onDelete = viewModel::onDeleteRequested,
        onStartSelected = viewModel::onStartSelected,
        onEndSelected = viewModel::onEndSelected,
        onNoteChanged = viewModel::onNoteChanged,
        onConfirmReplace = viewModel::onConfirmReplace,
        onCancelReplace = viewModel::onCancelReplace,
        onConfirmDelete = viewModel::onConfirmDelete,
        onCancelDelete = viewModel::onCancelDelete,
    )
}

/**
 * The leave form: a name (optional), first and last day, and what it costs: the
 * days that count (closed-port holidays left out) and the days left in the year.
 * Save in the top bar; delete for stored leaves.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditLeaveScreen(
    uiState: EditLeaveUiState,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onStartSelected: (LocalDate) -> Unit,
    onEndSelected: (LocalDate) -> Unit,
    onNoteChanged: (String) -> Unit,
    onConfirmReplace: () -> Unit,
    onCancelReplace: () -> Unit,
    onConfirmDelete: () -> Unit,
    onCancelDelete: () -> Unit,
) {
    val editing = uiState as? EditLeaveUiState.Editing
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (editing?.isExisting == true) R.string.leave_edit_title else R.string.leave_new_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.navigate_back))
                    }
                },
                actions = {
                    if (editing?.isExisting == true) {
                        IconButton(onClick = onDelete) {
                            Icon(painterResource(R.drawable.ic_delete), stringResource(R.string.edit_delete))
                        }
                    }
                    // Nothing can be saved while there is a problem
                    TextButton(onClick = onSave, enabled = editing != null && editing.problem == null) {
                        Text(stringResource(R.string.edit_save))
                    }
                },
            )
        },
    ) { innerPadding ->
        if (editing == null) {
            Box(Modifier.fillMaxSize().padding(innerPadding), Alignment.Center) { CircularProgressIndicator() }
        } else {
            LeaveForm(
                editing = editing,
                onStartSelected = onStartSelected,
                onEndSelected = onEndSelected,
                onNoteChanged = onNoteChanged,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .imePadding()
                    .verticalScroll(rememberScrollState()),
            )
            editing.confirmReplace?.let { count ->
                AlertDialog(
                    onDismissRequest = onCancelReplace,
                    title = { Text(stringResource(R.string.leave_replace_title)) },
                    text = { Text(pluralStringResource(R.plurals.leave_replace_text, count, count)) },
                    confirmButton = { TextButton(onClick = onConfirmReplace) { Text(stringResource(R.string.edit_replace)) } },
                    dismissButton = { TextButton(onClick = onCancelReplace) { Text(stringResource(R.string.cancel)) } },
                )
            }
            if (editing.confirmDelete) {
                AlertDialog(
                    onDismissRequest = onCancelDelete,
                    title = { Text(stringResource(R.string.leave_delete_title)) },
                    text = { Text(stringResource(R.string.leave_delete_text)) },
                    confirmButton = {
                        TextButton(onClick = onConfirmDelete) {
                            Text(stringResource(R.string.edit_delete), color = MaterialTheme.colorScheme.error)
                        }
                    },
                    dismissButton = { TextButton(onClick = onCancelDelete) { Text(stringResource(R.string.cancel)) } },
                )
            }
        }
    }
}

/** The fields: name, first day, last day, then the cost of the leave and any problem. */
@Composable
private fun LeaveForm(
    editing: EditLeaveUiState.Editing,
    onStartSelected: (LocalDate) -> Unit,
    onEndSelected: (LocalDate) -> Unit,
    onNoteChanged: (String) -> Unit,
    modifier: Modifier,
) {
    // Which date dialog is open, if any
    var picking by rememberSaveable { mutableStateOf<String?>(null) }

    Column(modifier) {
        OutlinedTextField(
            value = editing.note,
            onValueChange = onNoteChanged,
            label = { Text(stringResource(R.string.leave_note)) },
            placeholder = { Text(stringResource(R.string.leave_note_hint)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        )
        DateRow(stringResource(R.string.leave_from), editing.start, onChange = { picking = PICK_START })
        HorizontalDivider()
        DateRow(stringResource(R.string.leave_to), editing.end, onChange = { picking = PICK_END })
        HorizontalDivider()

        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when (val problem = editing.problem) {
                LeaveProblem.EndBeforeStart -> ProblemText(stringResource(R.string.leave_problem_end_before_start))
                is LeaveProblem.Overlaps -> ProblemText(
                    stringResource(
                        R.string.leave_problem_overlap,
                        problem.other.note ?: stringResource(R.string.shift_type_annual_leave),
                        leaveDates(problem.other),
                    ),
                )
                null -> CostCard(editing)
            }
        }
    }

    when (picking) {
        PICK_START -> DateDialog(
            initial = editing.start,
            onPicked = {
                onStartSelected(it)
                picking = null
            },
            onDismiss = { picking = null },
        )
        PICK_END -> DateDialog(
            initial = editing.end,
            onPicked = {
                onEndSelected(it)
                picking = null
            },
            onDismiss = { picking = null },
        )
    }
}

/** One date of the leave with its "Change" button. */
@Composable
private fun DateRow(label: String, date: LocalDate, onChange: () -> Unit) {
    ListItem(
        overlineContent = { Text(label) },
        headlineContent = { Text(ShiftFormat.long(date)) },
        trailingContent = { OutlinedButton(onClick = onChange) { Text(stringResource(R.string.edit_change)) } },
    )
}

/** What the leave costs: counted days, closed-port holidays left out, and the days left. */
@Composable
private fun CostCard(editing: EditLeaveUiState.Editing) {
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = pluralStringResource(R.plurals.leave_counts, editing.countedDays, editing.countedDays),
                style = MaterialTheme.typography.titleMedium,
            )
            if (editing.closedDays > 0) {
                Text(
                    text = pluralStringResource(R.plurals.leave_closed_days, editing.closedDays, editing.closedDays),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            editing.remainingAfter?.let { left ->
                Text(
                    text = if (left >= 0) {
                        stringResource(R.string.leave_left_after, editing.start.year, left)
                    } else {
                        stringResource(R.string.leave_over_by, -left)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    // More than the entitlement is allowed, but shown in red
                    color = if (left >= 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

/** A problem that keeps the leave from being saved, in red. */
@Composable
private fun ProblemText(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
}

/** Value of the open date dialog: the first day. */
private const val PICK_START = "start"

/** Value of the open date dialog: the last day. */
private const val PICK_END = "end"
