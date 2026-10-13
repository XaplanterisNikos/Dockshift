package com.foxnks.dockshift.ui.today

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.foxnks.dockshift.R
import com.foxnks.dockshift.domain.shift.Shift
import com.foxnks.dockshift.ui.navigation.AppBottomBar
import com.foxnks.dockshift.ui.navigation.TopLevel
import com.foxnks.dockshift.ui.shift.MachineLine
import com.foxnks.dockshift.ui.shift.ShiftChip
import com.foxnks.dockshift.ui.shift.ShiftFormat
import com.foxnks.dockshift.ui.shift.shiftSummary
import java.time.LocalDate

/**
 * Entry point of the "Today" screen.
 *
 * @param onEditDay open the shift of a day for adding or editing.
 * @param onOpenSettings open Settings.
 * @param onSelectTopLevel switch screen from the bottom bar.
 * @param viewModel provided by Hilt, one per screen entry.
 */
@Composable
fun TodayRoute(
    onEditDay: (LocalDate) -> Unit,
    onOpenSettings: () -> Unit,
    onSelectTopLevel: (TopLevel) -> Unit,
    viewModel: TodayViewModel = hiltViewModel(),
) {
    // Back from the background (maybe after midnight or 07:00): recompute "today"
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    TodayScreen(uiState, onEditDay, onOpenSettings, onSelectTopLevel)
}

/**
 * Today's shift in a card (type, machines with their location, overtime, comment)
 * and the next days below. Tapping any day opens it for editing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    uiState: TodayUiState,
    onEditDay: (LocalDate) -> Unit,
    onOpenSettings: () -> Unit,
    onSelectTopLevel: (TopLevel) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(painterResource(R.drawable.ic_settings), stringResource(R.string.settings_open))
                    }
                },
            )
        },
        bottomBar = { AppBottomBar(selected = TopLevel.TODAY, onSelect = onSelectTopLevel) },
        floatingActionButton = {
            if (uiState is TodayUiState.Ready) {
                FloatingActionButton(onClick = { onEditDay(uiState.currentDate) }) {
                    Icon(painterResource(R.drawable.ic_add), stringResource(R.string.add_shift))
                }
            }
        },
    ) { innerPadding ->
        when (uiState) {
            TodayUiState.Loading -> Box(Modifier.fillMaxSize().padding(innerPadding), Alignment.Center) {
                CircularProgressIndicator()
            }
            is TodayUiState.Ready -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = innerPadding.calculateTopPadding() + 8.dp,
                    // Room for the floating button over the last row
                    bottom = innerPadding.calculateBottomPadding() + 88.dp,
                ),
            ) {
                item {
                    CurrentShiftCard(
                        state = uiState,
                        onEdit = { onEditDay(uiState.currentDate) },
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
                item {
                    Text(
                        text = stringResource(R.string.today_upcoming),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 4.dp),
                    )
                }
                items(uiState.upcoming, key = { it.date.toString() }) { day ->
                    DayRow(day, onClick = { onEditDay(day.date) })
                    HorizontalDivider()
                }
            }
        }
    }
}

/** The big card of the current shift, or an invitation to add it. */
@Composable
private fun CurrentShiftCard(state: TodayUiState.Ready, onEdit: () -> Unit, modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth().clickable(onClick = onEdit)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(ShiftFormat.long(state.currentDate), style = MaterialTheme.typography.titleMedium)
            if (state.nightFromYesterday) {
                Text(
                    text = stringResource(R.string.today_night_from_yesterday),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
            val shift = state.current
            if (shift == null) {
                Text(stringResource(R.string.today_empty), style = MaterialTheme.typography.bodyLarge)
                FilledTonalButton(onClick = onEdit) { Text(stringResource(R.string.add_shift)) }
            } else {
                ShiftDetails(shift)
            }
        }
    }
}

/** Type, overtime, machines with locations and the comment of a shift. */
@Composable
private fun ShiftDetails(shift: Shift) {
    ShiftChip(shift.type)
    if (shift.overtime) {
        Text(stringResource(R.string.overtime_badge), style = MaterialTheme.typography.labelLarge)
    }
    shift.machines.forEach { MachineLine(it) }
    shift.comments?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
}

/** One of the next days: date, type and a short summary, or "not recorded". */
@Composable
private fun DayRow(day: DayEntry, onClick: () -> Unit) {
    val shift = day.shift
    ListItem(
        headlineContent = { Text(ShiftFormat.short(day.date)) },
        supportingContent = {
            Text(
                text = if (shift == null) stringResource(R.string.day_not_recorded) else shiftSummary(shift),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingContent = { shift?.let { ShiftChip(it.type) } },
        modifier = Modifier.clickable(onClick = onClick),
    )
}
