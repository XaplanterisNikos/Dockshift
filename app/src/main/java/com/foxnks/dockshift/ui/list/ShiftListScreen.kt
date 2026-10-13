package com.foxnks.dockshift.ui.list

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.foxnks.dockshift.R
import com.foxnks.dockshift.ui.common.PeriodFilters
import com.foxnks.dockshift.ui.navigation.AppBottomBar
import com.foxnks.dockshift.ui.navigation.TopLevel
import com.foxnks.dockshift.ui.shift.DayCard
import com.foxnks.dockshift.ui.shift.MiniDayCard
import com.foxnks.dockshift.ui.shift.ShiftFormat
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

/**
 * Year and month filters on top, the shifts grouped by month below, newest first:
 * small day cards upright, rows of mini cards sideways. Public holidays are named.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
        val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
        when (uiState) {
            ShiftListUiState.Loading -> Box(Modifier.fillMaxSize().padding(innerPadding), Alignment.Center) {
                CircularProgressIndicator()
            }
            is ShiftListUiState.Ready -> Column(Modifier.fillMaxSize().padding(top = innerPadding.calculateTopPadding())) {
                PeriodFilters(uiState.years, uiState.year, uiState.month, onYearSelected, onMonthSelected)
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
                            if (landscape) {
                                // Sideways: the month's days as mini cards, wrapping into rows
                                item(key = "cards-${group.month}") {
                                    FlowRow(
                                        modifier = Modifier.padding(horizontal = 16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        group.shifts.forEach { shift ->
                                            MiniDayCard(
                                                date = shift.date,
                                                shift = shift,
                                                onClick = { onEditDay(shift.date) },
                                                modifier = Modifier.width(76.dp),
                                                holiday = uiState.holidays[shift.date],
                                            )
                                        }
                                    }
                                }
                            } else {
                                // Upright: one small card per day
                                items(group.shifts, key = { it.date.toString() }) { shift ->
                                    DayCard(
                                        date = shift.date,
                                        shift = shift,
                                        onClick = { onEditDay(shift.date) },
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                                        holiday = uiState.holidays[shift.date],
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
