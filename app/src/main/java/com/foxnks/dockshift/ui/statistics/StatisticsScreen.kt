package com.foxnks.dockshift.ui.statistics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.foxnks.dockshift.R
import com.foxnks.dockshift.domain.machine.Machine
import com.foxnks.dockshift.domain.machine.MachineCategory
import com.foxnks.dockshift.domain.shift.MachineAssignment
import com.foxnks.dockshift.domain.shift.Shift
import com.foxnks.dockshift.domain.shift.ShiftType
import com.foxnks.dockshift.domain.statistics.MachineStat
import com.foxnks.dockshift.domain.statistics.MonthStat
import com.foxnks.dockshift.domain.statistics.ShiftStatistics
import com.foxnks.dockshift.ui.common.PeriodFilters
import com.foxnks.dockshift.ui.shift.ShiftFormat
import com.foxnks.dockshift.ui.shift.label
import com.foxnks.dockshift.ui.theme.DockshiftTheme
import java.time.LocalDate

/** How many machines are listed before "All machines". */
private const val TOP_MACHINES = 10

/** Height of the tallest month bar. */
private val MONTH_BAR_HEIGHT = 110.dp

/**
 * Entry point of the statistics screen.
 *
 * @param onBack called when the user taps the back arrow.
 * @param viewModel provided by Hilt, one per screen entry.
 */
@Composable
fun StatisticsRoute(onBack: () -> Unit, viewModel: StatisticsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    StatisticsScreen(
        uiState = uiState,
        onBack = onBack,
        onYearSelected = viewModel::onYearSelected,
        onMonthSelected = viewModel::onMonthSelected,
    )
}

/**
 * Statistics of a year or a month: totals of shifts, hours, overtime and holidays
 * worked on top, then days per type, working shifts month by month (for a whole
 * year), and the machines driven, most driven first.
 *
 * @param uiState what to show.
 * @param onBack back arrow tapped.
 * @param onYearSelected a year chip was tapped.
 * @param onMonthSelected a month chip (or "All") was tapped.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(
    uiState: StatisticsUiState,
    onBack: () -> Unit,
    onYearSelected: (Int) -> Unit,
    onMonthSelected: (Int?) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.menu_statistics)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.navigate_back))
                    }
                },
            )
        },
    ) { innerPadding ->
        when (uiState) {
            StatisticsUiState.Loading -> Box(Modifier.fillMaxSize().padding(innerPadding), Alignment.Center) {
                CircularProgressIndicator()
            }
            is StatisticsUiState.Ready -> Column(Modifier.fillMaxSize().padding(innerPadding)) {
                PeriodFilters(uiState.years, uiState.year, uiState.month, onYearSelected, onMonthSelected)
                HorizontalDivider()
                val stats = uiState.statistics
                if (stats.isEmpty) {
                    Box(Modifier.fillMaxSize(), Alignment.Center) {
                        Text(stringResource(R.string.list_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(24.dp),
                    ) {
                        Totals(stats, showHolidays = uiState.showHolidays)
                        Section(stringResource(R.string.stats_by_type)) { TypeBars(stats.countByType) }
                        // Month by month only makes sense for a whole year
                        if (uiState.month == null) {
                            Section(stringResource(R.string.stats_by_month)) { MonthBars(stats.months) }
                        }
                        if (stats.machines.isNotEmpty()) {
                            Section(stringResource(R.string.stats_machines)) { Machines(stats) }
                        }
                    }
                }
            }
        }
    }
}

/** A titled block of the screen. */
@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        content()
    }
}

