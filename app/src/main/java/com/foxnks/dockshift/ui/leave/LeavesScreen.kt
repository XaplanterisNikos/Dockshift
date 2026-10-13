package com.foxnks.dockshift.ui.leave

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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.foxnks.dockshift.R
import com.foxnks.dockshift.domain.holidays.GreekHoliday
import com.foxnks.dockshift.domain.leave.Leave
import com.foxnks.dockshift.domain.leave.LeaveBalance
import com.foxnks.dockshift.domain.leave.LeaveRules
import com.foxnks.dockshift.domain.shift.ShiftType
import com.foxnks.dockshift.ui.shift.ShiftFormat
import com.foxnks.dockshift.ui.shift.label
import com.foxnks.dockshift.ui.theme.DockshiftTheme
import java.time.LocalDate

/**
 * Entry point of Menu → Leave.
 *
 * @param onBack back arrow tapped.
 * @param onOpenLeave opens a leave for editing (its id), or a new one (0) starting on the given day.
 * @param viewModel provided by Hilt, one per screen entry.
 */
@Composable
fun LeavesRoute(
    onBack: () -> Unit,
    onOpenLeave: (Long, LocalDate?) -> Unit,
    viewModel: LeavesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LeavesScreen(
        uiState = uiState,
        onBack = onBack,
        onYearSelected = viewModel::onYearSelected,
        onEntitlementChanged = viewModel::onEntitlementChanged,
        onPortClosedToggled = viewModel::onPortClosedToggled,
        onOpenLeave = onOpenLeave,
    )
}

/**
 * Leave of a year: the days you are entitled to (set per year, 10–60), what is
 * taken, planned and left, the planned leaves, and the holidays on which the port
 * is closed (a leave day on one of them does not count).
 *
 * @param uiState what to show.
 * @param onBack back arrow tapped.
 * @param onYearSelected a year chip was tapped.
 * @param onEntitlementChanged the entitlement was changed with − / +.
 * @param onPortClosedToggled a closed-port holiday was ticked or unticked.
 * @param onOpenLeave a leave was tapped (its id), or "+" (0 and the day to start on).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeavesScreen(
    uiState: LeavesUiState,
    onBack: () -> Unit,
    onYearSelected: (Int) -> Unit,
    onEntitlementChanged: (Int) -> Unit,
    onPortClosedToggled: (GreekHoliday, Boolean) -> Unit,
    onOpenLeave: (Long, LocalDate?) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.menu_leave)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.navigate_back))
                    }
                },
            )
        },
        floatingActionButton = {
            if (uiState is LeavesUiState.Ready) {
                // A new leave starts in the shown year: today, or the 1st of January of a later year
                val today = LocalDate.now()
                val day = if (uiState.year == today.year) today else LocalDate.of(uiState.year, 1, 1)
                FloatingActionButton(onClick = { onOpenLeave(0, day) }) {
                    Icon(painterResource(R.drawable.ic_add), stringResource(R.string.leave_add))
                }
            }
        },
    ) { innerPadding ->
        when (uiState) {
            LeavesUiState.Loading -> Box(Modifier.fillMaxSize().padding(innerPadding), Alignment.Center) {
                CircularProgressIndicator()
            }
            is LeavesUiState.Ready -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = innerPadding.calculateTopPadding(),
                    // Room for the floating button over the last row
                    bottom = innerPadding.calculateBottomPadding() + 88.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        uiState.years.forEach { year ->
                            FilterChip(
                                selected = year == uiState.year,
                                onClick = { onYearSelected(year) },
                                label = { Text(year.toString()) },
                            )
                        }
                    }
                }
                item {
                    BalanceCard(
                        balance = uiState.balance,
                        entitlementSet = uiState.entitlementSet,
                        onEntitlementChanged = onEntitlementChanged,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
                item { SectionTitle(stringResource(R.string.leave_planned_title)) }
                if (uiState.leaves.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.leave_none),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                }
                items(uiState.leaves, key = { it.leave.id }) { row ->
                    LeaveCard(row, onClick = { onOpenLeave(row.leave.id, null) }, modifier = Modifier.padding(horizontal = 16.dp))
                }
                item {
                    Column {
                        HorizontalDivider(Modifier.padding(top = 16.dp))
                        SectionTitle(stringResource(R.string.leave_port_closed_title))
                        Text(
                            text = stringResource(R.string.leave_port_closed_summary),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                }
                items(GreekHoliday.entries, key = { it.name }) { holiday ->
                    val closed = holiday in uiState.portClosed
                    ListItem(
                        headlineContent = { Text(holiday.label()) },
                        // onCheckedChange = null: the row handles the tap
                        leadingContent = { Checkbox(checked = closed, onCheckedChange = null) },
                        modifier = Modifier.toggleable(
                            value = closed,
                            role = Role.Checkbox,
                            onValueChange = { onPortClosedToggled(holiday, it) },
                        ),
                    )
                }
            }
        }
    }
}

/** A heading above a group of the screen. */
@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp),
    )
}

