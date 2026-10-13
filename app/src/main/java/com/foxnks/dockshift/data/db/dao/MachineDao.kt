package com.foxnks.dockshift.data.db.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Update
import com.foxnks.dockshift.data.db.entity.MachineEntity
import com.foxnks.dockshift.data.db.entity.MachineUsage
import kotlinx.coroutines.flow.Flow

/**
 * Database access for the machine list. A machine used by any shift is never
 * deleted, only deactivated; the foreign key of `shift_machines` enforces it.
 */
@Dao
abstract class MachineDao {

    /** Every machine, active or not. Display order is applied by the repository. */
    @Query("SELECT * FROM machines")
    abstract fun observeAll(): Flow<List<MachineEntity>>

    /** Only active machines. */
    @Query("SELECT * FROM machines WHERE active = 1")
    abstract fun observeActive(): Flow<List<MachineEntity>>

    /**
     * Finds a machine by category and number, active or not.
     *
     * @return the row, or null if this machine was never added.
     */
    @Query("SELECT * FROM machines WHERE category = :category AND number = :number LIMIT 1")
    abstract suspend fun find(category: String, number: String): MachineEntity?

    /**
     * Finds a machine by its id.
     *
     * @return the row, or null if no machine has this id.
     */
    @Query("SELECT * FROM machines WHERE id = :id")
    abstract suspend fun findById(id: Long): MachineEntity?

    /** @return the id of the new row. */
    @Insert
    abstract suspend fun insert(machine: MachineEntity): Long

    /** Writes every column of an existing row (number, location, active). */
    @Update
    abstract suspend fun update(machine: MachineEntity)

    /** Sets the active flag (soft delete / restore). */
    @Query("UPDATE machines SET active = :active WHERE id = :id")
    abstract suspend fun setActive(id: Long, active: Boolean)

    /**
     * Deletes the machine only if no shift uses it. Check and delete are one SQL
     * statement, so a shift saved at the same moment can never be left pointing
     * at a deleted machine.
     *
     * @return the number of deleted rows: 1 if deleted, 0 if in use (or unknown id).
     */
    @Query(
        """
        DELETE FROM machines
        WHERE id = :id AND NOT EXISTS (SELECT 1 FROM shift_machines WHERE machineId = :id)
        """,
    )
    abstract suspend fun deleteIfUnused(id: Long): Int

    /**
     * How many distinct shifts used each machine; machines never used have no row.
     * Updates whenever shifts or their machines change.
     */
    @Query("SELECT machineId, COUNT(DISTINCT shiftDate) AS shifts FROM shift_machines GROUP BY machineId")
    abstract fun observeUsage(): Flow<List<MachineUsage>>

    /**
     * Returns the active machine with this category and number: reactivates it if
     * it was soft-deleted, or inserts it if it never existed. One transaction, so
     * two quick taps cannot create a duplicate.
     *
     * @param category category name, e.g. "RTG".
     * @param number fleet number, already trimmed.
     * @param location location given to a newly inserted machine; an existing
     * machine keeps its own.
     */
    @Transaction
    open suspend fun addOrReactivate(category: String, number: String, location: String?): MachineEntity {
        val existing = find(category, number)
        if (existing != null) {
            if (!existing.active) setActive(existing.id, true)
            return existing.copy(active = true)
        }
        val created = MachineEntity(category = category, number = number, active = true, location = location)
        return created.copy(id = insert(created))
    }
}
