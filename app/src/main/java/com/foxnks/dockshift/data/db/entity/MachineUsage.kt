package com.foxnks.dockshift.data.db.entity

/**
 * Result row of the "how many shifts used each machine" query; not a table.
 *
 * @property machineId the machine.
 * @property shifts the number of distinct shifts (days) it was driven in.
 */
data class MachineUsage(
    val machineId: Long,
    val shifts: Int,
)
