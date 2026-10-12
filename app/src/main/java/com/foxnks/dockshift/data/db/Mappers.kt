package com.foxnks.dockshift.data.db

import com.foxnks.dockshift.data.db.entity.MachineEntity
import com.foxnks.dockshift.data.db.entity.ShiftEntity
import com.foxnks.dockshift.data.db.entity.ShiftMachineEntity
import com.foxnks.dockshift.data.db.entity.ShiftWithMachines
import com.foxnks.dockshift.domain.machine.Machine
import com.foxnks.dockshift.domain.machine.MachineCategory
import com.foxnks.dockshift.domain.shift.MachineAssignment
import com.foxnks.dockshift.domain.shift.Shift
import com.foxnks.dockshift.domain.shift.ShiftType
import java.time.LocalDate

/**
 * Stable storage codes of [ShiftType].
 *
 * Written explicitly instead of using `enum.name`, so renaming an enum constant in
 * code can never change what is stored. A code, once released, must never change.
 */
object ShiftTypeCodes {

    /** @return the code stored in the database for [type]. */
    fun toCode(type: ShiftType): String = when (type) {
        ShiftType.MORNING -> "MORNING"
        ShiftType.AFTERNOON -> "AFTERNOON"
        ShiftType.NIGHT -> "NIGHT"
        ShiftType.DAY_OFF -> "DAY_OFF"
        ShiftType.ANNUAL_LEAVE -> "ANNUAL_LEAVE"
        ShiftType.SICK_LEAVE -> "SICK_LEAVE"
        ShiftType.PUBLIC_HOLIDAY -> "PUBLIC_HOLIDAY"
        ShiftType.TRAINING -> "TRAINING"
        ShiftType.STRIKE -> "STRIKE"
        ShiftType.PARENTAL_LEAVE -> "PARENTAL_LEAVE"
    }

    /**
     * @return the type stored under [code].
     * @throws IllegalStateException for an unknown code, which means corrupted data.
     */
    fun fromCode(code: String): ShiftType =
        ShiftType.entries.firstOrNull { toCode(it) == code }
            ?: error("Unknown shift type code in database: '$code'")
}

/** Database row → domain machine. */
fun MachineEntity.toDomain(): Machine = Machine(
    id = id,
    category = MachineCategory.valueOf(category),
    number = number,
    active = active,
)

/** Shift row and its machine rows → domain shift, machines in their saved order. */
fun ShiftWithMachines.toDomain(): Shift = Shift(
    date = LocalDate.parse(shift.date),
    type = ShiftTypeCodes.fromCode(shift.type),
    overtime = shift.overtime,
    machines = assignments
        .sortedBy { it.assignment.position }
        .map { MachineAssignment(machine = it.machine.toDomain(), duringOvertime = it.assignment.duringOvertime) },
    comments = shift.comments,
)

/** Domain shift → its `shifts` row. */
fun Shift.toEntity(): ShiftEntity = ShiftEntity(
    // LocalDate.toString() is ISO-8601: "2026-10-13"
    date = date.toString(),
    type = ShiftTypeCodes.toCode(type),
    overtime = overtime,
    comments = comments,
)

/**
 * Domain shift → its `shift_machines` rows, numbered in order.
 *
 * @throws IllegalArgumentException if a machine has not been saved yet (id 0).
 */
fun Shift.toAssignmentEntities(): List<ShiftMachineEntity> =
    machines.mapIndexed { index, assignment ->
        require(assignment.machine.id > 0) { "Machine ${assignment.machine.label} is not saved" }
        ShiftMachineEntity(
            shiftDate = date.toString(),
            position = index,
            machineId = assignment.machine.id,
            duringOvertime = assignment.duringOvertime,
        )
    }

/**
 * Display order of machines: category order of [MachineCategory], then fleet
 * number, numerically when possible ("9" before "10").
 */
val machineDisplayOrder: Comparator<Machine> =
    compareBy<Machine> { it.category.ordinal }
        .thenBy { it.number.toIntOrNull() ?: Int.MAX_VALUE }
        .thenBy { it.number }
