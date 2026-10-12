package com.foxnks.dockshift.data.db.entity

import androidx.room3.Embedded
import androidx.room3.Relation

/**
 * A shift row together with its machine rows, read by Room in a single transaction.
 *
 * Reading both at once matters: two separate observations could briefly disagree
 * while a shift is being saved (e.g. a day off still showing its old machines).
 *
 * @property shift the `shifts` row.
 * @property assignments its `shift_machines` rows with their machines, in no
 * particular order (sort by [ShiftMachineEntity.position]).
 */
data class ShiftWithMachines(
    @Embedded val shift: ShiftEntity,
    @Relation(
        entity = ShiftMachineEntity::class,
        parentColumns = ["date"],
        entityColumns = ["shiftDate"],
    )
    val assignments: List<AssignmentWithMachine>,
)

/**
 * One `shift_machines` row together with the machine it points to.
 *
 * @property assignment the assignment row.
 * @property machine the referenced `machines` row.
 */
data class AssignmentWithMachine(
    @Embedded val assignment: ShiftMachineEntity,
    @Relation(
        parentColumns = ["machineId"],
        entityColumns = ["id"],
    )
    val machine: MachineEntity,
)
