package com.foxnks.dockshift.testing

import com.foxnks.dockshift.domain.faults.FaultRepository
import com.foxnks.dockshift.domain.faults.FaultType
import com.foxnks.dockshift.domain.faults.MachineFault
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * [FaultRepository] in memory, with the same rules as the database: one open fault
 * per type and machine, and a fixed fault keeps its first repair date.
 *
 * @param initial the faults it starts with.
 */
class InMemoryFaultRepository(initial: List<MachineFault> = emptyList()) : FaultRepository {

    private val faults = MutableStateFlow(initial)
    private var nextId = (initial.maxOfOrNull { it.id } ?: 0) + 1

    /** Everything stored, for assertions. */
    val stored: List<MachineFault> get() = faults.value

    override fun observeForMachine(machineId: Long): Flow<List<MachineFault>> =
        faults.map { list -> list.filter { it.machineId == machineId } }

    override fun observeOpen(): Flow<List<MachineFault>> = faults.map { list -> list.filter { it.isOpen } }

    override suspend fun report(machineId: Long, type: FaultType, date: LocalDate) {
        faults.update { list ->
            if (list.any { it.machineId == machineId && it.type == type && it.isOpen }) list
            else list + MachineFault(nextId++, machineId, type, date)
        }
    }

    override suspend fun fix(id: Long, date: LocalDate) {
        faults.update { list -> list.map { if (it.id == id && it.isOpen) it.copy(fixedOn = date) else it } }
    }

    override suspend fun delete(id: Long) {
        faults.update { list -> list.filterNot { it.id == id } }
    }
}
