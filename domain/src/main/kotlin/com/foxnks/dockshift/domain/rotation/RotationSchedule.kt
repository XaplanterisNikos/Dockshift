package com.foxnks.dockshift.domain.rotation

import com.foxnks.dockshift.domain.settings.WorkSchedule
import com.foxnks.dockshift.domain.shift.Shift
import com.foxnks.dockshift.domain.shift.ShiftType
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Where an operator is in the rotating schedule: the first day of one of their
 * four-day rests, and the shift type that came right after it.
 *
 * The date alone places the operator in the 49-day cycle; the type tells which of
 * the three shift types the cycle started with, which together give the exact place
 * in the full 147-day pattern. Any of the operator's cycles can serve as anchor.
 *
 * @property cycleStart the first day of a four-day rest.
 * @property firstType the shift after that rest: afternoon, morning or night.
 */
data class RotationAnchor(val cycleStart: LocalDate, val firstType: ShiftType) {
    init {
        require(firstType in RotationSchedule.TYPE_ORDER) { "$firstType is not a working shift of the rotation" }
    }
}

/**
 * The rotating schedule of the terminal ("κυλιόμενο πρόγραμμα"), checked against 28
 * dates of real schedules from 2024 to 2027.
 *
 * One cycle is 49 days (7 weeks), always in this order:
 * 4 rest, 6 × block 1, 2 rest, 6 × block 2, 2 rest, 6 × block 3, 2 rest,
 * 2 × block 4, 1 rest, 5 × block 4, 1 rest, 4 × block 5, 2 rest, 6 × block 5.
 *
 * The five work blocks take the shift types in a fixed order, afternoon → morning →
 * night → afternoon …, continuing from one cycle to the next. Five blocks are not a
 * multiple of three, so each cycle starts one step further, and the whole pattern
 * repeats after three cycles (147 days).
 *
 * Example: the rest of 10–13/8/2024 was followed by afternoons; the same happens again
 * every 147 days, e.g. after the rest starting on 5/6/2027.
 */
object RotationSchedule {

    /** Days in one cycle. */
    const val CYCLE_DAYS = 49

    /** Work blocks in one cycle. */
    const val BLOCKS_PER_CYCLE = 5

    /** Days until the whole pattern repeats (3 cycles). */
    const val PERIOD_DAYS = CYCLE_DAYS * 3

    /** The order in which work blocks take the shift types. */
    val TYPE_ORDER: List<ShiftType> = listOf(ShiftType.AFTERNOON, ShiftType.MORNING, ShiftType.NIGHT)

    /**
     * Each day of a cycle: null for a rest day, otherwise the work block (0..4).
     * Built from the run lengths above, so it is easy to compare with the rule.
     */
    private val PATTERN: List<Int?> = buildList {
        fun rest(days: Int) = repeat(days) { add(null) }
        fun work(block: Int, days: Int) = repeat(days) { add(block) }
        rest(4); work(0, 6)
        rest(2); work(1, 6)
        rest(2); work(2, 6)
        rest(2); work(3, 2); rest(1); work(3, 5)
        rest(1); work(4, 4); rest(2); work(4, 6)
    }.also { check(it.size == CYCLE_DAYS) { "The cycle must be $CYCLE_DAYS days, not ${it.size}" } }

    /**
     * What the rotation says for a day: a working shift type or [ShiftType.DAY_OFF].
     *
     * @param anchor the operator's place in the rotation.
     * @param date any day, before or after the anchor.
     */
    fun typeOn(anchor: RotationAnchor, date: LocalDate): ShiftType {
        val days = ChronoUnit.DAYS.between(anchor.cycleStart, date)
        val cycle = Math.floorDiv(days, CYCLE_DAYS.toLong())
        val block = PATTERN[Math.floorMod(days, CYCLE_DAYS.toLong()).toInt()] ?: return ShiftType.DAY_OFF
        // Blocks keep counting across cycles; the type steps once per block
        val step = cycle * BLOCKS_PER_CYCLE + block + TYPE_ORDER.indexOf(anchor.firstType)
        return TYPE_ORDER[Math.floorMod(step, TYPE_ORDER.size.toLong()).toInt()]
    }

    /**
     * The predicted type of a day, or null: only the rotating schedule predicts, only
     * once the operator's place is set, and only from [today] on (past days without a
     * shift are gaps to fill, not predictions).
     *
     * @param schedule the operator's work schedule.
     * @param anchor the operator's place in the rotation, or null when not set.
     * @param date the day.
     * @param today the current date.
     */
    fun forecastOn(schedule: WorkSchedule, anchor: RotationAnchor?, date: LocalDate, today: LocalDate): ShiftType? {
        if (anchor == null || !schedule.predicts || date < today) return null
        return typeOn(anchor, date)
    }

    /**
     * The same place in the rotation, expressed by the last four-day rest that started
     * on or before [date], e.g. to show "your last four-day rest began on …".
     *
     * @param anchor the operator's place in the rotation.
     * @param date the day to look back from (usually today).
     */
    fun latestAnchor(anchor: RotationAnchor, date: LocalDate): RotationAnchor {
        val days = ChronoUnit.DAYS.between(anchor.cycleStart, date)
        val start = anchor.cycleStart.plusDays(Math.floorDiv(days, CYCLE_DAYS.toLong()) * CYCLE_DAYS)
        // The type after a rest is what the rotation says for the day after the 4 rest days
        return RotationAnchor(start, typeOn(anchor, start.plusDays(4)))
    }

    /**
     * Finds the operator's place in the rotation from the shifts they recorded.
     *
     * Every one of the 147 possible places is compared with the shifts: a working shift
     * must have exactly the predicted type, a day off must fall on a rest day; leave,
     * sickness, holidays, training and strikes say nothing and are skipped. The place is
     * returned only when exactly one fits without a single difference, so a guess is
     * never made from too few days (at most three weeks of recorded days are needed,
     * often fewer).
     *
     * @param shifts recorded shifts, best the recent ones (a schedule may have changed
     * long ago).
     * @return the only place that fits, as the last cycle start on or before the newest
     * shift; null when none or several fit.
     */
    fun detect(shifts: Collection<Shift>): RotationAnchor? {
        val telling = shifts.filter { it.type.isWorkShift || it.type == ShiftType.DAY_OFF }
        if (telling.none { it.type.isWorkShift }) return null
        val newest = telling.maxOf { it.date }
        val matches = (0 until CYCLE_DAYS).flatMap { offset ->
            TYPE_ORDER.map { first -> RotationAnchor(newest.minusDays(offset.toLong()), first) }
        }.filter { candidate -> telling.all { typeOn(candidate, it.date) == it.type } }
        return matches.singleOrNull()?.let { latestAnchor(it, newest) }
    }
}
