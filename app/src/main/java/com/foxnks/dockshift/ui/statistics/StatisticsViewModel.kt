package com.foxnks.dockshift.ui.statistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.foxnks.dockshift.domain.holidays.GreekHolidays
import com.foxnks.dockshift.domain.settings.SettingsRepository
import com.foxnks.dockshift.domain.shift.ShiftRepository
import com.foxnks.dockshift.domain.statistics.ShiftStatistics
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** What the statistics screen shows. */
sealed interface StatisticsUiState {

    /** Shifts are being read. */
    data object Loading : StatisticsUiState

    /**
     * @property years years that have shifts (plus the current one), newest first: the year filter.
     * @property year the selected year.
     * @property month the selected month (1-12), or null for the whole year.
     * @property statistics the totals of the selected period.
     * @property showHolidays whether public holidays are on (the "worked on holidays" total is shown only then).
     */
    data class Ready(
        val years: List<Int>,
        val year: Int,
        val month: Int?,
        val statistics: ShiftStatistics,
        val showHolidays: Boolean,
    ) : StatisticsUiState
}

/**
 * ViewModel of the statistics: the same year/month filter as the shift list over
 * the totals of shifts, overtime and machines. Recomputed whenever a shift changes.
 *
 * @param shifts stored shifts.
 * @param settings whether public holidays are on.
 * @param clock the current time, for the default year.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class StatisticsViewModel @Inject constructor(
    private val shifts: ShiftRepository,
    settings: SettingsRepository,
    clock: Clock,
) : ViewModel() {

    private val currentYear = LocalDate.now(clock).year

    /** The user's chosen year, or null for "the default" (the current year). */
    private val chosenYear = MutableStateFlow<Int?>(null)

    /** The chosen month, or null for the whole year. */
    private val chosenMonth = MutableStateFlow<Int?>(null)

    /** The screen state. */
    val uiState: StateFlow<StatisticsUiState> =
        combine(
            shifts.observeYears(),
            chosenYear,
            chosenMonth,
            settings.settings.map { it.showHolidays }.distinctUntilChanged(),
        ) { years, year, month, showHolidays ->
            // The current year is always offered, like in the shift list
            val allYears = (years + currentYear).distinct().sortedDescending()
            Period(allYears, year ?: currentYear, month, showHolidays)
        }.flatMapLatest { period ->
            val months = if (period.month == null) {
                ShiftStatistics.monthsOf(period.year)
            } else {
                listOf(YearMonth.of(period.year, period.month))
            }
            val from = months.first().atDay(1)
            val to = months.last().atEndOfMonth()
            val holidays = if (period.showHolidays) GreekHolidays.between(from, to).keys else emptySet()
            shifts.observeShifts(from, to).map { list ->
                StatisticsUiState.Ready(
                    years = period.years,
                    year = period.year,
                    month = period.month,
                    statistics = ShiftStatistics.of(list, months, holidays),
                    showHolidays = period.showHolidays,
                )
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = StatisticsUiState.Loading,
        )

    /** @param year the year chip tapped; also resets the month to "all". */
    fun onYearSelected(year: Int) {
        chosenYear.value = year
        chosenMonth.value = null
    }

    /** @param month the month chip tapped (1-12), or null for "all". */
    fun onMonthSelected(month: Int?) {
        chosenMonth.value = month
    }

    /** The chosen filters together, so they are applied as one. */
    private data class Period(val years: List<Int>, val year: Int, val month: Int?, val showHolidays: Boolean)

    private companion object {
        /** Keeps the state alive across a rotation. */
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
