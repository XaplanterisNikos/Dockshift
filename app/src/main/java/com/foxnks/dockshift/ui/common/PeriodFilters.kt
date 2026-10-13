package com.foxnks.dockshift.ui.common

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.foxnks.dockshift.R
import com.foxnks.dockshift.ui.shift.ShiftFormat

/**
 * The period filter shared by the shift list and the statistics: two scrollable
 * rows of chips, the years first, then "All" and the twelve months.
 *
 * @param years the years to offer, newest first.
 * @param year the selected year.
 * @param month the selected month (1-12), or null for the whole year.
 * @param onYearSelected a year chip was tapped.
 * @param onMonthSelected a month chip was tapped, or "All" (null).
 * @param modifier optional modifier from the caller.
 */
@Composable
fun PeriodFilters(
    years: List<Int>,
    year: Int,
    month: Int?,
    onYearSelected: (Int) -> Unit,
    onMonthSelected: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.padding(vertical = 4.dp)) {
        ChipRow {
            years.forEach { option ->
                FilterChip(
                    selected = option == year,
                    onClick = { onYearSelected(option) },
                    label = { Text(option.toString()) },
                )
            }
        }
        ChipRow {
            FilterChip(
                selected = month == null,
                onClick = { onMonthSelected(null) },
                label = { Text(stringResource(R.string.list_all_months)) },
            )
            (1..12).forEach { option ->
                FilterChip(
                    selected = option == month,
                    onClick = { onMonthSelected(option) },
                    label = { Text(ShiftFormat.monthShort(option)) },
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
