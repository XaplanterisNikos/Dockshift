package com.foxnks.dockshift.ui.faults

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.foxnks.dockshift.R
import com.foxnks.dockshift.domain.faults.FaultType
import com.foxnks.dockshift.domain.faults.MachineFault
import com.foxnks.dockshift.domain.machine.Machine
import com.foxnks.dockshift.domain.machine.MachineCategory
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** The picture of a machine category: tractor, straddle carrier, cranes, forklift. */
@DrawableRes
fun MachineCategory.iconRes(): Int = when (this) {
    MachineCategory.SC -> R.drawable.ic_machine_sc
    MachineCategory.RTG -> R.drawable.ic_machine_rtg
    MachineCategory.RMG -> R.drawable.ic_machine_rmg
    MachineCategory.FL -> R.drawable.ic_forklift
    MachineCategory.TRUCKS -> R.drawable.ic_machine_truck
    MachineCategory.QC -> R.drawable.ic_machine_qc
}

/** The name of a fault in the phone's language. */
@StringRes
fun FaultType.labelRes(): Int = when (this) {
    FaultType.SPREADER -> R.string.fault_spreader
    FaultType.TWISTLOCKS -> R.string.fault_twistlocks
    FaultType.FLIPPERS -> R.string.fault_flippers
    FaultType.HOIST -> R.string.fault_hoist
    FaultType.TROLLEY -> R.string.fault_trolley
    FaultType.BOOM -> R.string.fault_boom
    FaultType.GANTRY -> R.string.fault_gantry
    FaultType.ENGINE_LEFT -> R.string.fault_engine_left
    FaultType.ENGINE_RIGHT -> R.string.fault_engine_right
    FaultType.TIRE -> R.string.fault_tire
    FaultType.AIR_LEAK -> R.string.fault_air_leak
    FaultType.CRASHED -> R.string.fault_crashed
    FaultType.LIGHTS -> R.string.fault_lights
    FaultType.GLASS -> R.string.fault_glass
    FaultType.WIPERS -> R.string.fault_wipers
    FaultType.HEATING -> R.string.fault_heating
    FaultType.CONTROLS -> R.string.fault_controls
    FaultType.RADIO -> R.string.fault_radio
    FaultType.TERMINAL -> R.string.fault_terminal
}

/** "14/10/2026": the day of a fault, in full (the history spans years). */
fun LocalDate.faultDate(): String = format(DateTimeFormatter.ofPattern("d/M/yyyy"))

/**
 * A pill with a fault's name and its day, in the error colors: something is wrong.
 *
 * @param fault an open fault.
 */
@Composable
fun OpenFaultPill(fault: MachineFault) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    ) {
        Text(
            text = "${stringResource(fault.type.labelRes())} · ${fault.reportedOn.faultDate()}",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

/**
 * On "Today", one per machine of the shift: its picture, number and location, and its
 * open faults (or "No faults"). Tapping opens the machine's fault page.
 *
 * @param machine the machine.
 * @param open its open faults, oldest first.
 * @param onOpen opens its fault page.
 * @param modifier optional modifier from the caller.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MachineFaultCard(machine: Machine, open: List<MachineFault>, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(painterResource(machine.category.iconRes()), contentDescription = null, modifier = Modifier.size(32.dp))
                Column(Modifier.weight(1f)) {
                    Text(machine.label, style = MaterialTheme.typography.titleMedium)
                    machine.location?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null)
            }
            if (open.isEmpty()) {
                Text(
                    text = stringResource(R.string.faults_card_none),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    open.forEach { OpenFaultPill(it) }
                }
            }
        }
    }
}
