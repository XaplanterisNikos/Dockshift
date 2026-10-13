package com.foxnks.dockshift.ui.leave

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.foxnks.dockshift.domain.holidays.GreekHoliday
import com.foxnks.dockshift.domain.leave.Leave
import com.foxnks.dockshift.domain.leave.LeaveBalance
import com.foxnks.dockshift.domain.leave.LeaveRepository
import com.foxnks.dockshift.domain.leave.LeaveRules
import com.foxnks.dockshift.domain.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * One leave in the list of a year.
 *
 * @property leave the leave.
 * @property countedDays its days that count in the shown year.
 */
data class LeaveRow(val leave: Leave, val countedDays: Int)

/** What the leave screen shows. */
sealed interface LeavesUiState {

    /** Leaves and settings are being read. */
    data object Loading : LeavesUiState

    /**
     * @property years the years offered (around the current one, plus any with leave), in order.
     * @property year the selected year.
     * @property entitlementSet whether the entitlement was ever set (else the screen invites to set it).
     * @property balance entitlement, taken, planned and remaining days of [year].
     * @property leaves the leaves with days in [year], by start.
     * @property portClosed the holidays on which the port is closed.
     */
    data class Ready(
        val years: List<Int>,
        val year: Int,
        val entitlementSet: Boolean,
        val balance: LeaveBalance,
        val leaves: List<LeaveRow>,
        val portClosed: Set<GreekHoliday>,
    ) : LeavesUiState
}

/**
 * ViewModel of Menu → Leave: the yearly entitlement, the planned leaves of a year
 * with the days left, and the holidays on which the port is closed.
 *
 * @param leaves stored leaves.
 * @param settings entitlements and closed holidays.
 * @param clock the current time, for the default year and "taken" versus "planned".
 */
@HiltViewModel
class LeavesViewModel @Inject constructor(
    leaves: LeaveRepository,
    private val settings: SettingsRepository,
    clock: Clock,
) : ViewModel() {

    private val today = LocalDate.now(clock)

    /** The chosen year; starts on the current one. */
    private val chosenYear = MutableStateFlow(today.year)

    /** The screen state. */
    val uiState: StateFlow<LeavesUiState> =
        combine(leaves.observeAll(), settings.settings, chosenYear) { all, saved, year ->
            // Leave is planned ahead: offer the year before and a few years after, plus any year with leave
            val years = ((today.year - 1)..(today.year + YEARS_AHEAD)).toSet() +
                all.flatMap { listOf(it.start.year, it.end.year) }
            val ofYear = all.filter { it.start.year <= year && it.end.year >= year }
            val entitlement = LeaveRules.entitlementFor(year, saved.leaveEntitlements)
            LeavesUiState.Ready(
                years = years.sorted(),
                year = year,
                entitlementSet = entitlement != null,
                balance = LeaveBalance.of(year, entitlement, ofYear, saved.portClosedHolidays, today),
                leaves = ofYear.map { LeaveRow(it, LeaveRules.countedDays(it, saved.portClosedHolidays, year)) },
                portClosed = saved.portClosedHolidays,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = LeavesUiState.Loading,
        )

    /** @param year the year chip tapped. */
    fun onYearSelected(year: Int) {
        chosenYear.value = year
    }

    /**
     * Saves the entitlement of the shown year; kept within `LeaveRules.ENTITLEMENT_RANGE`.
     *
     * @param days the new number of days.
     */
    fun onEntitlementChanged(days: Int) {
        val year = chosenYear.value
        viewModelScope.launch {
            settings.setLeaveEntitlement(year, days.coerceIn(LeaveRules.ENTITLEMENT_RANGE))
        }
    }

    /**
     * Marks a holiday as one on which the port is closed (it then does not count in leave), or not.
     *
     * @param holiday the holiday.
     * @param closed true when the port is closed on it.
     */
    fun onPortClosedToggled(holiday: GreekHoliday, closed: Boolean) {
        viewModelScope.launch {
            val current = settings.settings.first().portClosedHolidays
            settings.setPortClosedHolidays(if (closed) current + holiday else current - holiday)
        }
    }

    private companion object {
        /** How many years after the current one are offered. */
        const val YEARS_AHEAD = 2

        /** Keeps the state alive across a rotation. */
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
