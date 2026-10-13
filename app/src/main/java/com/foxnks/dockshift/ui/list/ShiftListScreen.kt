package com.foxnks.dockshift.ui.list

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.foxnks.dockshift.R
import com.foxnks.dockshift.domain.shift.Shift
import com.foxnks.dockshift.ui.navigation.AppBottomBar
import com.foxnks.dockshift.ui.navigation.TopLevel
import com.foxnks.dockshift.ui.shift.ShiftChip
import com.foxnks.dockshift.ui.shift.ShiftFormat
import com.foxnks.dockshift.ui.shift.shiftSummary
import java.time.LocalDate

/**
 * Entry point of the shift list.
 *
 * @param onEditDay open the shift of a day for adding or editing.
 * @param onSelectTopLevel switch screen from the bottom bar.
 * @param newShiftDate the day the "+" button opens.
 * @param viewModel provided by Hilt, one per screen entry.
 */
@Composable
fun ShiftListRoute(
    onEditDay: (LocalDate) -> Unit,
    onSelectTopLevel: (TopLevel) -> Unit,
    newShiftDate: () -> LocalDate,
    viewModel: ShiftListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ShiftListScreen(
        uiState = uiState,
        onYearSelected = viewModel::onYearSelected,
        onMonthSelected = viewModel::onMonthSelected,
        onEditDay = onEditDay,
        onAdd = { onEditDay(newShiftDate()) },
        onSelectTopLevel = onSelectTopLevel,
    )
}

/** Year and month filters on top, the shifts grouped by month below, newest first. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShiftListScreen(
    uiState: ShiftListUiState,
    onYearSelected: (Int) -> Unit,
    onMonthSelected: (Int?) -> Unit,
    onEditDay: (LocalDate) -> Unit,
    onAdd: () -> Unit,
    onSelectTopLevel: (TopLevel) -> Unit,
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.list_title)) }) },
        bottomBar = { AppBottomBar(selected = TopLevel.SHIFTS, onSelect = onSelectTopLevel) },
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd) {
                Icon(painterResource(R.drawable.ic_add), stringResource(R.string.add_shift))
            }
        },
    ) { innerPadding ->
        when (uiState) {
            ShiftListUiState.Loading -> Box(Modifier.fillMaxSize().padding(innerPadding), Alignment.Center) {
                CircularProgressIndicator()
            }
            is ShiftListUiState.Ready -> Column(Modifier.fillMaxSize().padding(top = innerPadding.calculateTopPadding())) {
                Filters(uiState, onYearSelected, onMonthSelected)
                HorizontalDivider()
                if (uiState.groups.isEmpty()) {
                    Box(Modifier.fillMaxSize().padding(bottom = innerPadding.calculateBottomPadding()), Alignment.Center) {
                        Text(stringResource(R.string.list_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(
                        // Room for the floating button over the last row
                        contentPadding = PaddingValues(bottom = innerPadding.calculateBottomPadding() + 88.dp),
                    ) {
                        uiState.groups.forEach { group ->
                            item(key = "header-${group.month}") {
                                Text(
                                    text = ShiftFormat.month(group.month.year, group.month.monthValue),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
                                )
                            }
                            items(group.shifts, key = { it.date.toString() }) { shift ->
                                ShiftRow(shift, onClick = { onEditDay(shift.date) })
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Two scrollable rows of chips: years, then "All" and the twelve months. */
@Composable
private fun Filters(state: ShiftListUiState.Ready, onYearSelected: (Int) -> Unit, onMonthSelected: (Int?) -> Unit) {
    Column(Modifier.padding(vertical = 4.dp)) {
        ChipRow {
            state.years.forEach { year ->
                FilterChip(
                    selected = year == state.year,
                    onClick = { onYearSelected(year) },
                    label = { Text(year.toString()) },
                )
            }
        }
        ChipRow {
            FilterChip(
                selected = state.month == null,
                onClick = { onMonthSelected(null) },
                label = { Text(stringResource(R.string.list_all_months)) },
            )
            (1..12).forEach { month ->
                FilterChip(
                    selected = month == state.month,
                    onClick = { onMonthSelected(month) },
                    label = { Text(ShiftFormat.monthShort(month)) },
                )
            }
        }
    }
}

/** A horizontally scrolling row with spacing, for chips. */
@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}

/** One shift: date, machines/overtime summary, and the type chip. */
@Composable
private fun ShiftRow(shift: Shift, onClick: () -> Unit) {
    val summary = shiftSummary(shift)
    ListItem(
        headlineContent = { Text(ShiftFormat.short(shift.date)) },
        // Days off have no machines: the slot stays empty
        supportingContent = {
            if (summary.isNotEmpty()) Text(summary, color = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        trailingContent = { ShiftChip(shift.type) },
        modifier = Modifier.clickable(onClick = onClick),
    )
}
