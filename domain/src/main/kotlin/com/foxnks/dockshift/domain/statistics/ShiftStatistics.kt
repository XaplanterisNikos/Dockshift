package com.foxnks.dockshift.domain.statistics

import com.foxnks.dockshift.domain.machine.Machine
import com.foxnks.dockshift.domain.machine.MachineCategory
import com.foxnks.dockshift.domain.shift.Shift
import com.foxnks.dockshift.domain.shift.ShiftType
import java.time.LocalDate
import java.time.YearMonth

/**
 * Working shifts and overtime of one month, for the month-by-month bars.
 *
 * @property month the month.
 * @property workShifts working shifts recorded in it.
 * @property overtimeShifts how many of them had overtime.
 */
data class MonthStat(val month: YearMonth, val workShifts: Int, val overtimeShifts: Int)

/**
 * How often one machine was driven.
 *
 * @property machine the machine (as it is now: number and location may have changed).
 * @property shifts shifts in which it was driven, counted once per shift.
 * @property overtimeShifts shifts in which it was driven in the overtime hours.
 */
data class MachineStat(val machine: Machine, val shifts: Int, val overtimeShifts: Int)

/**
 * How often a machine category was driven.
 *
 * @property category the category.
 * @property shifts shifts with at least one machine of this category.
 */
data class CategoryStat(val category: MachineCategory, val shifts: Int)

/**
 * Totals of a period: shifts, overtime and machines.
 *
 * Only recorded shifts count; predictions of the rotating schedule never do, because
 * statistics describe what was actually worked.
 *
 * @property workShifts working shifts (morning, afternoon, night).
 * @property workedHours their hours, overtime included.
 * @property overtimeShifts working shifts with overtime.
 * @property countByType days per type, in [ShiftType] order, types with 0 days left out.
 * @property holidaysWorked working shifts on a public holiday.
 * @property months one entry per month of the period, in order, months without shifts included.
 * @property machines every machine driven, most driven first.
 * @property categories every category driven, most driven first.
 */
data class ShiftStatistics(
    val workShifts: Int,
    val workedHours: Int,
    val overtimeShifts: Int,
    val countByType: Map<ShiftType, Int>,
    val holidaysWorked: Int,
    val months: List<MonthStat>,
    val machines: List<MachineStat>,
    val categories: List<CategoryStat>,
) {
    /** Extra hours from overtime (4 per overtime shift). */
    val overtimeHours: Int
        get() = overtimeShifts * Shift.OVERTIME_HOURS

    /** True when the period has no recorded day at all. */
    val isEmpty: Boolean
        get() = countByType.isEmpty()

    companion object {
        /**
         * Adds up the shifts of a period.
         *
         * @param shifts the recorded shifts; any outside [months] are ignored.
         * @param months the months of the period, e.g. the 12 of a year or a single one.
         * @param holidays public holiday dates, to count the holidays worked (empty when
         * the user turned holidays off).
         */
        fun of(
            shifts: Collection<Shift>,
            months: List<YearMonth>,
            holidays: Set<LocalDate> = emptySet(),
        ): ShiftStatistics {
            val period = months.toSet()
            val inPeriod = shifts.filter { YearMonth.from(it.date) in period }
            val worked = inPeriod.filter { it.type.isWorkShift }
            val counts = inPeriod.groupingBy { it.type }.eachCount()
            val byMonth = worked.groupBy { YearMonth.from(it.date) }

            return ShiftStatistics(
                workShifts = worked.size,
                workedHours = worked.sumOf { it.workedHours },
                overtimeShifts = worked.count { it.overtime },
                // Ordered like the enum, so types always appear in the same order
                countByType = ShiftType.entries.filter { it in counts }.associateWith { counts.getValue(it) },
                holidaysWorked = worked.count { it.date in holidays },
                months = months.map { month ->
                    val list = byMonth[month].orEmpty()
                    MonthStat(month, list.size, list.count { it.overtime })
                },
                machines = machineStats(inPeriod),
                categories = categoryStats(inPeriod),
            )
        }

        /** Per machine, counted once per shift even if it was driven twice in that shift. */
        private fun machineStats(shifts: List<Shift>): List<MachineStat> {
            val driven = mutableMapOf<Long, Machine>()
            val total = mutableMapOf<Long, Int>()
            val overtime = mutableMapOf<Long, Int>()
            shifts.forEach { shift ->
                shift.machines.groupBy { it.machine.id }.forEach { (id, assignments) ->
                    driven[id] = assignments.first().machine
                    total.merge(id, 1, Int::plus)
                    if (assignments.any { it.duringOvertime }) overtime.merge(id, 1, Int::plus)
                }
            }
            return driven.values
                .map { MachineStat(it, total.getValue(it.id), overtime[it.id] ?: 0) }
                // Most driven first; ties in the usual machine order (category, then number)
                .sortedWith(
                    compareByDescending<MachineStat> { it.shifts }
                        .thenBy { it.machine.category.ordinal }
                        .thenBy { it.machine.number.toIntOrNull() ?: Int.MAX_VALUE }
                        .thenBy { it.machine.number },
                )
        }

        /** Per category, counted once per shift. */
        private fun categoryStats(shifts: List<Shift>): List<CategoryStat> =
            shifts
                .flatMap { shift -> shift.machines.map { it.machine.category }.distinct() }
                .groupingBy { it }
                .eachCount()
                .map { (category, count) -> CategoryStat(category, count) }
                .sortedWith(compareByDescending<CategoryStat> { it.shifts }.thenBy { it.category.ordinal })

        /**
         * The 12 months of [year], for a year's statistics.
         *
         * @param year the year.
         */
        fun monthsOf(year: Int): List<YearMonth> = (1..12).map { YearMonth.of(year, it) }
    }
}
