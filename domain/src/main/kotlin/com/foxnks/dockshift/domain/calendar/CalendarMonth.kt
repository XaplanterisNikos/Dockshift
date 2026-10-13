package com.foxnks.dockshift.domain.calendar

import com.foxnks.dockshift.domain.holidays.GreekHoliday
import com.foxnks.dockshift.domain.shift.Shift
import com.foxnks.dockshift.domain.shift.ShiftType
import java.time.LocalDate
import java.time.YearMonth

/**
 * What the calendar knows about one day.
 *
 * Kept as its own type (and not just "a shift or null") because a day can be more
 * than stored or empty: the rotating schedule (#8) predicts days that were never
 * entered. A new kind of day is added here, and every `when` that draws a day then
 * fails to compile until it handles it, so nothing is forgotten.
 */
sealed interface DayStatus {

    /**
     * The user entered (or imported) this day.
     *
     * @property shift the stored shift, with its machines and comment.
     */
    data class Recorded(val shift: Shift) : DayStatus

    /**
     * Nothing stored, but the rotating schedule predicts this type.
     * Shown in gold; never saved until the user records the day.
     *
     * @property type the predicted shift type.
     */
    data class Forecast(val type: ShiftType) : DayStatus

    /** Nothing stored and nothing predicted. */
    data object Empty : DayStatus
}

/**
 * One cell of a month page.
 *
 * @property date the day.
 * @property inMonth false for the faded days of the previous/next month that fill
 * the first and last rows.
 * @property status what is known about the day.
 * @property holiday the public holiday of the day, or null (also null when the user
 * turned holidays off). Independent of [status]: a holiday can be a working day.
 */
data class CalendarDay(
    val date: LocalDate,
    val inMonth: Boolean,
    val status: DayStatus,
    val holiday: GreekHoliday? = null,
)

/**
 * Totals of one month, shown under (or next to) the calendar.
 * Only the days of the month itself count, never the faded neighbour days.
 *
 * @property countByType recorded days per type, in [ShiftType] order, types with 0 days left out.
 * @property workShifts recorded working shifts (morning, afternoon, night).
 * @property workedHours hours of those shifts, overtime included.
 * @property overtimeShifts working shifts with overtime.
 * @property forecastDays days shown as a prediction (not recorded), counted apart.
 * @property holidays the public holidays of the month, by date.
 */
data class MonthSummary(
    val countByType: Map<ShiftType, Int>,
    val workShifts: Int,
    val workedHours: Int,
    val overtimeShifts: Int,
    val forecastDays: Int,
    val holidays: List<Pair<LocalDate, GreekHoliday>> = emptyList(),
) {
    /** True when the month has nothing recorded and nothing predicted. */
    val isEmpty: Boolean
        get() = countByType.isEmpty() && forecastDays == 0

    /** Extra hours from overtime (4 per overtime shift). */
    val overtimeHours: Int
        get() = overtimeShifts * Shift.OVERTIME_HOURS

    companion object {
        /**
         * Adds up [days], skipping the ones outside the month.
         *
         * @param days the cells of a month page.
         */
        fun of(days: List<CalendarDay>): MonthSummary {
            val inMonth = days.filter { it.inMonth }
            val recorded = inMonth.mapNotNull { (it.status as? DayStatus.Recorded)?.shift }
            val counts = recorded.groupingBy { it.type }.eachCount()
            return MonthSummary(
                // Ordered like the enum, so the summary always lists types in the same order
                countByType = ShiftType.entries.filter { it in counts }.associateWith { counts.getValue(it) },
                workShifts = recorded.count { it.type.isWorkShift },
                workedHours = recorded.sumOf { it.workedHours },
                overtimeShifts = recorded.count { it.overtime },
                forecastDays = inMonth.count { it.status is DayStatus.Forecast },
                holidays = inMonth.mapNotNull { day -> day.holiday?.let { day.date to it } },
            )
        }
    }
}

/**
 * Everything one month page shows: its cells and its totals.
 *
 * @property month the month.
 * @property days the [MonthGrid.CELLS] cells, row by row.
 * @property summary the totals of [month].
 */
data class CalendarMonth(
    val month: YearMonth,
    val days: List<CalendarDay>,
    val summary: MonthSummary,
) {
    /**
     * The cell of [date], or null if the page does not show that day.
     *
     * @param date the day to look up.
     */
    fun day(date: LocalDate): CalendarDay? = days.firstOrNull { it.date == date }

    companion object {
        /**
         * Builds the page of [month].
         *
         * A stored shift always wins over a prediction: what the user entered is
         * what happened (or was agreed), the rotation only fills the gaps.
         *
         * @param month the month of the page.
         * @param shifts stored shifts; any that are not on the page are ignored, so a
         * wider list (e.g. three months at once) can be passed as it is.
         * @param holidays public holidays by date (see `GreekHolidays.between`); a wider
         * map can be passed as it is. Empty when the user turned holidays off.
         * @param forecast the predicted type of a day, or null. Nothing predicts yet;
         * the rotating schedule (#8) will plug in here.
         */
        fun of(
            month: YearMonth,
            shifts: Collection<Shift>,
            holidays: Map<LocalDate, GreekHoliday> = emptyMap(),
            forecast: (LocalDate) -> ShiftType? = { null },
        ): CalendarMonth {
            val byDate = shifts.associateBy { it.date }
            val days = MonthGrid.dates(month).map { date ->
                val stored = byDate[date]
                val predicted = if (stored == null) forecast(date) else null
                CalendarDay(
                    date = date,
                    inMonth = YearMonth.from(date) == month,
                    status = when {
                        stored != null -> DayStatus.Recorded(stored)
                        predicted != null -> DayStatus.Forecast(predicted)
                        else -> DayStatus.Empty
                    },
                    holiday = holidays[date],
                )
            }
            return CalendarMonth(month, days, MonthSummary.of(days))
        }
    }
}
