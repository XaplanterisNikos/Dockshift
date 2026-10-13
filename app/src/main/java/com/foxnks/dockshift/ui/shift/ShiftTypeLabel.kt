package com.foxnks.dockshift.ui.shift

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.foxnks.dockshift.R
import com.foxnks.dockshift.domain.shift.ShiftType

/** String resource with the name of a shift type. Exhaustive: a new type needs a name. */
@StringRes
fun ShiftType.labelRes(): Int = when (this) {
    ShiftType.MORNING -> R.string.shift_type_morning
    ShiftType.AFTERNOON -> R.string.shift_type_afternoon
    ShiftType.NIGHT -> R.string.shift_type_night
    ShiftType.DAY_OFF -> R.string.shift_type_day_off
    ShiftType.ANNUAL_LEAVE -> R.string.shift_type_annual_leave
    ShiftType.SICK_LEAVE -> R.string.shift_type_sick_leave
    ShiftType.PUBLIC_HOLIDAY -> R.string.shift_type_public_holiday
    ShiftType.TRAINING -> R.string.shift_type_training
    ShiftType.STRIKE -> R.string.shift_type_strike
    ShiftType.PARENTAL_LEAVE -> R.string.shift_type_parental_leave
}

/** Name of a shift type in the user's language, e.g. "Νύχτα". */
@Composable
fun ShiftType.label(): String = stringResource(labelRes())
