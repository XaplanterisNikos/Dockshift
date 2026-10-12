package com.foxnks.dockshift.data.db.entity

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index

/**
 * Row of the `shift_machines` table: one machine driven during one shift.
 *
 * @property shiftDate the shift's date (its key in `shifts`).
 * @property position order within the shift, starting at 0; together with
 * [shiftDate] it is the primary key, so the same machine may appear twice.
 * @property machineId the machine (its key in `machines`).
 * @property duringOvertime true if driven in the overtime hours.
 */
@Entity(
    tableName = "shift_machines",
    primaryKeys = ["shiftDate", "position"],
    foreignKeys = [
        // Deleting a shift also deletes its machine list
        ForeignKey(
            entity = ShiftEntity::class,
            parentColumns = ["date"],
            childColumns = ["shiftDate"],
            onDelete = ForeignKey.CASCADE,
        ),
        // A machine used by any shift can never be physically deleted (only deactivated)
        ForeignKey(
            entity = MachineEntity::class,
            parentColumns = ["id"],
            childColumns = ["machineId"],
        ),
    ],
    // Speeds up "which shifts used this machine" (machine history, statistics)
    indices = [Index("machineId")],
)
data class ShiftMachineEntity(
    val shiftDate: String,
    val position: Int,
    val machineId: Long,
    val duringOvertime: Boolean,
)
