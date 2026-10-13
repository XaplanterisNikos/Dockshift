package com.foxnks.dockshift.domain.machine

import kotlinx.coroutines.flow.Flow

/**
 * The user-managed list of terminal machines.
 *
 * Lists are sorted by category (in [MachineCategory] order) and then by fleet
 * number, numerically when the number is numeric.
 */
interface MachineRepository {

    /** Every machine, including deactivated ones (for the machines screen). */
    fun observeAll(): Flow<List<Machine>>

    /** Only active machines (for picking the machines of a shift). */
    fun observeActive(): Flow<List<Machine>>

    /**
     * How many shifts each machine was driven in, by machine id. Machines that were
     * never used are missing from the map (count 0).
     */
    fun observeUsage(): Flow<Map<Long, Int>>

    /**
     * Adds a machine, or reactivates it if the same category and number already
     * exist; the same physical machine therefore always keeps one identity.
     *
     * @param category the machine's category.
     * @param number its fleet number; surrounding whitespace is ignored.
     * @return the active, saved machine.
     */
    suspend fun addOrReactivate(category: MachineCategory, number: String): Machine

    /**
     * Stores a machine: inserts it when its id is 0, otherwise updates the stored
     * one. A changed number or location shows in every past shift of the machine,
     * because shifts refer to the machine, not to its number.
     *
     * Call it with the result of [MachineForm.plan], which has already checked that
     * the category and number are unique.
     *
     * @param machine the machine to store.
     * @return the stored machine, with its id.
     */
    suspend fun save(machine: Machine): Machine

    /**
     * Soft delete or restore: an inactive machine disappears from [observeActive]
     * but stays in the history of the shifts that used it.
     *
     * @param id the machine to change.
     * @param active the new state.
     */
    suspend fun setActive(id: Long, active: Boolean)

    /**
     * Deletes a machine for good, but only if no shift ever used it (e.g. one added
     * by mistake). A machine with history can only be deactivated.
     *
     * @param id the machine to delete.
     * @return true if it was deleted, false if a shift uses it.
     */
    suspend fun delete(id: Long): Boolean
}
