package com.foxnks.dockshift.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.foxnks.dockshift.domain.shift.Shift
import com.foxnks.dockshift.domain.shift.ShiftRepository
import com.foxnks.dockshift.domain.shift.WorkDay
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * One day of the "next days" list.
 *
 * @property date the day.
 * @property shift its stored shift, or null if nothing is recorded.
 */
data class DayEntry(val date: LocalDate, val shift: Shift?)

/** What the "Today" screen shows. */
sealed interface TodayUiState {

    /** Shifts are being read. */
    data object Loading : TodayUiState

    /**
     * @property currentDate the day whose shift is current (yesterday while last night's shift runs).
     * @property nightFromYesterday true when [currentDate] is yesterday because its night shift is still running.
     * @property current the shift of [currentDate], or null if nothing is recorded.
     * @property upcoming the days after [currentDate].
     */
    data class Ready(
        val currentDate: LocalDate,
        val nightFromYesterday: Boolean,
        val current: Shift?,
        val upcoming: List<DayEntry>,
    ) : TodayUiState
}

/**
 * ViewModel of the "Today" screen: the current shift and the next [UPCOMING_DAYS] days.
 *
 * @param shifts stored shifts.
 * @param clock the current time; injected so tests can fix it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TodayViewModel @Inject constructor(
    private val shifts: ShiftRepository,
    private val clock: Clock,
) : ViewModel() {

    /** The time the screen is computed for; refreshed when the screen comes back to the front. */
    private val now = MutableStateFlow(LocalDateTime.now(clock))

    /** The screen state, always up to date with the stored shifts. */
    val uiState: StateFlow<TodayUiState> = now
        .flatMapLatest { time ->
            val today = time.toLocalDate()
            // From yesterday (a night may still be running) to a week after today
            shifts.observeShifts(today.minusDays(1), today.plusDays(UPCOMING_DAYS + 1L))
                .map { list -> build(time, list.associateBy { it.date }) }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = TodayUiState.Loading,
        )

    /**
     * Re-reads the clock, e.g. when the app returns from the background after
     * midnight or after 07:00. Cheap: the stored shifts are only re-queried if the
     * day window actually moved.
     */
    fun refresh() {
        now.value = LocalDateTime.now(clock)
    }

    /** Builds the screen state for [time] from the shifts around it. */
    private fun build(time: LocalDateTime, byDate: Map<LocalDate, Shift>): TodayUiState.Ready {
        val today = time.toLocalDate()
        val current = WorkDay.current(time, byDate[today.minusDays(1)]?.type)
        return TodayUiState.Ready(
            currentDate = current,
            nightFromYesterday = current != today,
            current = byDate[current],
            upcoming = (1..UPCOMING_DAYS).map { offset ->
                val date = current.plusDays(offset.toLong())
                DayEntry(date, byDate[date])
            },
        )
    }

    companion object {
        /** How many days after the current one are listed. */
        const val UPCOMING_DAYS = 7

        /** Keeps the state alive across a rotation. */
        private const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
