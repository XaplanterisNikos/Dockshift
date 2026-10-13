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

/**
 * String resource with the short name of a shift type for calendar cells, e.g. "Ν"
 * for night. Exhaustive: a new type needs a short name too.
 */
@StringRes
fun ShiftType.abbreviationRes(): Int = when (this) {
    ShiftType.MORNING -> R.string.shift_abbr_morning
    ShiftType.AFTERNOON -> R.string.shift_abbr_afternoon
    ShiftType.NIGHT -> R.string.shift_abbr_night
    ShiftType.DAY_OFF -> R.string.shift_abbr_day_off
    ShiftType.ANNUAL_LEAVE -> R.string.shift_abbr_annual_leave
    ShiftType.SICK_LEAVE -> R.string.shift_abbr_sick_leave
    ShiftType.PUBLIC_HOLIDAY -> R.string.shift_abbr_public_holiday
    ShiftType.TRAINING -> R.string.shift_abbr_training
    ShiftType.STRIKE -> R.string.shift_abbr_strike
    ShiftType.PARENTAL_LEAVE -> R.string.shift_abbr_parental_leave
}

/** Short name of a shift type in the user's language, e.g. "Ν". */
@Composable
fun ShiftType.abbreviation(): String = stringResource(abbreviationRes())
