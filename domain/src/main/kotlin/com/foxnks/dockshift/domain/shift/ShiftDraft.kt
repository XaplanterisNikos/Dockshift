package com.foxnks.dockshift.domain.shift

import com.foxnks.dockshift.domain.machine.Machine
import java.time.LocalDate

/**
 * A shift while it is being edited on screen.
 *
 * Every change goes through one of the functions below, which keep the draft
 * valid at all times (so [toShift] can never fail): switching to a day off drops
 * the machines and the overtime, turning overtime off clears the overtime marks
 * of the machines, and so on. The edit screen therefore cannot reach an invalid
 * combination, and the rules are tested here without any UI.
 *
 * @property date the day being edited.
 * @property type the chosen shift type.
 * @property overtime whether the 4 overtime hours were worked.
 * @property machines machines driven, in order.
 * @property comments free text; blank is saved as "no comment".
 */
data class ShiftDraft(
    val date: LocalDate,
    val type: ShiftType = ShiftType.MORNING,
    val overtime: Boolean = false,
    val machines: List<MachineAssignment> = emptyList(),
    val comments: String = "",
) {

    /** Machines and overtime only make sense on a working shift. */
    val allowsMachines: Boolean
        get() = type.isWorkShift

    /** Picks another type; a non-working day drops machines and overtime. */
    fun withType(newType: ShiftType): ShiftDraft =
        if (newType.isWorkShift) {
            copy(type = newType)
        } else {
            copy(type = newType, overtime = false, machines = emptyList())
        }

    /** Turns overtime on/off; off also clears every machine's overtime mark. */
    fun withOvertime(enabled: Boolean): ShiftDraft = when {
        !allowsMachines -> this
        enabled -> copy(overtime = true)
        else -> copy(overtime = false, machines = machines.map { it.copy(duringOvertime = false) })
    }

    /**
     * Adds a machine at the end. With overtime on, a machine added after the
     * regular ones is most likely the overtime machine, but the user decides:
     * it starts as regular and can be marked with [toggleOvertimeAt].
     */
    fun addMachine(machine: Machine): ShiftDraft =
        if (allowsMachines) copy(machines = machines + MachineAssignment(machine)) else this

    /** Removes the machine at [index]. */
    fun removeMachineAt(index: Int): ShiftDraft =
        if (index in machines.indices) copy(machines = machines.filterIndexed { i, _ -> i != index }) else this

    /** Moves the machine at [index] one place earlier (no-op for the first). */
    fun moveMachineUp(index: Int): ShiftDraft = swap(index, index - 1)

    /** Moves the machine at [index] one place later (no-op for the last). */
    fun moveMachineDown(index: Int): ShiftDraft = swap(index, index + 1)

    /** Marks the machine at [index] as driven in the overtime hours, or back to regular. */
    fun toggleOvertimeAt(index: Int): ShiftDraft {
        if (!overtime || index !in machines.indices) return this
        return copy(
            machines = machines.mapIndexed { i, a -> if (i == index) a.copy(duringOvertime = !a.duringOvertime) else a },
        )
    }

    /** Moves the edit to another day, keeping everything else. */
    fun withDate(newDate: LocalDate): ShiftDraft = copy(date = newDate)

    /** Replaces the comment text. */
    fun withComments(text: String): ShiftDraft = copy(comments = text)

    /** The shift to save. Never fails: the functions above keep the draft valid. */
    fun toShift(): Shift = Shift(
        date = date,
        type = type,
        overtime = overtime,
        machines = machines,
        comments = comments.trim().ifEmpty { null },
    )

    /** Swaps two machines if both positions exist. */
    private fun swap(a: Int, b: Int): ShiftDraft {
        if (a !in machines.indices || b !in machines.indices) return this
        val list = machines.toMutableList()
        list[a] = machines[b]
        list[b] = machines[a]
        return copy(machines = list)
    }

    companion object {
        /** A draft showing an existing shift. */
        fun from(shift: Shift): ShiftDraft = ShiftDraft(
            date = shift.date,
            type = shift.type,
            overtime = shift.overtime,
            machines = shift.machines,
            comments = shift.comments.orEmpty(),
        )
    }
}
