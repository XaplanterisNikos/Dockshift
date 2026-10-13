package com.foxnks.dockshift.ui.machines

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.foxnks.dockshift.R
import com.foxnks.dockshift.domain.machine.MachineForm

/**
 * Bottom sheet to add a machine or edit one: category (new machines only), number,
 * location with suggestions, active switch, and "delete for good" for machines that
 * no shift used.
 *
 * @param state the open sheet.
 * @param locations every location in use, offered as suggestions.
 * @param actions the screen's actions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MachineSheet(state: MachineFormState, locations: List<String>, actions: MachinesActions) {
    val form = state.form
    val original = state.original

    ModalBottomSheet(
        onDismissRequest = actions.onSheetDismissed,
        // Open fully: the keyboard needs the room
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = original?.label ?: stringResource(R.string.machines_new),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            // The category of a stored machine is fixed: a wrong one means a new machine
            if (form.isNew) {
                CategoryChips(selected = form.category, onSelected = { actions.onFormChanged(form.copy(category = it)) })
            }

            OutlinedTextField(
                value = form.number,
                onValueChange = { actions.onFormChanged(form.copy(number = it)) },
                label = { Text(stringResource(R.string.machines_number)) },
                singleLine = true,
                isError = state.error != null,
                supportingText = { state.error?.let { Text(errorText(it)) } },
                // Fleet numbers are digits: open the number pad
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            )

            OutlinedTextField(
                value = form.location,
                onValueChange = { actions.onFormChanged(form.copy(location = it)) },
                label = { Text(stringResource(R.string.machines_location)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            )
            LocationSuggestions(
                typed = form.location,
                locations = locations,
                onPick = { actions.onFormChanged(form.copy(location = it)) },
            )

            if (original != null) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.machines_active)) },
                    supportingContent = { Text(stringResource(R.string.machines_active_summary)) },
                    // onCheckedChange = null: the row handles the tap
                    trailingContent = { Switch(checked = form.active, onCheckedChange = null) },
                    modifier = Modifier.toggleable(
                        value = form.active,
                        role = Role.Switch,
                        onValueChange = { actions.onFormChanged(form.copy(active = it)) },
                    ),
                )
                if (state.shifts > 0) {
                    Text(
                        text = pluralStringResource(R.plurals.machines_history_note, state.shifts, state.shifts),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Only machines without history can disappear completely
                if (original != null && state.shifts == 0) {
                    TextButton(onClick = actions.onDeleteRequested) {
                        Text(stringResource(R.string.machines_delete), color = MaterialTheme.colorScheme.error)
                    }
                }
                Spacer(Modifier.weight(1f))
                Button(onClick = actions.onSave) { Text(stringResource(R.string.machines_save)) }
            }
        }
    }

    if (state.confirmDelete && original != null) {
        AlertDialog(
            onDismissRequest = actions.onDeleteCancelled,
            title = { Text(stringResource(R.string.machines_delete_title, original.label)) },
            text = { Text(stringResource(R.string.machines_delete_text)) },
            confirmButton = {
                TextButton(onClick = actions.onDeleteConfirmed) {
                    Text(stringResource(R.string.machines_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = actions.onDeleteCancelled) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/**
 * Locations already in use that contain what was typed, as chips; tapping one fills
 * the field, so the same place is always written the same way.
 */
@Composable
private fun LocationSuggestions(typed: String, locations: List<String>, onPick: (String) -> Unit) {
    val text = typed.trim()
    val matching = locations
        .filter { it.contains(text, ignoreCase = true) && !it.equals(text, ignoreCase = true) }
        .take(MAX_SUGGESTIONS)
    if (matching.isEmpty()) return
    Row(
        modifier = Modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        matching.forEach { location ->
            SuggestionChip(onClick = { onPick(location) }, label = { Text(location) })
        }
    }
}

/** Text of a form error. */
@Composable
private fun errorText(error: MachineFormError): String = when (error) {
    MachineFormError.BlankNumber -> stringResource(R.string.machines_error_blank)
    MachineFormError.NumberTooLong -> stringResource(R.string.machines_error_too_long, MachineForm.MAX_NUMBER_LENGTH)
    is MachineFormError.Duplicate ->
        if (error.existing.active) {
            stringResource(R.string.machines_error_duplicate, error.existing.label)
        } else {
            stringResource(R.string.machines_error_duplicate_inactive, error.existing.label)
        }
}

/** Most location suggestions shown at once. */
private const val MAX_SUGGESTIONS = 8
