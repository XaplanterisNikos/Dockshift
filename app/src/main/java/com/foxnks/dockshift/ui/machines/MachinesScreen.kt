package com.foxnks.dockshift.ui.machines

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.foxnks.dockshift.R
import com.foxnks.dockshift.domain.machine.Machine
import com.foxnks.dockshift.domain.machine.MachineCategory
import com.foxnks.dockshift.domain.machine.MachineForm
import com.foxnks.dockshift.ui.theme.DockshiftTheme

/**
 * Entry point of the machines screen.
 *
 * @param onBack back arrow tapped: returns to the menu.
 * @param viewModel provided by Hilt, one per screen entry.
 */
@Composable
fun MachinesRoute(onBack: () -> Unit, viewModel: MachinesViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    MachinesScreen(
        uiState = uiState,
        actions = MachinesActions(
            onBack = onBack,
            onCategorySelected = viewModel::onCategorySelected,
            onQueryChanged = viewModel::onQueryChanged,
            onAdd = viewModel::onAddClicked,
            onMachineClicked = viewModel::onMachineClicked,
            onFormChanged = viewModel::onFormChanged,
            onSave = viewModel::onSave,
            onSheetDismissed = viewModel::onSheetDismissed,
            onDeleteRequested = viewModel::onDeleteRequested,
            onDeleteCancelled = viewModel::onDeleteCancelled,
            onDeleteConfirmed = viewModel::onDeleteConfirmed,
            onMessageShown = viewModel::onMessageShown,
        ),
    )
}

/** Every user action of the machines screen, bundled to keep signatures short. */
data class MachinesActions(
    val onBack: () -> Unit,
    val onCategorySelected: (MachineCategory) -> Unit,
    val onQueryChanged: (String) -> Unit,
    val onAdd: () -> Unit,
    val onMachineClicked: (MachineRow) -> Unit,
    val onFormChanged: (MachineForm) -> Unit,
    val onSave: () -> Unit,
    val onSheetDismissed: () -> Unit,
    val onDeleteRequested: () -> Unit,
    val onDeleteCancelled: () -> Unit,
    val onDeleteConfirmed: () -> Unit,
    val onMessageShown: () -> Unit,
)

/**
 * The machine list of one category, searchable by number or location. Inactive
 * machines stay listed, faded, so they can be found and turned back on.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MachinesScreen(uiState: MachinesUiState, actions: MachinesActions) {
    val snackbarHostState = remember { SnackbarHostState() }
    val ready = uiState as? MachinesUiState.Ready

    // A confirmation ("RTG 46 added") is shown once, then cleared in the ViewModel
    val message = ready?.message
    val messageText = message?.let { textOf(it) }
    LaunchedEffect(message) {
        if (messageText != null) {
            snackbarHostState.showSnackbar(messageText)
            actions.onMessageShown()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.menu_machines)) },
                navigationIcon = {
                    IconButton(onClick = actions.onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.navigate_back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (ready != null) {
                FloatingActionButton(onClick = actions.onAdd) {
                    Icon(painterResource(R.drawable.ic_add), stringResource(R.string.machines_add))
                }
            }
        },
    ) { innerPadding ->
        if (ready == null) {
            Box(Modifier.fillMaxSize().padding(innerPadding), Alignment.Center) { CircularProgressIndicator() }
        } else {
            Column(Modifier.fillMaxSize().padding(top = innerPadding.calculateTopPadding())) {
                CategoryChips(selected = ready.category, onSelected = actions.onCategorySelected)
                OutlinedTextField(
                    value = ready.query,
                    onValueChange = actions.onQueryChanged,
                    label = { Text(stringResource(R.string.machines_search)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                )
                HorizontalDivider(Modifier.padding(top = 8.dp))
                if (ready.rows.isEmpty()) {
                    Text(
                        text = stringResource(R.string.picker_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                } else {
                    LazyColumn(
                        // Room for the floating button over the last row
                        contentPadding = PaddingValues(bottom = innerPadding.calculateBottomPadding() + 88.dp),
                    ) {
                        items(ready.rows, key = { it.machine.id }) { row ->
                            MachineListRow(row, onClick = { actions.onMachineClicked(row) })
                        }
                    }
                }
            }
        }
    }

    ready?.sheet?.let { sheet ->
        MachineSheet(state = sheet, locations = ready.locations, actions = actions)
    }
}

/** The six categories as a scrolling row of chips. */
@Composable
internal fun CategoryChips(selected: MachineCategory, onSelected: (MachineCategory) -> Unit) {
    Row(
        modifier = Modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MachineCategory.entries.forEach { item ->
            FilterChip(
                selected = item == selected,
                onClick = { onSelected(item) },
                label = { Text(item.name) },
            )
        }
    }
}

/** One machine: label, location, and in how many shifts it was driven. Faded when inactive. */
@Composable
private fun MachineListRow(row: MachineRow, onClick: () -> Unit) {
    val machine = row.machine
    ListItem(
        headlineContent = {
            Text(
                if (machine.active) machine.label else stringResource(R.string.machines_inactive_label, machine.label),
            )
        },
        supportingContent = {
            Text(
                text = machine.location ?: stringResource(R.string.machines_no_location),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingContent = {
            Text(
                text = pluralStringResource(R.plurals.machines_used_in, row.shifts, row.shifts),
                style = MaterialTheme.typography.labelMedium,
            )
        },
        modifier = Modifier
            .clickable(onClick = onClick)
            // Inactive machines stay findable, but clearly out of service
            .alpha(if (machine.active) 1f else INACTIVE_ALPHA),
    )
}

/** Text of a one-time confirmation. */
@Composable
private fun textOf(message: MachineMessage): String = when (message) {
    is MachineMessage.Added -> stringResource(R.string.machines_added, message.label)
    is MachineMessage.Reactivated -> stringResource(R.string.machines_reactivated, message.label)
    is MachineMessage.Deleted -> stringResource(R.string.machines_deleted, message.label)
}

/** Opacity of inactive machines in the list. */
private const val INACTIVE_ALPHA = 0.55f

/** Android Studio preview of [MachinesScreen]; not part of the running app. */
@Preview(showBackground = true)
@Composable
private fun MachinesScreenPreview() {
    val rows = listOf(
        MachineRow(Machine(1, MachineCategory.RTG, "12", location = "Προβλήτα ΙΙ Μπλόκ 15"), shifts = 23),
        MachineRow(Machine(2, MachineCategory.RTG, "13", active = false), shifts = 0),
    )
    DockshiftTheme {
        MachinesScreen(
            uiState = MachinesUiState.Ready(MachineCategory.RTG, "", rows, emptyList(), sheet = null, message = null),
            actions = MachinesActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}),
        )
    }
}
