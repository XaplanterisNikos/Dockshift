package com.foxnks.dockshift.ui.calendar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.foxnks.dockshift.R
import com.foxnks.dockshift.domain.calendar.CalendarDay
import com.foxnks.dockshift.domain.calendar.CalendarMonth
import com.foxnks.dockshift.domain.calendar.DayStatus
import com.foxnks.dockshift.domain.calendar.MonthGrid
import com.foxnks.dockshift.domain.shift.Shift
import com.foxnks.dockshift.domain.shift.ShiftType
import com.foxnks.dockshift.ui.shift.ShiftFormat
import com.foxnks.dockshift.ui.shift.abbreviation
import com.foxnks.dockshift.ui.shift.label
import com.foxnks.dockshift.ui.theme.DockshiftTheme
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/** Space between cells, so neighbouring days of the same color stay apart. */
private val CELL_GAP = 3.dp

/** Width / height of a cell when the page grows with its width (portrait): a bit taller than wide. */
private const val CELL_ASPECT_RATIO = 0.85f

/** Opacity of the neighbour months' days in the first and last rows. */
private const val OUTSIDE_MONTH_ALPHA = 0.38f

/**
 * The names of the week days above the grid, e.g. "Δευ … Κυρ", Monday first.
 *
 * @param modifier optional modifier from the caller.
 */
@Composable
fun WeekDayHeader(modifier: Modifier = Modifier) {
    val locale = Locale.getDefault()
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(CELL_GAP)) {
        MonthGrid.weekDays().forEach { day ->
            Text(
                // Stand-alone short name: the form used on its own, e.g. as a column title
                text = day.getDisplayName(TextStyle.SHORT_STANDALONE, locale).replaceFirstChar { it.titlecase(locale) },
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
    }
}

/**
 * One month as a 6×7 grid of [DayCell]s.
 *
 * @param page the month's cells.
 * @param today the current date (circled).
 * @param selectedDate the selected day (outlined), or null when nothing is selectable.
 * @param fillHeight true to stretch the 6 rows over the available height (landscape,
 * where height is short); false to size cells by their width (portrait).
 * @param clickLabel what tapping a day does, read out by TalkBack ("Open"/"Select").
 * @param onDayClick called with the tapped day.
 * @param modifier optional modifier from the caller.
 */
@Composable
fun MonthPage(
    page: CalendarMonth,
    today: LocalDate,
    selectedDate: LocalDate?,
    fillHeight: Boolean,
    clickLabel: String,
    onDayClick: (CalendarDay) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(CELL_GAP)) {
        page.days.chunked(MonthGrid.DAYS_PER_WEEK).forEach { week ->
            Row(
                // Landscape: each week takes an equal share of the height
                modifier = if (fillHeight) Modifier.fillMaxWidth().weight(1f) else Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(CELL_GAP),
            ) {
                week.forEach { day ->
                    DayCell(
                        day = day,
                        isToday = day.date == today,
                        // Only the real cell is outlined, not the same date shown faded on a neighbour page
                        isSelected = day.inMonth && day.date == selectedDate,
                        clickLabel = clickLabel,
                        onClick = { onDayClick(day) },
                        modifier = Modifier
                            .weight(1f)
                            .then(if (fillHeight) Modifier.fillMaxHeight() else Modifier.aspectRatio(CELL_ASPECT_RATIO)),
                    )
                }
            }
        }
    }
}

/**
 * One day of the calendar.
 *
 * - Recorded: filled with the shift type's color, its short name in the middle and
 *   "+4" in the corner when there was overtime.
 * - Forecast: pale gold with a gold border, gold short name and gold sparkles.
 * - Empty: a neutral tile with only the day number.
 * Today's number sits in a filled circle; the selected day gets a thick outline; days
 * of the neighbour months are faded. TalkBack reads one full sentence per cell, e.g.
 * "Tuesday 13 October 2026, today, Night, with overtime".
 *
 * @param day the day and what is known about it.
 * @param isToday whether it is the current date.
 * @param isSelected whether it is the selected day (day-details mode only).
 * @param isHoliday whether it is a public holiday (red number). Nothing sets it yet:
 * the Greek public holidays come with their own issue.
 * @param clickLabel what tapping does, for TalkBack.
 * @param onClick called on tap.
 * @param modifier size and position from the grid.
 */
