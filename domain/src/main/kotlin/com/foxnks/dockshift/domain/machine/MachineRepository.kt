package com.foxnks.dockshift.domain.machine

import kotlinx.coroutines.flow.Flow

/**
 * The user-managed list of terminal machines.
 *
 * Lists are sorted by category (in [MachineCategory] order) and then by fleet
 * number, numerically when the number is numeric.
 */
interface MachineRepository {

    /** Every machine, including deactivated ones (for the settings screen). */
    fun observeAll(): Flow<List<Machine>>

    /** Only active machines (for picking the machines of a shift). */
    fun observeActive(): Flow<List<Machine>>

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
     * Soft-deletes a machine: it disappears from [observeActive] but stays in
     * the history of the shifts that used it.
     *
     * @param id the machine to deactivate.
     */
    suspend fun deactivate(id: Long)
}
