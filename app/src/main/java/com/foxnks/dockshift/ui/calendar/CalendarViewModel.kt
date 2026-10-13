package com.foxnks.dockshift.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.foxnks.dockshift.domain.calendar.CalendarDay
import com.foxnks.dockshift.domain.calendar.CalendarMonth
import com.foxnks.dockshift.domain.calendar.DayStatus
import com.foxnks.dockshift.domain.calendar.MonthGrid
import com.foxnks.dockshift.domain.holidays.GreekHolidays
import com.foxnks.dockshift.domain.leave.LeaveRepository
import com.foxnks.dockshift.domain.rotation.RotationAnchor
import com.foxnks.dockshift.domain.rotation.RotationSchedule
import com.foxnks.dockshift.domain.settings.AppSettings
import com.foxnks.dockshift.domain.settings.CalendarView
import com.foxnks.dockshift.domain.settings.SettingsRepository
import com.foxnks.dockshift.domain.settings.WorkSchedule
import com.foxnks.dockshift.domain.shift.Shift
import com.foxnks.dockshift.domain.shift.ShiftRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * What the calendar shows.
 *
 * @property today the current date (the cell with the circled number).
 * @property visibleMonth the month on screen.
 * @property months pages of [visibleMonth] and of the month before and after it, so
 * the neighbour pages are already filled while the user swipes. A page that is not
 * here yet (e.g. right after a fast swipe) is drawn empty for a moment.
 * @property selectedDate the day whose details are shown; always inside [visibleMonth].
 * @property viewPortrait what is shown under the grid when the phone is upright.
 * @property viewLandscape what is shown beside the grid when the phone is sideways.
 */
data class CalendarUiState(
    val today: LocalDate,
    val visibleMonth: YearMonth,
    val months: Map<YearMonth, CalendarMonth>,
    val selectedDate: LocalDate,
    val viewPortrait: CalendarView,
    val viewLandscape: CalendarView,
) {
    /** The page of [visibleMonth], or null while it is being read. */
    val visible: CalendarMonth?
        get() = months[visibleMonth]

    /** The cell of [selectedDate], or null while the page is being read. */
    val selectedDay: CalendarDay?
        get() = visible?.day(selectedDate)

    /**
     * The page of [month]: the loaded one, or an empty grid while it is being read.
     *
     * @param month any month (the pager asks for pages it is about to show).
     */
    fun page(month: YearMonth): CalendarMonth = months[month] ?: CalendarMonth.of(month, emptyList())

    /**
     * What is shown next to the grid in one orientation.
     *
     * @param landscape true when the screen is wider than tall.
     */
    fun view(landscape: Boolean): CalendarView = if (landscape) viewLandscape else viewPortrait
}

