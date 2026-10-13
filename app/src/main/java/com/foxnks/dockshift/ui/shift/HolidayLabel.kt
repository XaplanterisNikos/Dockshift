package com.foxnks.dockshift.ui.shift

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.foxnks.dockshift.R
import com.foxnks.dockshift.domain.holidays.GreekHoliday

/** String resource with the name of a public holiday. Exhaustive: a new holiday needs a name. */
@StringRes
fun GreekHoliday.labelRes(): Int = when (this) {
    GreekHoliday.NEW_YEAR -> R.string.holiday_new_year
    GreekHoliday.EPIPHANY -> R.string.holiday_epiphany
    GreekHoliday.CLEAN_MONDAY -> R.string.holiday_clean_monday
    GreekHoliday.INDEPENDENCE_DAY -> R.string.holiday_independence_day
    GreekHoliday.GOOD_FRIDAY -> R.string.holiday_good_friday
    GreekHoliday.EASTER -> R.string.holiday_easter
    GreekHoliday.EASTER_MONDAY -> R.string.holiday_easter_monday
    GreekHoliday.LABOUR_DAY -> R.string.holiday_labour_day
    GreekHoliday.WHIT_MONDAY -> R.string.holiday_whit_monday
    GreekHoliday.ASSUMPTION -> R.string.holiday_assumption
    GreekHoliday.OHI_DAY -> R.string.holiday_ohi_day
    GreekHoliday.CHRISTMAS -> R.string.holiday_christmas
    GreekHoliday.SECOND_DAY_OF_CHRISTMAS -> R.string.holiday_second_day_of_christmas
}

/** Name of a public holiday in the user's language, e.g. "28η Οκτωβρίου". */
@Composable
fun GreekHoliday.label(): String = stringResource(labelRes())
