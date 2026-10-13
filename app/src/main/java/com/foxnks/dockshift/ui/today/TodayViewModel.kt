package com.foxnks.dockshift.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.foxnks.dockshift.domain.holidays.GreekHoliday
import com.foxnks.dockshift.domain.holidays.GreekHolidays
import com.foxnks.dockshift.domain.leave.Leave
import com.foxnks.dockshift.domain.leave.LeaveRepository
import com.foxnks.dockshift.domain.rotation.RotationAnchor
import com.foxnks.dockshift.domain.rotation.RotationSchedule
import com.foxnks.dockshift.domain.settings.SettingsRepository
import com.foxnks.dockshift.domain.settings.WorkSchedule
import com.foxnks.dockshift.domain.shift.Shift
import com.foxnks.dockshift.domain.shift.ShiftRepository
import com.foxnks.dockshift.domain.shift.ShiftType
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * One day of the "next days" list.
 *
 * @property date the day.
 * @property shift its stored shift, or null if nothing is recorded.
 * @property holiday its public holiday, or null (also when holidays are turned off).
 * @property forecast the type the rotating schedule predicts when nothing is stored, or null.
 * @property leave the planned leave the day belongs to, when nothing is stored, or null.
 */
data class DayEntry(
    val date: LocalDate,
    val shift: Shift?,
    val holiday: GreekHoliday? = null,
    val forecast: ShiftType? = null,
    val leave: Leave? = null,
)

/** What the "Today" screen shows. */
sealed interface TodayUiState {

    /** Shifts are being read. */
    data object Loading : TodayUiState

    /**
     * @property currentDate the day whose shift is current (yesterday while last night's shift runs).
     * @property nightFromYesterday true when [currentDate] is yesterday because its night shift is still running.
     * @property current the shift of [currentDate], or null if nothing is recorded.
     * @property upcoming the days after [currentDate].
     * @property currentHoliday the public holiday of [currentDate], or null.
     * @property currentForecast the predicted type of [currentDate] when nothing is stored, or null.
     * @property currentLeave the planned leave of [currentDate] when nothing is stored, or null.
     */
    data class Ready(
        val currentDate: LocalDate,
        val nightFromYesterday: Boolean,
        val current: Shift?,
        val upcoming: List<DayEntry>,
        val currentHoliday: GreekHoliday? = null,
        val currentForecast: ShiftType? = null,
        val currentLeave: Leave? = null,
    ) : TodayUiState
}

/**
 * ViewModel of the "Today" screen: the current shift and the next [UPCOMING_DAYS] days.
 *
 * @param shifts stored shifts.
 * @param leaves planned leaves.
 * @param settings whether public holidays are marked, and the rotation for predictions.
 * @param clock the current time; injected so tests can fix it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TodayViewModel @Inject constructor(
    private val shifts: ShiftRepository,
    private val leaves: LeaveRepository,
    settings: SettingsRepository,
    private val clock: Clock,
) : ViewModel() {

    /** The time the screen is computed for; refreshed when the screen comes back to the front. */
    private val now = MutableStateFlow(LocalDateTime.now(clock))

    /** The screen state, always up to date with the stored shifts. */
    val uiState: StateFlow<TodayUiState> =
        combine(
            now,
            settings.settings.map { DaySettings(it.showHolidays, it.workSchedule, it.rotationAnchor) }.distinctUntilChanged(),
        ) { time, daySettings -> time to daySettings }.flatMapLatest { (time, daySettings) ->
            val today = time.toLocalDate()
            // From yesterday (a night may still be running) to a week after today
            val from = today.minusDays(1)
            val to = today.plusDays(UPCOMING_DAYS + 1L)
            val holidays = if (daySettings.showHolidays) GreekHolidays.between(from, to) else emptyMap()
            val forecast = { date: LocalDate ->
                RotationSchedule.forecastOn(daySettings.workSchedule, daySettings.rotationAnchor, date, today)
            }
            combine(shifts.observeShifts(from, to), leaves.observeBetween(from, to)) { list, leaveList ->
                build(time, list.associateBy { it.date }, holidays, forecast, leaveList)
            }
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

    /** Builds the screen state for [time] from the shifts and holidays around it. */
    private fun build(
        time: LocalDateTime,
        byDate: Map<LocalDate, Shift>,
        holidays: Map<LocalDate, GreekHoliday>,
        forecast: (LocalDate) -> ShiftType?,
        leaveList: List<Leave>,
    ): TodayUiState.Ready {
        /** The leave of a day without a stored shift: a stored shift always wins. */
        fun leaveOn(date: LocalDate): Leave? = if (byDate[date] == null) leaveList.firstOrNull { date in it } else null

        val today = time.toLocalDate()
        val current = WorkDay.current(time, byDate[today.minusDays(1)]?.type)
        return TodayUiState.Ready(
            currentDate = current,
            nightFromYesterday = current != today,
            current = byDate[current],
            upcoming = (1..UPCOMING_DAYS).map { offset ->
                val date = current.plusDays(offset.toLong())
                val shift = byDate[date]
                val leave = leaveOn(date)
                // A stored shift wins over leave, and leave over the prediction
                DayEntry(date, shift, holidays[date], if (shift == null && leave == null) forecast(date) else null, leave)
            },
            currentHoliday = holidays[current],
            currentForecast = if (byDate[current] == null && leaveOn(current) == null) forecast(current) else null,
            currentLeave = leaveOn(current),
        )
    }

    /** The settings the screen depends on; changing anything else must not re-read it. */
    private data class DaySettings(
        val showHolidays: Boolean,
        val workSchedule: WorkSchedule,
        val rotationAnchor: RotationAnchor?,
    )

    companion object {
        /** How many days after the current one are listed. */
        const val UPCOMING_DAYS = 7

        /** Keeps the state alive across a rotation. */
        private const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
