package com.foxnks.dockshift.data.db.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Transaction
import com.foxnks.dockshift.data.db.entity.MachineFaultEntity
import kotlinx.coroutines.flow.Flow

/** Database access for machine faults. */
@Dao
abstract class FaultDao {

    /** All faults of one machine. */
    @Query("SELECT * FROM machine_faults WHERE machineId = :machineId ORDER BY reportedOn, id")
    abstract fun observeForMachine(machineId: Long): Flow<List<MachineFaultEntity>>

    /** Every open fault. */
    @Query("SELECT * FROM machine_faults WHERE fixedOn IS NULL ORDER BY reportedOn, id")
    abstract fun observeOpen(): Flow<List<MachineFaultEntity>>

    /** How many open faults of this type the machine has (0 or 1). */
    @Query("SELECT COUNT(*) FROM machine_faults WHERE machineId = :machineId AND type = :type AND fixedOn IS NULL")
    abstract suspend fun countOpen(machineId: Long, type: String): Int

    /** Stores a new fault. */
    @Insert
    abstract suspend fun insert(fault: MachineFaultEntity): Long

    /**
     * Opens a fault unless the same one is already open on that machine, in one
     * transaction, so two quick taps never open it twice.
     */
    @Transaction
    open suspend fun reportOnce(fault: MachineFaultEntity) {
        if (countOpen(fault.machineId, fault.type) == 0) insert(fault)
    }

    /** Checks an open fault as fixed; a fixed one keeps its first repair date. */
    @Query("UPDATE machine_faults SET fixedOn = :date WHERE id = :id AND fixedOn IS NULL")
    abstract suspend fun fix(id: Long, date: String)

    /** Removes a fault entered by mistake. */
    @Query("DELETE FROM machine_faults WHERE id = :id")
    abstract suspend fun delete(id: Long)
}