/**
 * The entitlement with − / + (10–60) and taken / planned / left underneath; left is
 * red when more is planned than allowed. Before the first setting, a button sets the
 * usual 22 days, changeable at once.
 */
@Composable
private fun BalanceCard(
    balance: LeaveBalance,
    entitlementSet: Boolean,
    onEntitlementChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = stringResource(R.string.leave_entitlement_title, balance.year),
                style = MaterialTheme.typography.titleMedium,
            )
            val entitlement = balance.entitlement
            if (!entitlementSet || entitlement == null) {
                Text(stringResource(R.string.leave_entitlement_unset), style = MaterialTheme.typography.bodyMedium)
                FilledTonalButton(onClick = { onEntitlementChanged(LeaveRules.DEFAULT_ENTITLEMENT) }) {
                    Text(stringResource(R.string.leave_entitlement_set, LeaveRules.DEFAULT_ENTITLEMENT))
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    FilledTonalIconButton(
                        onClick = { onEntitlementChanged(entitlement - 1) },
                        enabled = entitlement > LeaveRules.ENTITLEMENT_RANGE.first,
                    ) { Text("−", style = MaterialTheme.typography.titleLarge) }
                    Text(
                        text = pluralStringResource(R.plurals.leave_days, entitlement, entitlement),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    FilledTonalIconButton(
                        onClick = { onEntitlementChanged(entitlement + 1) },
                        enabled = entitlement < LeaveRules.ENTITLEMENT_RANGE.last,
                    ) { Text("+", style = MaterialTheme.typography.titleLarge) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Figure(stringResource(R.string.leave_taken), balance.taken.toString(), Modifier.weight(1f))
                Figure(stringResource(R.string.leave_planned), balance.planned.toString(), Modifier.weight(1f))
                balance.remaining?.let { left ->
                    Figure(stringResource(R.string.leave_left), left.toString(), Modifier.weight(1f), warning = left < 0)
                }
            }
        }
    }
}

/** A small number with its label; red when it is a warning. */
@Composable
private fun Figure(label: String, value: String, modifier: Modifier = Modifier, warning: Boolean = false) {
    Surface(modifier = modifier, shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = if (warning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/** One leave: a colored tile with the days it counts, its name and its dates. */
@Composable
private fun LeaveCard(row: LeaveRow, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = DockshiftTheme.shiftColors
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = colors.container(ShiftType.ANNUAL_LEAVE),
                contentColor = colors.onContainer(ShiftType.ANNUAL_LEAVE),
            ) {
                Text(
                    text = row.countedDays.toString(),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
            Column(Modifier.weight(1f)) {
                Text(row.leave.note ?: stringResource(R.string.shift_type_annual_leave), style = MaterialTheme.typography.titleSmall)
                Text(
                    text = leaveDates(row.leave),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** "21 Δεκ 2026 – 26 Δεκ 2026", or one date for a one-day leave. */
fun leaveDates(leave: Leave): String =
    if (leave.start == leave.end) ShiftFormat.medium(leave.start) else "${ShiftFormat.medium(leave.start)} – ${ShiftFormat.medium(leave.end)}"

/** Android Studio preview of the leave screen; not part of the running app. */
@Preview(showBackground = true, widthDp = 360, heightDp = 900)
@Composable
private fun LeavesPreview() {
    val summer = Leave(1, LocalDate.of(2026, 8, 3), LocalDate.of(2026, 8, 16), "Καλοκαιρινή")
    val christmas = Leave(2, LocalDate.of(2026, 12, 21), LocalDate.of(2026, 12, 26), "Χριστούγεννα")
    DockshiftTheme(darkTheme = false, dynamicColor = false) {
        LeavesScreen(
            uiState = LeavesUiState.Ready(
                years = listOf(2025, 2026, 2027, 2028),
                year = 2026,
                entitlementSet = true,
                balance = LeaveBalance(2026, 22, taken = 14, planned = 5),
                leaves = listOf(LeaveRow(summer, 14), LeaveRow(christmas, 5)),
                portClosed = setOf(GreekHoliday.CHRISTMAS),
            ),
            onBack = {},
            onYearSelected = {},
            onEntitlementChanged = {},
            onPortClosedToggled = { _, _ -> },
            onOpenLeave = { _, _ -> },
        )
    }
}
