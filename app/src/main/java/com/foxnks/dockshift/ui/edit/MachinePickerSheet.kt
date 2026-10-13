package com.foxnks.dockshift.ui.edit

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.foxnks.dockshift.R
import com.foxnks.dockshift.domain.machine.Machine
import com.foxnks.dockshift.domain.machine.MachineCategory

/**
 * Bottom sheet for picking a machine: category chips, a number search (there are
 * 182 trucks), and the matching active machines with their location.
 *
 * @param machines the active machines.
 * @param initialCategory category selected when the sheet opens.
 * @param onPick called with the tapped machine; the sheet then closes.
 * @param onDismiss called when the sheet is closed without picking.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MachinePickerSheet(
    machines: List<Machine>,
    initialCategory: MachineCategory,
    onPick: (Machine) -> Unit,
    onDismiss: () -> Unit,
) {
    // Survive rotation while the sheet is open
    var category by rememberSaveable { mutableStateOf(initialCategory) }
    var query by rememberSaveable { mutableStateOf("") }
    val matching = machines.filter { it.category == category && it.number.contains(query.trim()) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        // Open fully: the list needs the room
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.padding(bottom = 16.dp)) {
            Text(
                text = stringResource(R.string.picker_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MachineCategory.entries.forEach { item ->
                    FilterChip(
                        selected = item == category,
                        onClick = { category = item },
                        label = { Text(item.name) },
                    )
                }
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text(stringResource(R.string.picker_search)) },
                singleLine = true,
                // Fleet numbers are digits: open the number pad
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            )
            if (matching.isEmpty()) {
                Text(
                    text = stringResource(R.string.picker_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            } else {
                LazyColumn(Modifier.heightIn(max = 480.dp)) {
                    items(matching, key = { it.id }) { machine ->
                        ListItem(
                            headlineContent = { Text(machine.label) },
                            supportingContent = { machine.location?.let { Text(it) } },
                            modifier = Modifier.clickable { onPick(machine) },
                        )
                    }
                }
            }
        }
    }
}
