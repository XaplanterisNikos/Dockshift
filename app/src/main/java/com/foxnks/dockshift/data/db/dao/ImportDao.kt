package com.foxnks.dockshift.data.db.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction
import com.foxnks.dockshift.data.db.entity.MachineEntity
import com.foxnks.dockshift.data.db.entity.ShiftEntity
import com.foxnks.dockshift.data.db.entity.ShiftMachineEntity

/**
 * One shift to import, as database values.
 *
 * @property shift the `shifts` row.
 * @property machineCategory category name of the machine driven, or null.
 * @property machineNumber its fleet number, or null.
 */
data class ImportItem(
    val shift: ShiftEntity,
    val machineCategory: String?,
    val machineNumber: String?,
)

/**
 * Database access for bulk imports.
 *
 * It has its own copies of a few machine/shift queries because a Room
 * `@Transaction` function can only call functions of its own DAO, and an import
 * must create machines and shifts in one single transaction.
 */
@Dao
abstract class ImportDao {

    /** ISO dates of every stored shift. */
    @Query("SELECT date FROM shifts")
    abstract suspend fun allShiftDates(): List<String>

    /** Every machine, active or not. */
    @Query("SELECT * FROM machines")
    abstract suspend fun allMachines(): List<MachineEntity>

    /**
     * Imports shifts all-or-nothing: if anything fails, nothing is stored.
     *
     * Days that already have a shift are skipped (merge: the app's data wins).
     * Machines are looked up by category and number, and added when missing; an
     * existing machine is linked as it is, even if it was deactivated.
     *
     * @param items the shifts to import.
     * @return how many shifts were stored.
     */
    @Transaction
    open suspend fun importShifts(items: List<ImportItem>): Int {
        // Each machine is looked up once, however many shifts use it
        val machineIds = HashMap<Pair<String, String>, Long>()
        var stored = 0

        for (item in items) {
            // IGNORE returns -1 when the day already exists
            if (insertShiftIfAbsent(item.shift) == -1L) continue
            stored++

            val category = item.machineCategory ?: continue
            val number = item.machineNumber ?: continue
            val machineId = machineIds.getOrPut(category to number) {
                findMachine(category, number)?.id
                    ?: insertMachine(MachineEntity(category = category, number = number, active = true))
            }
            insertAssignment(
                ShiftMachineEntity(shiftDate = item.shift.date, position = 0, machineId = machineId, duringOvertime = false),
            )
        }
        return stored
    }

    // Building blocks of importShifts; not meant to be called on their own.

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertShiftIfAbsent(shift: ShiftEntity): Long

    @Query("SELECT * FROM machines WHERE category = :category AND number = :number LIMIT 1")
    abstract suspend fun findMachine(category: String, number: String): MachineEntity?

    @Insert
    abstract suspend fun insertMachine(machine: MachineEntity): Long

    @Insert
    abstract suspend fun insertAssignment(assignment: ShiftMachineEntity)
}