/**
 * ViewModel of the calendar: the visible month (with its neighbours), the
 * selected day and the panel shown next to the grid.
 *
 * The pager on screen owns the scrolling; it reports the month it settled on
 * through [onMonthShown], and only then are that month's shifts read.
 *
 * @param shifts stored shifts.
 * @param leaves planned leaves.
 * @param settings where the chosen panel ([CalendarView]) of each orientation is kept.
 * @param clock the current time; injected so tests can fix "today".
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val shifts: ShiftRepository,
    private val leaves: LeaveRepository,
    private val settings: SettingsRepository,
    private val clock: Clock,
) : ViewModel() {

    /** The current date; refreshed when the screen comes back to the front. */
    private val today = MutableStateFlow(LocalDate.now(clock))

    /** The month the pager shows. Starts on the current month. */
    private val visibleMonth = MutableStateFlow(YearMonth.from(today.value))

    /** The selected day. Starts on today. */
    private val selectedDate = MutableStateFlow(today.value)

    /**
     * The visible month together with its pages. Kept as one pair so the month and
     * the pages can never come from two different moments.
     */
    private val pages: Flow<Pair<YearMonth, Map<YearMonth, CalendarMonth>>> =
        combine(
            visibleMonth,
            today,
            // Only the settings that change the pages: not the panel choice, for example
            settings.settings.map { PageSettings(it.showHolidays, it.workSchedule, it.rotationAnchor) }.distinctUntilChanged(),
        ) { month, now, pageSettings -> Triple(month, now, pageSettings) }
            .flatMapLatest { (month, now, pageSettings) ->
                val shown = listOf(month.minusMonths(1), month, month.plusMonths(1))
                // One query from the first cell of the previous page to the last cell of the next
                val from = MonthGrid.range(shown.first()).start
                val to = MonthGrid.range(shown.last()).endInclusive
                // Holidays are computed, not stored: worked out once per month change
                val holidays = if (pageSettings.showHolidays) GreekHolidays.between(from, to) else emptyMap()
                // Predictions too: gold days from today on, when the rotation place is set
                val forecast = { date: LocalDate ->
                    RotationSchedule.forecastOn(pageSettings.workSchedule, pageSettings.rotationAnchor, date, now)
                }
                combine(shifts.observeShifts(from, to), leaves.observeBetween(from, to)) { list, leaveList ->
                    month to shown.associateWith {
                        CalendarMonth.of(it, list, holidays = holidays, leaves = leaveList, forecast = forecast)
                    }
                }
            }

    /** The screen state, always up to date with the stored shifts and settings. */
    val uiState: StateFlow<CalendarUiState> =
        combine(pages, selectedDate, today, settings.settings) { (month, months), selected, now, appSettings ->
            CalendarUiState(
                today = now,
                visibleMonth = month,
                months = months,
                selectedDate = selected,
                viewPortrait = appSettings.calendarViewPortrait,
                viewLandscape = appSettings.calendarViewLandscape,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = initialState(),
        )

    /**
     * The pager settled on [month]. Reads its shifts, and moves the selection into it
     * if needed: to today in the current month, to the 1st in any other.
     *
     * @param month the month now on screen.
     */
    fun onMonthShown(month: YearMonth) {
        visibleMonth.value = month
        if (YearMonth.from(selectedDate.value) != month) {
            selectedDate.value = if (YearMonth.from(today.value) == month) today.value else month.atDay(1)
        }
    }

    /**
     * A day was tapped in day-details mode. A faded day of a neighbour month is
     * selected too; the screen then swipes to that month, and [onMonthShown] keeps
     * this selection because it is inside the new month.
     *
     * @param date the tapped day.
     */
    fun onDaySelected(date: LocalDate) {
        selectedDate.value = date
    }

    /** The "Today" button: select today (the screen swipes back to its month). */
    fun onToday() {
        selectedDate.value = today.value
    }

    /**
     * The view button: switches the panel of the current orientation between the
     * month summary and the day details, and remembers the choice.
     *
     * @param landscape the orientation the button was pressed in.
     */
    fun onToggleView(landscape: Boolean) {
        viewModelScope.launch {
            val current = settings.settings.first().calendarView(landscape)
            val next = if (current == CalendarView.SUMMARY) CalendarView.DAY_DETAILS else CalendarView.SUMMARY
            settings.setCalendarView(landscape, next)
        }
    }

    /**
     * Records every predicted day of the visible month as a shift of the predicted type,
     * after the screen's confirmation. Days that already have a shift are never touched
     * (they are not predictions), and each saved day can still be edited afterwards.
     */
    fun onRecordForecasts() {
        val page = uiState.value.visible ?: return
        viewModelScope.launch {
            page.days
                .filter { it.inMonth }
                .forEach { day ->
                    val status = day.status
                    if (status is DayStatus.Forecast) shifts.save(Shift(day.date, status.type))
                }
        }
    }

    /** Re-reads the date, e.g. when the app comes back from the background after midnight. */
    fun refresh() {
        today.value = LocalDate.now(clock)
    }

    /** The state before anything is read: the current month, empty, with default panels. */
    private fun initialState(): CalendarUiState {
        val defaults = AppSettings()
        return CalendarUiState(
            today = today.value,
            visibleMonth = visibleMonth.value,
            months = emptyMap(),
            selectedDate = selectedDate.value,
            viewPortrait = defaults.calendarViewPortrait,
            viewLandscape = defaults.calendarViewLandscape,
        )
    }

    /**
     * The settings a page depends on, and nothing else: changing the panel choice, for
     * example, must not rebuild the pages.
     */
    private data class PageSettings(
        val showHolidays: Boolean,
        val workSchedule: WorkSchedule,
        val rotationAnchor: RotationAnchor?,
    )

    /** The panel of one orientation in these settings. */
    private fun AppSettings.calendarView(landscape: Boolean): CalendarView =
        if (landscape) calendarViewLandscape else calendarViewPortrait

    private companion object {
        /** Keeps the state alive across a rotation. */
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