@Composable
fun DayCell(
    day: CalendarDay,
    isToday: Boolean,
    isSelected: Boolean,
    clickLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isHoliday: Boolean = false,
) {
    val shiftColors = DockshiftTheme.shiftColors
    val status = day.status
    // Fill and text color by what the day is
    val (container, content) = when (status) {
        is DayStatus.Recorded -> shiftColors.container(status.shift.type) to shiftColors.onContainer(status.shift.type)
        is DayStatus.Forecast -> shiftColors.forecastContainer to shiftColors.forecast
        DayStatus.Empty -> MaterialTheme.colorScheme.surfaceContainerHigh to MaterialTheme.colorScheme.onSurface
    }
    val shape = RoundedCornerShape(8.dp)
    val border = when {
        isSelected -> BorderStroke(3.dp, MaterialTheme.colorScheme.onSurface)
        status is DayStatus.Forecast -> BorderStroke(1.5.dp, shiftColors.forecast)
        else -> null
    }
    val description = cellDescription(day, isToday)

    Box(
        modifier = modifier
            .alpha(if (day.inMonth) 1f else OUTSIDE_MONTH_ALPHA)
            .clip(shape)
            .background(container)
            .then(if (border != null) Modifier.border(border, shape) else Modifier)
            .clickable(onClickLabel = clickLabel, onClick = onClick)
            // One sentence for the whole cell; the texts inside are hidden from TalkBack
            .semantics {
                contentDescription = description
                if (isSelected) selected = true
            }
            .padding(3.dp),
    ) {
        DayNumber(
            day = day.date.dayOfMonth,
            isToday = isToday,
            color = if (isHoliday) MaterialTheme.colorScheme.error else content,
            modifier = Modifier.align(Alignment.TopStart),
        )
        when (status) {
            is DayStatus.Recorded -> {
                CellText(
                    text = status.shift.type.abbreviation(),
                    color = content,
                    modifier = Modifier.align(Alignment.Center),
                )
                if (status.shift.overtime) {
                    Text(
                        text = stringResource(R.string.calendar_overtime_mark),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = content,
                        maxLines = 1,
                        modifier = Modifier.align(Alignment.BottomEnd).clearAndSetSemantics {},
                    )
                }
            }
            is DayStatus.Forecast -> {
                // The magic wand's sparkles: what makes a prediction look like one
                Icon(
                    painter = painterResource(R.drawable.ic_sparkle),
                    contentDescription = null,
                    tint = shiftColors.forecast,
                    modifier = Modifier.align(Alignment.TopEnd).size(12.dp),
                )
                CellText(
                    text = status.type.abbreviation(),
                    color = shiftColors.forecast,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            DayStatus.Empty -> Unit
        }
    }
}

/** The day number in a cell's corner; today's sits in a filled circle. */
@Composable
private fun DayNumber(day: Int, isToday: Boolean, color: Color, modifier: Modifier = Modifier) {
    val text = day.toString()
    if (isToday) {
        Box(
            modifier = modifier
                .sizeIn(minWidth = 20.dp, minHeight = 20.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary,
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 3.dp).clearAndSetSemantics {},
            )
        }
    } else {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = color,
            maxLines = 1,
            modifier = modifier.padding(start = 2.dp).clearAndSetSemantics {},
        )
    }
}

/** The short type name in the middle of a cell, e.g. "Ν". */
@Composable
private fun CellText(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = color,
        maxLines = 1,
        modifier = modifier.clearAndSetSemantics {},
    )
}

/** What TalkBack reads for a cell: date, "today", type or forecast, overtime. */
@Composable
private fun cellDescription(day: CalendarDay, isToday: Boolean): String {
    val parts = mutableListOf(ShiftFormat.long(day.date))
    if (isToday) parts += stringResource(R.string.calendar_cell_today)
    when (val status = day.status) {
        is DayStatus.Recorded -> {
            parts += status.shift.type.label()
            if (status.shift.overtime) parts += stringResource(R.string.calendar_cell_overtime)
        }
        is DayStatus.Forecast -> parts += stringResource(R.string.calendar_cell_forecast, status.type.label())
        DayStatus.Empty -> parts += stringResource(R.string.day_not_recorded)
    }
    return parts.joinToString(", ")
}

/** Sample October 2026 for previews: a few shifts, overtime, and gold forecasts after the 20th. */
internal fun previewMonth(): CalendarMonth {
    val october = YearMonth.of(2026, 10)
    val shifts = listOf(
        Shift(october.atDay(1), ShiftType.NIGHT),
        Shift(october.atDay(2), ShiftType.NIGHT, overtime = true),
        Shift(october.atDay(3), ShiftType.DAY_OFF),
        Shift(october.atDay(5), ShiftType.MORNING),
        Shift(october.atDay(6), ShiftType.MORNING, overtime = true),
        Shift(october.atDay(8), ShiftType.AFTERNOON),
        Shift(october.atDay(9), ShiftType.ANNUAL_LEAVE),
        Shift(october.atDay(12), ShiftType.SICK_LEAVE),
        Shift(october.atDay(13), ShiftType.TRAINING),
    )
    return CalendarMonth.of(october, shifts) { date ->
        if (YearMonth.from(date) == october && date.dayOfMonth > 20) ShiftType.entries[date.dayOfMonth % 3] else null
    }
}

/** Android Studio preview of a month page in light mode; not part of the running app. */
@Preview(showBackground = true, widthDp = 360)
@Composable
private fun MonthPageLightPreview() {
    DockshiftTheme(darkTheme = false, dynamicColor = false) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            WeekDayHeader()
            MonthPage(previewMonth(), LocalDate.of(2026, 10, 13), null, fillHeight = false, clickLabel = "", onDayClick = {})
        }
    }
}

/** Android Studio preview of a month page in dark mode, with a selected day. */
@Preview(showBackground = true, backgroundColor = 0xFF0B0F11, widthDp = 360)
@Composable
private fun MonthPageDarkPreview() {
    DockshiftTheme(darkTheme = true, dynamicColor = false) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            WeekDayHeader()
            MonthPage(
                previewMonth(),
                LocalDate.of(2026, 10, 13),
                LocalDate.of(2026, 10, 6),
                fillHeight = false,
                clickLabel = "",
                onDayClick = {},
            )
        }
    }
}
