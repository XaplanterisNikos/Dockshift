package com.foxnks.dockshift.domain.settings

import com.foxnks.dockshift.domain.shift.ShiftType

/**
 * Which shifts the operator works, chosen in Settings ("Ωράριο").
 *
 * Only [ROTATING] follows the rotating schedule and gets predictions. The others limit
 * the shift types offered when adding a shift (days off, leave and the rest are always
 * offered), and the suggested type.
 *
 * @property workTypes the working shift types of this schedule.
 */
enum class WorkSchedule(val workTypes: Set<ShiftType>) {
    /** All three shifts, in the rotating schedule. */
    ROTATING(setOf(ShiftType.MORNING, ShiftType.AFTERNOON, ShiftType.NIGHT)),

    /** Mornings only. */
    MORNING_ONLY(setOf(ShiftType.MORNING)),

    /** Afternoons only. */
    AFTERNOON_ONLY(setOf(ShiftType.AFTERNOON)),

    /** Mornings and afternoons. */
    MORNING_AFTERNOON(setOf(ShiftType.MORNING, ShiftType.AFTERNOON)),

    /** Afternoons and nights. */
    AFTERNOON_NIGHT(setOf(ShiftType.AFTERNOON, ShiftType.NIGHT)),

    /** Nights only. */
    NIGHT_ONLY(setOf(ShiftType.NIGHT)),
    ;

    /** Whether shifts are predicted from the rotating schedule. */
    val predicts: Boolean
        get() = this == ROTATING

    /**
     * Whether [type] can be picked without leaving the schedule: every non-working
     * type, and the working types of this schedule.
     *
     * @param type a shift type.
     */
    fun allows(type: ShiftType): Boolean = !type.isWorkShift || type in workTypes
}
