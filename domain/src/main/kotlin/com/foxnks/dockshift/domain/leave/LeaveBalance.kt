package com.foxnks.dockshift.domain.leave

import com.foxnks.dockshift.domain.holidays.GreekHoliday
import com.foxnks.dockshift.domain.holidays.GreekHolidays
import java.time.LocalDate

/** How leave days are counted, and the limits of the yearly entitlement. */
object LeaveRules {

    /** The yearly entitlement the user can choose (it depends on years of service). */
    val ENTITLEMENT_RANGE: IntRange = 10..60

    /** The value offered when the user sets the entitlement for the first time. */
    const val DEFAULT_ENTITLEMENT = 22

    /**
     * The days of [leave] that count against the entitlement: every day, except the
     * public holidays on which the port is closed (the user chooses which those are;
     * on the other holidays the port works, so they count).
     *
     * @param leave the leave.
     * @param portClosed the holidays on which the port is closed.
     * @param year count only the days of this year (a leave can cross New Year), or null for all.
     */
    fun countedDays(leave: Leave, portClosed: Set<GreekHoliday>, year: Int? = null): Int {
        val closedDates = closedDates(leave.start, leave.end, portClosed)
        return leave.days.count { (year == null || it.year == year) && it !in closedDates }
    }

    /**
     * The dates between two days (both included) on which the port is closed.
     *
     * @param from first day.
     * @param to last day.
     * @param portClosed the holidays on which the port is closed.
     */
    fun closedDates(from: LocalDate, to: LocalDate, portClosed: Set<GreekHoliday>): Set<LocalDate> =
        if (portClosed.isEmpty()) emptySet() else GreekHolidays.between(from, to).filterValues { it in portClosed }.keys

    /**
     * The entitlement that applies to [year]: the one set for it, or else the latest one
     * set for an earlier year (it rarely changes), or null when none was ever set.
     *
     * @param year the year.
     * @param byYear the entitlements the user set, by year.
     */
    fun entitlementFor(year: Int, byYear: Map<Int, Int>): Int? =
        byYear[year] ?: byYear.filterKeys { it < year }.maxByOrNull { it.key }?.value
}

/**
 * The leave of one year at a glance.
 *
 * @property year the year.
 * @property entitlement the days the user is entitled to, or null when not set.
 * @property taken counted leave days up to and including today.
 * @property planned counted leave days after today.
 */
data class LeaveBalance(
    val year: Int,
    val entitlement: Int?,
    val taken: Int,
    val planned: Int,
) {
    /** All counted days of the year, taken and planned. */
    val used: Int
        get() = taken + planned

    /** Days still to plan; negative when more is planned than allowed; null without an entitlement. */
    val remaining: Int?
        get() = entitlement?.minus(used)

    companion object {
        /**
         * Adds up the leaves of [year].
         *
         * @param year the year.
         * @param entitlement the year's entitlement, or null.
         * @param leaves leaves; only their days in [year] count.
         * @param portClosed the holidays on which the port is closed (they do not count).
         * @param today the current date: up to it is "taken", after it "planned".
         */
        fun of(year: Int, entitlement: Int?, leaves: List<Leave>, portClosed: Set<GreekHoliday>, today: LocalDate): LeaveBalance {
            var taken = 0
            var planned = 0
            leaves.forEach { leave ->
                val closed = LeaveRules.closedDates(leave.start, leave.end, portClosed)
                leave.days.filter { it.year == year && it !in closed }.forEach { day ->
                    if (day <= today) taken++ else planned++
                }
            }
            return LeaveBalance(year, entitlement, taken, planned)
        }
    }
}

/** What is wrong with a leave being edited, if anything. */
sealed interface LeaveProblem {

    /** The last day is before the first. */
    data object EndBeforeStart : LeaveProblem

    /**
     * It shares days with another leave.
     *
     * @property other the leave it overlaps.
     */
    data class Overlaps(val other: Leave) : LeaveProblem

    companion object {
        /**
         * Checks a leave before saving.
         *
         * @param start the first day picked.
         * @param end the last day picked.
         * @param id the leave's identity (0 for a new one), so it is not compared with itself.
         * @param others every stored leave.
         * @return the problem, or null when it can be saved.
         */
        fun of(start: LocalDate, end: LocalDate, id: Long, others: List<Leave>): LeaveProblem? {
            if (end < start) return EndBeforeStart
            val candidate = Leave(id, start, end)
            return others.firstOrNull { it.id != id && it.overlaps(candidate) }?.let { Overlaps(it) }
        }
    }
}
