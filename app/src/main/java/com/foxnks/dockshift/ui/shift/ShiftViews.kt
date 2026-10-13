package com.foxnks.dockshift.ui.shift

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.foxnks.dockshift.R
import com.foxnks.dockshift.domain.shift.MachineAssignment
import com.foxnks.dockshift.domain.shift.Shift

/**
 * One machine of a shift: "RTG 12", its location underneath, and an "Overtime"
 * label when it was driven in the overtime hours.
 *
 * @param assignment the machine and whether it was in overtime.
 * @param modifier optional modifier from the caller.
 */
@Composable
fun MachineLine(assignment: MachineAssignment, modifier: Modifier = Modifier) {
    Column(modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(assignment.machine.label, style = MaterialTheme.typography.titleMedium)
            if (assignment.duringOvertime) {
                Text(
                    text = stringResource(R.string.overtime),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
        }
        assignment.machine.location?.let { location ->
            Text(
                text = location,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Compact one-line summary of a shift's machines and overtime for list rows,
 * e.g. "RTG 12 · QC 31 · +4h". Empty for days without machines.
 */
@Composable
fun shiftSummary(shift: Shift): String {
    val parts = shift.machines.map { it.machine.label }.toMutableList()
    if (shift.overtime) parts += stringResource(R.string.overtime_short)
    return parts.joinToString(" · ")
}
