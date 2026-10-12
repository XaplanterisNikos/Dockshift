package com.foxnks.dockshift.domain.shift

import java.time.LocalDate

/**
 * What the operator did on one calendar day. There is at most one per day.
 *
 * The constructor enforces the rules below, so an invalid shift cannot exist
 * anywhere in the app (it fails fast with [IllegalArgumentException]):
 * - only working shifts ([ShiftType.isWorkShift]) can have machines or overtime;
 * - a machine marked as driven during overtime requires the shift to have overtime.
 *
 * @property date the calendar day; it identifies the shift.
 * @property type what kind of day it was.
 * @property overtime true when 4 overtime hours were added to the 8 regular ones.
 * @property machines the machines driven, in the order they were driven.
 * @property comments free notes, or null when there are none.
 */
data class Shift(
    val date: LocalDate,
    val type: ShiftType,
    val overtime: Boolean = false,
    val machines: List<MachineAssignment> = emptyList(),
    val comments: String? = null,
) {
    init {
        if (!type.isWorkShift) {
            require(!overtime) { "$type cannot have overtime" }
            require(machines.isEmpty()) { "$type cannot have machines" }
        }
        require(overtime || machines.none { it.duringOvertime }) {
            "A machine is marked as driven during overtime, but the shift has no overtime"
        }
    }

    /** Hours worked: 8 for a working shift, 12 with overtime, 0 otherwise. */
    val workedHours: Int
        get() = when {
            !type.isWorkShift -> 0
            overtime -> REGULAR_HOURS + OVERTIME_HOURS
            else -> REGULAR_HOURS
        }

    /** Shifts this day counts as in statistics: 1, 1.5 with overtime, 0 if not worked. */
    val shiftUnits: Double
        get() = workedHours.toDouble() / REGULAR_HOURS

    companion object {
        /** Length of a regular shift. */
        const val REGULAR_HOURS = 8

        /** Overtime is always 4 hours (half a shift); longer is not allowed by law. */
        const val OVERTIME_HOURS = 4
    }
}
