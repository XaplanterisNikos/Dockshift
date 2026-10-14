package com.foxnks.dockshift.ui.today

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.foxnks.dockshift.R
import com.foxnks.dockshift.domain.faults.MachineFault
import com.foxnks.dockshift.domain.leave.Leave
import com.foxnks.dockshift.domain.shift.Shift
import com.foxnks.dockshift.domain.shift.ShiftType
import com.foxnks.dockshift.ui.faults.MachineFaultCard
import com.foxnks.dockshift.ui.faults.OpenFaultsViewModel
import com.foxnks.dockshift.ui.navigation.AppBottomBar
import com.foxnks.dockshift.ui.navigation.TopLevel
import com.foxnks.dockshift.ui.shift.DayCard
import com.foxnks.dockshift.ui.shift.MachineLine
import com.foxnks.dockshift.ui.shift.MiniDayCard
import com.foxnks.dockshift.ui.shift.ShiftChip
import com.foxnks.dockshift.ui.shift.ShiftFormat
import com.foxnks.dockshift.ui.shift.label
import com.foxnks.dockshift.ui.theme.DockshiftTheme
import com.foxnks.dockshift.ui.vessels.TodayVesselsCard
import java.time.LocalDate

/**
 * Entry point of the "Today" screen.
 *
 * @param onEditDay open the shift of a day for adding or editing.
 * @param onOpenLeave open a planned leave (its id) for changing or deleting.
 * @param onSelectTopLevel switch screen from the bottom bar.
 * @param onOpenBackup open Data & backup (from the backup reminder).
 * @param onOpenVessels open the Vessels tab (from the vessels card).
 * @param onOpenFaults open a machine's fault page (from its card).
 * @param onOpenOtherApp open the page of the author's other app (the ⓘ in the top bar).
 * @param viewModel provided by Hilt, one per screen entry.
 * @param faults the open faults of every machine, for the machine cards.
 */
@Composable
fun TodayRoute(
    onEditDay: (LocalDate) -> Unit,
    onOpenLeave: (Long) -> Unit,
    onSelectTopLevel: (TopLevel) -> Unit,
    onOpenBackup: () -> Unit = {},
    onOpenVessels: () -> Unit = {},
    onOpenFaults: (Long) -> Unit = {},
    onOpenOtherApp: () -> Unit = {},
    viewModel: TodayViewModel = hiltViewModel(),
    faults: OpenFaultsViewModel = hiltViewModel(),
) {
    // Back from the background (maybe after midnight or 07:00): recompute "today"
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val backupDueDays by viewModel.backupDueDays.collectAsStateWithLifecycle()
    val openFaults by faults.byMachine.collectAsStateWithLifecycle()
    TodayScreen(
        uiState = uiState,
        onEditDay = onEditDay,
        onSelectTopLevel = onSelectTopLevel,
        onOpenLeave = onOpenLeave,
        backupDueDays = backupDueDays,
        onOpenBackup = onOpenBackup,
        openFaults = openFaults,
        onOpenFaults = onOpenFaults,
        onOpenOtherApp = onOpenOtherApp,
    ) {
        // Its own ViewModel: reads the vessel pages when the lists are stale
        TodayVesselsCard(onOpen = onOpenVessels, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp))
    }
}

