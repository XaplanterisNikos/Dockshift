package com.foxnks.dockshift.data.db.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Transaction
import com.foxnks.dockshift.data.db.entity.MachineEntity
import kotlinx.coroutines.flow.Flow

/** Database access for the machine list. Machines are never deleted, only deactivated. */
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

    /** Sets the active flag (soft delete / restore). */
    @Query("UPDATE machines SET active = :active WHERE id = :id")
    abstract suspend fun setActive(id: Long, active: Boolean)

    /**
     * Returns the active machine with this category and number: reactivates it if
     * it was soft-deleted, or inserts it if it never existed. One transaction, so
     * two quick taps cannot create a duplicate.
     *
     * @param category category name, e.g. "RTG".
     * @param number fleet number, already trimmed.
     */
    @Transaction
    open suspend fun addOrReactivate(category: String, number: String): MachineEntity {
        val existing = find(category, number)
        if (existing != null) {
            if (!existing.active) setActive(existing.id, true)
            return existing.copy(active = true)
        }
        val created = MachineEntity(category = category, number = number, active = true)
        return created.copy(id = insert(created))
    }
}
