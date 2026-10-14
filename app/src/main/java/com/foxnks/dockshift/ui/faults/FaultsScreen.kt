package com.foxnks.dockshift.ui.faults

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.foxnks.dockshift.R
import com.foxnks.dockshift.domain.faults.FaultType
import com.foxnks.dockshift.domain.faults.MachineFault
import com.foxnks.dockshift.domain.faults.MachineFaults
import com.foxnks.dockshift.domain.machine.Machine
import com.foxnks.dockshift.domain.machine.MachineCategory
import com.foxnks.dockshift.ui.theme.DockshiftTheme
import java.time.LocalDate

/**
 * Entry point of a machine's fault page.
 *
 * @param machineId the machine, from the navigation key.
 * @param onBack closes the page.
 * @param viewModel provided by Hilt, one per page.
 */
@Composable
fun FaultsRoute(machineId: Long, onBack: () -> Unit, viewModel: FaultsViewModel = hiltViewModel()) {
    LaunchedEffect(machineId) { viewModel.start(machineId) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    FaultsScreen(
        uiState = uiState,
        onBack = onBack,
        onReport = viewModel::onReport,
        onReportedShown = viewModel::onReportedShown,
        onFixed = viewModel::onFixed,
        onDelete = viewModel::onDelete,
    )
}

/**
 * A machine's faults in three parts: the open ones (each with "Fixed"), the faults
 * that can be reported (the machine category's list, as buttons: nothing is typed),
 * and the history of repaired ones with both dates.
 *
 * @param uiState what to show.
 * @param onBack back arrow tapped.
 * @param onReport a fault button tapped.
 * @param onReportedShown the "reported" confirmation was shown.
 * @param onFixed "Fixed" tapped on an open fault.
 * @param onDelete a fault entered by mistake was confirmed for removal.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FaultsScreen(
    uiState: FaultsUiState,
    onBack: () -> Unit,
    onReport: (FaultType) -> Unit = {},
    onReportedShown: () -> Unit = {},
    onFixed: (MachineFault) -> Unit = {},
    onDelete: (MachineFault) -> Unit = {},
) {
    val ready = uiState as? FaultsUiState.Ready
    val snackbar = remember { SnackbarHostState() }
    val reportedText = ready?.reported?.let { stringResource(R.string.faults_reported_message, stringResource(it.labelRes())) }
    LaunchedEffect(ready?.reported) {
        if (reportedText != null) {
            snackbar.showSnackbar(reportedText)
            onReportedShown()
        }
    }
    var deleting by remember { mutableStateOf<MachineFault?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(ready?.machine?.label?.let { stringResource(R.string.faults_title, it) } ?: "") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.navigate_back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { innerPadding ->
        when (uiState) {
            FaultsUiState.Loading -> Box(Modifier.fillMaxSize().padding(innerPadding), Alignment.Center) {
                CircularProgressIndicator()
            }
            FaultsUiState.Missing -> Box(Modifier.fillMaxSize().padding(innerPadding), Alignment.Center) {
                Text(stringResource(R.string.faults_machine_missing))
            }
            is FaultsUiState.Ready -> FaultsContent(
                state = uiState,
                onReport = onReport,
                onFixed = onFixed,
                onDeleteRequested = { deleting = it },
                contentPadding = innerPadding,
            )
        }
    }

    deleting?.let { fault ->
        val name = stringResource(fault.type.labelRes())
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.faults_delete_title, name)) },
            text = { Text(stringResource(R.string.faults_delete_text)) },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(fault)
                    deleting = null
                }) { Text(stringResource(R.string.faults_delete_button), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/** The three parts of the page, in one scrolling list. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FaultsContent(
    state: FaultsUiState.Ready,
    onReport: (FaultType) -> Unit,
    onFixed: (MachineFault) -> Unit,
    onDeleteRequested: (MachineFault) -> Unit,
    contentPadding: PaddingValues,
) {
    LazyColumn(contentPadding = contentPadding) {
        item { MachineHeader(state.machine) }

        item { SectionTitle(R.string.faults_open) }
        if (state.faults.open.isEmpty()) {
            item { QuietText(R.string.faults_none_open) }
        } else {
            items(state.faults.open, key = { "open-${it.id}" }) { fault ->
                ListItem(
                    headlineContent = { Text(stringResource(fault.type.labelRes())) },
                    supportingContent = { Text(stringResource(R.string.faults_since, fault.reportedOn.faultDate())) },
                    leadingContent = {
                        Icon(painterResource(R.drawable.ic_build), contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    },
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { onDeleteRequested(fault) }) {
                                Icon(painterResource(R.drawable.ic_delete), stringResource(R.string.faults_delete))
                            }
                            FilledTonalButton(onClick = { onFixed(fault) }) {
                                Icon(painterResource(R.drawable.ic_check_circle), contentDescription = null, modifier = Modifier.size(18.dp))
                                Text(stringResource(R.string.faults_fixed), modifier = Modifier.padding(start = 6.dp))
                            }
                        }
                    },
                )
            }
        }
        item { HorizontalDivider(Modifier.padding(top = 8.dp)) }

        item { SectionTitle(R.string.faults_report) }
        item {
            if (state.reportable.isEmpty()) {
                QuietText(R.string.faults_all_reported)
            } else {
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.faults_report_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.reportable.forEach { type ->
                            AssistChip(onClick = { onReport(type) }, label = { Text(stringResource(type.labelRes())) })
                        }
                    }
                }
            }
        }
        item { HorizontalDivider(Modifier.padding(top = 8.dp)) }

        item { SectionTitle(R.string.faults_history) }
        if (state.faults.history.isEmpty()) {
            item { QuietText(R.string.faults_history_empty) }
        } else {
            items(state.faults.history, key = { "history-${it.id}" }) { fault ->
                ListItem(
                    headlineContent = { Text(stringResource(fault.type.labelRes())) },
                    supportingContent = {
                        Text(stringResource(R.string.faults_reported_fixed, fault.reportedOn.faultDate(), fault.fixedOn?.faultDate().orEmpty()))
                    },
                    leadingContent = {
                        Icon(painterResource(R.drawable.ic_check_circle), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    },
                    trailingContent = {
                        IconButton(onClick = { onDeleteRequested(fault) }) {
                            Icon(painterResource(R.drawable.ic_delete), stringResource(R.string.faults_delete))
                        }
                    },
                )
            }
        }
    }
}

/** The machine's picture, number and location at the top of the page. */
@Composable
private fun MachineHeader(machine: Machine) {
    Row(
        modifier = Modifier.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(painterResource(machine.category.iconRes()), contentDescription = null, modifier = Modifier.size(48.dp))
        Column {
            Text(machine.label, style = MaterialTheme.typography.headlineSmall)
            machine.location?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Small colored heading above a part of the page. */
@Composable
private fun SectionTitle(@StringRes text: Int) {
    Text(
        text = stringResource(text),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
    )
}

/** A grey line for an empty part. */
@Composable
private fun QuietText(@StringRes text: Int) {
    Text(
        text = stringResource(text),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

/** Android Studio preview of [FaultsScreen]; not part of the running app. */
@Preview(showBackground = true)
@Composable
private fun FaultsScreenPreview() {
    val day = LocalDate.of(2026, 10, 14)
    val machine = Machine(7, MachineCategory.RTG, "12", location = "Μπλοκ 15")
    val faults = MachineFaults.of(
        listOf(
            MachineFault(1, 7, FaultType.LIGHTS, day.minusDays(2)),
            MachineFault(2, 7, FaultType.SPREADER, day.minusDays(20), day.minusDays(18)),
        ),
    )
    DockshiftTheme {
        FaultsScreen(
            uiState = FaultsUiState.Ready(machine, faults, listOf(FaultType.SPREADER, FaultType.TIRE, FaultType.RADIO)),
            onBack = {},
        )
    }
}
