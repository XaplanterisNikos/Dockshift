package com.foxnks.dockshift.ui.shift

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.foxnks.dockshift.domain.shift.ShiftType
import com.foxnks.dockshift.ui.theme.DockshiftTheme

/**
 * A small rounded label in the color of a shift type, e.g. a "Νύχτα" pill.
 * The building block for showing a shift type anywhere in the app.
 *
 * @param type the shift type to show.
 * @param modifier optional modifier from the caller.
 */
@Composable
fun ShiftChip(type: ShiftType, modifier: Modifier = Modifier) {
    val colors = DockshiftTheme.shiftColors
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = colors.container(type),
        contentColor = colors.onContainer(type),
    ) {
        Text(
            text = type.label(),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}
