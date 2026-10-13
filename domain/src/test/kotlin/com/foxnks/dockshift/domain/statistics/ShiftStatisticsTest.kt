package com.foxnks.dockshift.domain.statistics

import com.foxnks.dockshift.domain.machine.Machine
import com.foxnks.dockshift.domain.machine.MachineCategory
import com.foxnks.dockshift.domain.shift.MachineAssignment
import com.foxnks.dockshift.domain.shift.Shift
import com.foxnks.dockshift.domain.shift.ShiftType
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Totals of shifts, overtime and machines over a period. */
class ShiftStatisticsTest {

    private val rtg12 = Machine(1, MachineCategory.RTG, "12")
    private val rtg7 = Machine(2, MachineCategory.RTG, "7")
    private val qc31 = Machine(3, MachineCategory.QC, "31")
    private val year2026 = ShiftStatistics.monthsOf(2026)

    private fun day(month: Int, day: Int): LocalDate = LocalDate.of(2026, month, day)

    private val shifts = listOf(
        Shift(day(1, 5), ShiftType.MORNING, machines = listOf(MachineAssignment(rtg12))),
        Shift(
            day(1, 6),
            ShiftType.AFTERNOON,
            overtime = true,
            // RTG 12 twice in the same shift counts once; QC 31 in the overtime hours
            machines = listOf(MachineAssignment(rtg12), MachineAssignment(qc31, duringOvertime = true), MachineAssignment(rtg12)),
        ),
        Shift(day(3, 25), ShiftType.NIGHT, machines = listOf(MachineAssignment(rtg7))),
        Shift(day(3, 26), ShiftType.DAY_OFF),
        Shift(day(8, 1), ShiftType.ANNUAL_LEAVE),
        // Outside the year: ignored
        Shift(LocalDate.of(2025, 12, 31), ShiftType.NIGHT, overtime = true),
    )

    @Test
    fun `counts working shifts, hours and overtime`() {
        val stats = ShiftStatistics.of(shifts, year2026)

        assertEquals(3, stats.workShifts)
        // 3 × 8 regular hours + 4 overtime hours
        assertEquals(28, stats.workedHours)
        assertEquals(1, stats.overtimeShifts)
        assertEquals(4, stats.overtimeHours)
        assertFalse(stats.isEmpty)
    }

    @Test
    fun `days per type in a fixed order`() {
        val stats = ShiftStatistics.of(shifts, year2026)

        assertEquals(
            listOf(ShiftType.MORNING, ShiftType.AFTERNOON, ShiftType.NIGHT, ShiftType.DAY_OFF, ShiftType.ANNUAL_LEAVE),
            stats.countByType.keys.toList(),
        )
        assertTrue(stats.countByType.values.all { it == 1 })
    }

    @Test
    fun `every month of the period is listed, empty ones too`() {
        val months = ShiftStatistics.of(shifts, year2026).months

        assertEquals(12, months.size)
        assertEquals(MonthStat(YearMonth.of(2026, 1), workShifts = 2, overtimeShifts = 1), months[0])
        assertEquals(MonthStat(YearMonth.of(2026, 2), workShifts = 0, overtimeShifts = 0), months[1])
        assertEquals(1, months[2].workShifts)
    }

    @Test
    fun `machines are counted once per shift, most driven first`() {
        val machines = ShiftStatistics.of(shifts, year2026).machines

        // RTG 7 and QC 31 tie with 1 shift: the usual machine order (RTG before QC) decides
        assertEquals(listOf(rtg12, rtg7, qc31), machines.map { it.machine })
        assertEquals(MachineStat(rtg12, shifts = 2, overtimeShifts = 0), machines[0])
        assertEquals(MachineStat(qc31, shifts = 1, overtimeShifts = 1), machines[2])
    }

    @Test
    fun `categories are counted once per shift`() {
        val categories = ShiftStatistics.of(shifts, year2026).categories

        // RTG: 5/1, 6/1 and 25/3; QC: 6/1
        assertEquals(listOf(CategoryStat(MachineCategory.RTG, 3), CategoryStat(MachineCategory.QC, 1)), categories)
    }

    @Test
    fun `holidays worked count only working shifts on a holiday`() {
        val holidays = setOf(day(3, 25), day(8, 1), day(10, 28))
        // 25 March: night shift (worked); 1 August: annual leave (not worked)
        assertEquals(1, ShiftStatistics.of(shifts, year2026, holidays).holidaysWorked)
    }

    @Test
    fun `a single month only counts that month`() {
        val stats = ShiftStatistics.of(shifts, listOf(YearMonth.of(2026, 3)))

        assertEquals(1, stats.workShifts)
        assertEquals(listOf(MonthStat(YearMonth.of(2026, 3), 1, 0)), stats.months)
        assertEquals(listOf(rtg7), stats.machines.map { it.machine })
    }

    @Test
    fun `a period without shifts is empty`() {
        val stats = ShiftStatistics.of(emptyList(), year2026)

        assertTrue(stats.isEmpty)
        assertEquals(0, stats.workShifts)
        assertTrue(stats.machines.isEmpty())
        assertEquals(12, stats.months.size)
    }
}