/**
 * Today's shift in a card (type, machines with their location, overtime, comment)
 * and the next days below: small cards upright, a row of mini cards sideways.
 * Public holidays are named in red. Tapping any day opens it for editing.
 * Under today's shift: one card per machine of the shift with its open faults, the
 * vessels being worked now, and every 15 days (unless turned off) a card reminding to
 * back up.
 *
 * @param uiState what to show.
 * @param onEditDay opens a day for adding or editing.
 * @param onSelectTopLevel switch screen from the bottom bar.
 * @param onOpenLeave opens a planned leave (its id).
 * @param backupDueDays days since the last backup when the reminder is due
 * ([Int.MAX_VALUE]: never backed up), or null for no reminder.
 * @param onOpenBackup opens Data & backup.
 * @param openFaults the open faults of every machine, by machine id.
 * @param onOpenFaults opens a machine's fault page.
 * @param onOpenOtherApp the ⓘ in the top bar: the author's other app.
 * @param vesselsCard the card of the vessels being worked, under the shift (a slot,
 * so previews need no Hilt).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    uiState: TodayUiState,
    onEditDay: (LocalDate) -> Unit,
    onSelectTopLevel: (TopLevel) -> Unit,
    onOpenLeave: (Long) -> Unit = {},
    backupDueDays: Int? = null,
    onOpenBackup: () -> Unit = {},
    openFaults: Map<Long, List<MachineFault>> = emptyMap(),
    onOpenFaults: (Long) -> Unit = {},
    onOpenOtherApp: () -> Unit = {},
    vesselsCard: @Composable () -> Unit = {},
) {
    /** A leave day cannot be worked: it opens its leave instead of the shift form. */
    fun openDay(date: LocalDate, leave: Leave?) {
        if (leave != null) onOpenLeave(leave.id) else onEditDay(date)
    }
    Scaffold(
        // Settings live in the menu (bottom bar); the only action is the ⓘ
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onOpenOtherApp) {
                        Icon(painterResource(R.drawable.ic_info), stringResource(R.string.other_app_title))
                    }
                },
            )
        },
        bottomBar = { AppBottomBar(selected = TopLevel.TODAY, onSelect = onSelectTopLevel) },
        floatingActionButton = {
            if (uiState is TodayUiState.Ready) {
                FloatingActionButton(onClick = { openDay(uiState.currentDate, uiState.currentLeave) }) {
                    Icon(painterResource(R.drawable.ic_add), stringResource(R.string.add_shift))
                }
            }
        },
    ) { innerPadding ->
        val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
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
                        onEdit = { openDay(uiState.currentDate, uiState.currentLeave) },
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
                // One card per machine of the shift (each machine once), with its open faults
                val machines = uiState.current?.machines.orEmpty().map { it.machine }.distinctBy { it.id }
                items(machines, key = { "machine-${it.id}" }) { machine ->
                    MachineFaultCard(
                        machine = machine,
                        open = openFaults[machine.id].orEmpty(),
                        onOpen = { onOpenFaults(machine.id) },
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
                    )
                }
                // The vessels being worked now
                item { vesselsCard() }
                if (backupDueDays != null) {
                    item {
                        BackupReminderCard(
                            days = backupDueDays,
                            onOpenBackup = onOpenBackup,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
                        )
                    }
                }
                item {
                    Text(
                        text = stringResource(R.string.today_upcoming),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 4.dp),
                    )
                }
                if (landscape) {
                    // Sideways: the next days as a row of mini cards
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(uiState.upcoming, key = { it.date.toString() }) { day ->
                                MiniDayCard(
                                    date = day.date,
                                    shift = day.shift ?: day.leave?.let { leaveShift(day.date, it) },
                                    onClick = { openDay(day.date, day.leave) },
                                    modifier = Modifier.width(76.dp),
                                    holiday = day.holiday,
                                    forecast = day.forecast,
                                )
                            }
                        }
                    }
                } else {
                    // Upright: one small card per day
                    items(uiState.upcoming, key = { it.date.toString() }) { day ->
                        DayCard(
                            date = day.date,
                            shift = day.shift ?: day.leave?.let { leaveShift(day.date, it) },
                            onClick = { openDay(day.date, day.leave) },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                            holiday = day.holiday,
                            forecast = day.forecast,
                        )
                    }
                }
            }
        }
    }
}

/**
 * "16 days since your last backup" (or "no backup yet") with a button to Data & backup.
 *
 * @param days days since the last backup; [Int.MAX_VALUE] when never made.
 * @param onOpenBackup opens Data & backup.
 * @param modifier optional modifier from the caller.
 */
@Composable
private fun BackupReminderCard(days: Int, onOpenBackup: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        ),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(painterResource(R.drawable.ic_backup), contentDescription = null)
            Text(
                text = if (days == Int.MAX_VALUE) {
                    stringResource(R.string.today_backup_never)
                } else {
                    pluralStringResource(R.plurals.today_backup_due, days, days)
                },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onOpenBackup) { Text(stringResource(R.string.today_backup_button)) }
        }
    }
}

/** The big card of the current shift, or an invitation to add it. */
@Composable
private fun CurrentShiftCard(state: TodayUiState.Ready, onEdit: () -> Unit, modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth().clickable(onClick = onEdit)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(ShiftFormat.long(state.currentDate), style = MaterialTheme.typography.titleMedium)
            state.currentHoliday?.let {
                Text(it.label(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.error)
            }
            if (state.nightFromYesterday) {
                Text(
                    text = stringResource(R.string.today_night_from_yesterday),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
            val shift = state.current
            val forecast = state.currentForecast
            val leave = state.currentLeave
            when {
                shift != null -> ShiftDetails(shift)
                // On leave: the type, the leave's name, and a button that opens the leave
                leave != null -> {
                    ShiftChip(ShiftType.ANNUAL_LEAVE)
                    leave.note?.let { Text(it, style = MaterialTheme.typography.titleSmall) }
                    FilledTonalButton(onClick = onEdit) { Text(stringResource(R.string.leave_open)) }
                }
                // Not recorded, but the rotating schedule predicts it: show it in gold
                forecast != null -> {
                    ForecastLine(forecast)
                    FilledTonalButton(onClick = onEdit) { Text(stringResource(R.string.calendar_record)) }
                }
                else -> {
                    Text(stringResource(R.string.today_empty), style = MaterialTheme.typography.bodyLarge)
                    FilledTonalButton(onClick = onEdit) { Text(stringResource(R.string.add_shift)) }
                }
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

/** "✦ Πρόβλεψη: Πρωί" in gold: the shift the rotating schedule predicts. */
@Composable
private fun ForecastLine(type: ShiftType) {
    val gold = DockshiftTheme.shiftColors.forecast
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(painterResource(R.drawable.ic_sparkle), contentDescription = null, tint = gold, modifier = Modifier.size(20.dp))
        Text(
            text = stringResource(R.string.today_forecast, type.label()),
            style = MaterialTheme.typography.titleMedium,
            color = gold,
        )
    }
}

/**
 * A leave day drawn as an annual leave shift (its note as the comment), so the day
 * cards show it like any other day. Display only: it is never saved.
 */
private fun leaveShift(date: LocalDate, leave: Leave): Shift = Shift(date, ShiftType.ANNUAL_LEAVE, comments = leave.note)
