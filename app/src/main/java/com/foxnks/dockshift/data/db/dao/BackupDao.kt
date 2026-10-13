package com.foxnks.dockshift.data.db.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Transaction
import com.foxnks.dockshift.data.db.entity.LeaveEntity
import com.foxnks.dockshift.data.db.entity.MachineEntity
import com.foxnks.dockshift.data.db.entity.ShiftEntity
import com.foxnks.dockshift.data.db.entity.ShiftMachineEntity
import com.foxnks.dockshift.data.db.entity.ShiftWithMachines

/**
 * Database access for backups: read every table, or replace every table at once.
 *
 * Has its own copies of a few queries because a Room `@Transaction` function can
 * only call functions of its own DAO, and a restore must replace everything in one
 * single transaction.
 */
@Dao
abstract class BackupDao {

    /** Every machine, active or not. */
    @Query("SELECT * FROM machines ORDER BY id")
    abstract suspend fun allMachines(): List<MachineEntity>

    /** Every shift with its machines, by date. */
    @Transaction
    @Query("SELECT * FROM shifts ORDER BY date")
    abstract suspend fun allShifts(): List<ShiftWithMachines>

    /** Every leave, by first day. */
    @Query("SELECT * FROM leaves ORDER BY startDate")
    abstract suspend fun allLeaves(): List<LeaveEntity>

    /**
     * Replaces every shift, machine and leave, all or nothing: if any insert fails,
     * the transaction is rolled back and the old data stays.
     *
     * @param machines machine rows, with the identities the assignments refer to.
     * @param shifts shift rows.
     * @param assignments the machine rows of the shifts.
     * @param leaves leave rows (id 0: new identities).
     */
    @Transaction
    open suspend fun replaceAll(
        machines: List<MachineEntity>,
        shifts: List<ShiftEntity>,
        assignments: List<ShiftMachineEntity>,
        leaves: List<LeaveEntity>,
    ) {
        // Children first, so no foreign key is ever broken on the way
        deleteAllAssignments()
        deleteAllShifts()
        deleteAllMachines()
        deleteAllLeaves()
        insertMachines(machines)
        insertShifts(shifts)
        insertAssignments(assignments)
        insertLeaves(leaves)
    }

    // Building blocks of replaceAll; not meant to be called on their own.

    @Query("DELETE FROM shift_machines")
    abstract suspend fun deleteAllAssignments()

    @Query("DELETE FROM shifts")
    abstract suspend fun deleteAllShifts()

    @Query("DELETE FROM machines")
    abstract suspend fun deleteAllMachines()

    @Query("DELETE FROM leaves")
    abstract suspend fun deleteAllLeaves()

    @Insert
    abstract suspend fun insertMachines(machines: List<MachineEntity>)

    @Insert
    abstract suspend fun insertShifts(shifts: List<ShiftEntity>)

    @Insert
    abstract suspend fun insertAssignments(assignments: List<ShiftMachineEntity>)

    @Insert
    abstract suspend fun insertLeaves(leaves: List<LeaveEntity>)
}
