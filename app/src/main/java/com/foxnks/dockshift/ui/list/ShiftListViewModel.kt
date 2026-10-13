package com.foxnks.dockshift.ui.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.foxnks.dockshift.domain.shift.Shift
import com.foxnks.dockshift.domain.shift.ShiftRepository
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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * The shifts of one month, newest first.
 *
 * @property month the month.
 * @property shifts its shifts, newest first.
 */
data class MonthGroup(val month: YearMonth, val shifts: List<Shift>)

/** What the shift list shows. */
sealed interface ShiftListUiState {

    /** Shifts are being read. */
    data object Loading : ShiftListUiState

    /**
     * @property years years that have shifts (plus the current one), newest first: the year filter.
     * @property year the selected year.
     * @property month the selected month (1-12), or null for the whole year.
     * @property groups the matching shifts grouped by month, newest month first.
     */
    data class Ready(
        val years: List<Int>,
        val year: Int,
        val month: Int?,
        val groups: List<MonthGroup>,
    ) : ShiftListUiState
}

/**
 * ViewModel of the shift list: year and month filters over the stored shifts.
 * The year filter only offers years that have data, so it grows by itself.
 *
 * @param shifts stored shifts.
 * @param clock the current time, for the default year.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ShiftListViewModel @Inject constructor(
    private val shifts: ShiftRepository,
    clock: Clock,
) : ViewModel() {

    private val currentYear = LocalDate.now(clock).year

    /** The user's chosen year, or null for "the default" (the current year). */
    private val chosenYear = MutableStateFlow<Int?>(null)

    /** The chosen month, or null for the whole year. */
    private val chosenMonth = MutableStateFlow<Int?>(null)

    /** The screen state. */
    val uiState: StateFlow<ShiftListUiState> =
        combine(shifts.observeYears(), chosenYear, chosenMonth) { years, year, month ->
            // The current year is always offered, so new users see where their shifts will go
            val allYears = (years + currentYear).distinct().sortedDescending()
            Triple(allYears, year ?: currentYear, month)
        }.flatMapLatest { (years, year, month) ->
            val from = if (month == null) LocalDate.of(year, 1, 1) else YearMonth.of(year, month).atDay(1)
            val to = if (month == null) LocalDate.of(year, 12, 31) else YearMonth.of(year, month).atEndOfMonth()
            shifts.observeShifts(from, to).map { list ->
                ShiftListUiState.Ready(
                    years = years,
                    year = year,
                    month = month,
                    groups = list
                        .sortedByDescending { it.date }
                        .groupBy { YearMonth.from(it.date) }
                        .map { (month, shifts) -> MonthGroup(month, shifts) },
                )
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = ShiftListUiState.Loading,
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

    private companion object {
        /** Keeps the state alive across a rotation. */
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