/** The headline numbers as tiles: working shifts, overtime, and (if on) holidays worked. */
@Composable
private fun Totals(stats: ShiftStatistics, showHolidays: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Tile(
            label = stringResource(R.string.stats_work_shifts),
            value = stats.workShifts.toString(),
            detail = stringResource(R.string.stats_hours, stats.workedHours),
            modifier = Modifier.weight(1f),
        )
        Tile(
            label = stringResource(R.string.stats_overtime),
            value = stats.overtimeShifts.toString(),
            detail = stringResource(R.string.stats_overtime_hours, stats.overtimeHours),
            modifier = Modifier.weight(1f),
        )
        if (showHolidays) {
            Tile(
                label = stringResource(R.string.stats_holidays_worked),
                value = stats.holidaysWorked.toString(),
                detail = stringResource(R.string.stats_holidays_worked_detail),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** One headline number: a small label, the number big, and a detail line. */
@Composable
private fun Tile(label: String, value: String, detail: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(value, style = MaterialTheme.typography.headlineMedium)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Days per type as horizontal bars in each type's color, longest = most days. */
@Composable
private fun TypeBars(counts: Map<ShiftType, Int>) {
    val max = counts.values.maxOrNull() ?: return
    val colors = DockshiftTheme.shiftColors
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        counts.forEach { (type, count) ->
            BarRow(
                label = type.label(),
                count = count,
                fraction = count.toFloat() / max,
                color = colors.container(type),
            )
        }
    }
}

/**
 * One horizontal bar: label on the left, the bar, the number on the right.
 *
 * @param fraction bar length from 0 to 1.
 * @param extra optional text after the number, e.g. overtime.
 */
@Composable
private fun BarRow(label: String, count: Int, fraction: Float, color: Color, extra: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(96.dp))
        Box(Modifier.weight(1f)) {
            Box(
                Modifier
                    // A very short bar is still visible
                    .fillMaxWidth(fraction.coerceIn(0.02f, 1f))
                    .height(16.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(color),
            )
        }
        Text(count.toString(), style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.End, modifier = Modifier.width(32.dp))
        extra?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
        }
    }
}

/**
 * Working shifts per month of the year as vertical bars; the overtime part of each
 * bar is drawn on top in another color.
 */
@Composable
private fun MonthBars(months: List<MonthStat>) {
    val max = months.maxOfOrNull { it.workShifts }?.takeIf { it > 0 } ?: 1
    val regularColor = MaterialTheme.colorScheme.primary
    val overtimeColor = MaterialTheme.colorScheme.tertiary
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth().height(MONTH_BAR_HEIGHT + 40.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            months.forEach { stat ->
                val description = stringResource(
                    R.string.stats_month_bar,
                    ShiftFormat.month(stat.month.year, stat.month.monthValue),
                    stat.workShifts,
                    stat.overtimeShifts,
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        // One sentence per month for TalkBack
                        .clearAndSetSemantics { contentDescription = description },
                    verticalArrangement = Arrangement.Bottom,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (stat.workShifts > 0) {
                        Text(stat.workShifts.toString(), style = MaterialTheme.typography.labelSmall)
                        val regular = stat.workShifts - stat.overtimeShifts
                        Column(
                            Modifier
                                .width(14.dp)
                                .height(MONTH_BAR_HEIGHT * (stat.workShifts.toFloat() / max))
                                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)),
                        ) {
                            // Weights must be above zero, so empty parts are left out
                            if (stat.overtimeShifts > 0) {
                                Box(Modifier.fillMaxWidth().weight(stat.overtimeShifts.toFloat()).background(overtimeColor))
                            }
                            if (regular > 0) {
                                Box(Modifier.fillMaxWidth().weight(regular.toFloat()).background(regularColor))
                            }
                        }
                    }
                    Text(
                        text = ShiftFormat.monthShort(stat.month.monthValue),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            LegendItem(regularColor, stringResource(R.string.stats_work_shifts))
            LegendItem(overtimeColor, stringResource(R.string.stats_with_overtime))
        }
    }
}

/** A colored dot with its meaning, under a chart. */
@Composable
private fun LegendItem(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * Categories as pills, then each machine as a bar (most driven first), with how
 * many of its shifts were in the overtime hours. The list is cut to [TOP_MACHINES]
 * until "All machines" is tapped.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Machines(stats: ShiftStatistics) {
    var showAll by rememberSaveable { mutableStateOf(false) }
    val max = stats.machines.first().shifts
    val shown = if (showAll) stats.machines else stats.machines.take(TOP_MACHINES)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            stats.categories.forEach { category ->
                Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                    Text(
                        text = "${category.category.name} ${category.shifts}",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
        }
        shown.forEach { stat -> MachineBar(stat, max) }
        if (stats.machines.size > TOP_MACHINES) {
            TextButton(onClick = { showAll = !showAll }) {
                Text(
                    if (showAll) {
                        stringResource(R.string.stats_show_fewer)
                    } else {
                        stringResource(R.string.stats_show_all_machines, stats.machines.size)
                    },
                )
            }
        }
    }
}

/** One machine: "RTG 12", its bar, how many shifts, and "+N" for overtime shifts. */
@Composable
private fun MachineBar(stat: MachineStat, max: Int) {
    BarRow(
        label = stat.machine.label,
        count = stat.shifts,
        fraction = stat.shifts.toFloat() / max,
        color = MaterialTheme.colorScheme.secondary,
        extra = if (stat.overtimeShifts > 0) stringResource(R.string.stats_machine_overtime, stat.overtimeShifts) else null,
    )
}

/** Android Studio preview of a year's statistics; not part of the running app. */
@Preview(showBackground = true, widthDp = 360, heightDp = 1100)
@Composable
private fun StatisticsPreview() {
    val rtg = Machine(1, MachineCategory.RTG, "12")
    val qc = Machine(2, MachineCategory.QC, "31")
    val shifts = (1..200).map { n ->
        val date = LocalDate.of(2026, 1, 1).plusDays(n.toLong())
        val type = ShiftType.entries[n % 4]
        if (type.isWorkShift) {
            Shift(date, type, overtime = n % 5 == 0, machines = listOf(MachineAssignment(if (n % 3 == 0) qc else rtg)))
        } else {
            Shift(date, type)
        }
    }
    DockshiftTheme(darkTheme = false, dynamicColor = false) {
        StatisticsScreen(
            uiState = StatisticsUiState.Ready(
                years = listOf(2026, 2025),
                year = 2026,
                month = null,
                statistics = ShiftStatistics.of(shifts, ShiftStatistics.monthsOf(2026), setOf(LocalDate.of(2026, 3, 25))),
                showHolidays = true,
            ),
            onBack = {},
            onYearSelected = {},
            onMonthSelected = {},
        )
    }
}
