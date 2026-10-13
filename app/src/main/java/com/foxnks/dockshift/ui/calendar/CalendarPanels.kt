package com.foxnks.dockshift.ui.calendar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.foxnks.dockshift.R
import com.foxnks.dockshift.domain.calendar.CalendarDay
import com.foxnks.dockshift.domain.calendar.DayStatus
import com.foxnks.dockshift.domain.calendar.MonthSummary
import com.foxnks.dockshift.domain.shift.ShiftType
import com.foxnks.dockshift.ui.shift.MachineLine
import com.foxnks.dockshift.ui.shift.ShiftChip
import com.foxnks.dockshift.ui.shift.ShiftFormat
import com.foxnks.dockshift.ui.shift.label
import com.foxnks.dockshift.ui.theme.DockshiftTheme
import java.time.LocalDate

/**
 * The month's totals: days per type as colored pills, worked shifts and hours,
 * overtime, and (once the rotation predicts) how many days are forecasts.
 *
 * @param summary the totals of the visible month.
 * @param modifier optional modifier from the caller.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MonthSummaryPanel(summary: MonthSummary, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PanelTitle(stringResource(R.string.calendar_summary_title))
        if (summary.isEmpty) {
            Text(
                text = stringResource(R.string.calendar_summary_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                summary.countByType.forEach { (type, count) -> TypeCount(type, count) }
            }
            if (summary.workShifts > 0) {
                Text(
                    text = pluralStringResource(
                        R.plurals.calendar_summary_worked,
                        summary.workShifts,
                        summary.workShifts,
                        summary.workedHours,
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            if (summary.overtimeShifts > 0) {
                Text(
                    text = stringResource(R.string.calendar_summary_overtime, summary.overtimeShifts, summary.overtimeHours),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (summary.forecastDays > 0) {
                ForecastLabel(stringResource(R.string.calendar_summary_forecast, summary.forecastDays))
            }
        }
    }
}

/**
 * Everything about the selected day: its shift (type, overtime, machines with
 * location, comment) and a button to edit it, or to record it when it is empty
 * or only predicted.
 *
 * @param day the selected day, or null while its month is being read.
 * @param onEditDay opens the day for adding or editing.
 * @param modifier optional modifier from the caller.
 * @param holidayName the public holiday of the day, shown in red. Nothing sets it
 * yet: the Greek public holidays come with their own issue.
 */
@Composable
fun DayDetailsPanel(
    day: CalendarDay?,
    onEditDay: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    holidayName: String? = null,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (day != null) {
            PanelTitle(ShiftFormat.long(day.date))
            holidayName?.let {
                Text(it, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.error)
            }
            when (val status = day.status) {
                is DayStatus.Recorded -> {
                    val shift = status.shift
                    ShiftChip(shift.type)
                    if (shift.overtime) {
                        Text(stringResource(R.string.overtime_badge), style = MaterialTheme.typography.labelLarge)
                    }
                    shift.machines.forEach { MachineLine(it) }
                    shift.comments?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    FilledTonalButton(onClick = { onEditDay(day.date) }) { Text(stringResource(R.string.calendar_edit)) }
                }
                is DayStatus.Forecast -> {
                    ForecastLabel(stringResource(R.string.calendar_forecast))
                    ForecastChip(status.type)
                    Text(
                        text = stringResource(R.string.calendar_forecast_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FilledTonalButton(onClick = { onEditDay(day.date) }) { Text(stringResource(R.string.calendar_record)) }
                }
                DayStatus.Empty -> {
                    Text(
                        text = stringResource(R.string.day_not_recorded),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FilledTonalButton(onClick = { onEditDay(day.date) }) { Text(stringResource(R.string.add_shift)) }
                }
            }
        }
    }
}

/** A panel heading, in the style of the other screens' section titles. */
@Composable
private fun PanelTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
}

/** A pill in the type's color with its name and number of days, e.g. "Πρωί 8". */
@Composable
private fun TypeCount(type: ShiftType, count: Int) {
    val colors = DockshiftTheme.shiftColors
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = colors.container(type),
        contentColor = colors.onContainer(type),
    ) {
        Text(
            text = "${type.label()} $count",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

/** Gold sparkles followed by a gold text: the mark of anything predicted. */
@Composable
private fun ForecastLabel(text: String) {
    val gold = DockshiftTheme.shiftColors.forecast
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(painterResource(R.drawable.ic_sparkle), contentDescription = null, tint = gold, modifier = Modifier.size(18.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = gold)
    }
}

/** A predicted type as a pill: pale gold with a gold border and gold text. */
@Composable
private fun ForecastChip(type: ShiftType) {
    val colors = DockshiftTheme.shiftColors
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = colors.forecastContainer,
        contentColor = colors.forecast,
        border = BorderStroke(1.5.dp, colors.forecast),
    ) {
        Text(
            text = type.label(),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

/** Android Studio preview of both panels; not part of the running app. */
@Preview(showBackground = true, widthDp = 320)
@Composable
private fun PanelsPreview() {
    DockshiftTheme(darkTheme = false, dynamicColor = false) {
        val month = previewMonth()
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            MonthSummaryPanel(month.summary)
            DayDetailsPanel(month.day(LocalDate.of(2026, 10, 6)), onEditDay = {})
            DayDetailsPanel(month.day(LocalDate.of(2026, 10, 22)), onEditDay = {})
        }
    }
}
