package com.foxnks.dockshift.data.machine

import com.foxnks.dockshift.data.db.dao.MachineDao
import com.foxnks.dockshift.data.db.machineDisplayOrder
import com.foxnks.dockshift.data.db.toDomain
import com.foxnks.dockshift.domain.machine.Machine
import com.foxnks.dockshift.domain.machine.MachineCategory
import com.foxnks.dockshift.domain.machine.MachineRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * [MachineRepository] backed by the Room database.
 *
 * @param dao machine data access, provided by Hilt.
 */
class RoomMachineRepository @Inject constructor(
    private val dao: MachineDao,
) : MachineRepository {

    override fun observeAll(): Flow<List<Machine>> =
        dao.observeAll().map { rows -> rows.map { it.toDomain() }.sortedWith(machineDisplayOrder) }

    override fun observeActive(): Flow<List<Machine>> =
        dao.observeActive().map { rows -> rows.map { it.toDomain() }.sortedWith(machineDisplayOrder) }

    override suspend fun addOrReactivate(category: MachineCategory, number: String): Machine {
        val trimmed = number.trim()
        // Fail before touching the database; Machine would reject it anyway
        require(trimmed.isNotEmpty()) { "Machine number must not be blank" }
        return dao.addOrReactivate(category.name, trimmed).toDomain()
    }

    override suspend fun deactivate(id: Long) {
        dao.setActive(id, false)
    }
}
