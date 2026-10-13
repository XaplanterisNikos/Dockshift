package com.foxnks.dockshift.testing

import com.foxnks.dockshift.domain.machine.Machine
import com.foxnks.dockshift.domain.machine.MachineCategory
import com.foxnks.dockshift.domain.machine.MachineRepository
import com.foxnks.dockshift.domain.shift.Shift
import com.foxnks.dockshift.domain.shift.ShiftRepository
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * [ShiftRepository] in memory, behaving like the database: one shift per day,
 * sorted results, and every observer updated on every change.
 *
 * @param initial shifts stored from the start.
 */
class InMemoryShiftRepository(initial: List<Shift> = emptyList()) : ShiftRepository {

    private val byDate = MutableStateFlow(initial.associateBy { it.date })

    /** Everything stored, by day, for assertions. */
    val stored: Map<LocalDate, Shift> get() = byDate.value

    override fun observeShifts(from: LocalDate, to: LocalDate): Flow<List<Shift>> =
        byDate.map { all -> all.values.filter { it.date in from..to }.sortedBy { it.date } }

    override fun observeShift(date: LocalDate): Flow<Shift?> = byDate.map { it[date] }

    override fun observeCount(): Flow<Int> = byDate.map { it.size }

    override fun observeYears(): Flow<List<Int>> =
        byDate.map { all -> all.keys.map { it.year }.distinct().sortedDescending() }

    override suspend fun save(shift: Shift) = byDate.update { it + (shift.date to shift) }

    override suspend fun delete(date: LocalDate) = byDate.update { it - date }

    override suspend fun deleteAll() = byDate.update { emptyMap() }
}

/**
 * [MachineRepository] in memory.
 *
 * @param initial machines stored from the start.
 */
class InMemoryMachineRepository(initial: List<Machine> = emptyList()) : MachineRepository {

    private val machines = MutableStateFlow(initial)

    override fun observeAll(): Flow<List<Machine>> = machines

    override fun observeActive(): Flow<List<Machine>> = machines.map { list -> list.filter { it.active } }

    override suspend fun addOrReactivate(category: MachineCategory, number: String): Machine {
        val existing = machines.value.firstOrNull { it.category == category && it.number == number.trim() }
        val result = existing?.copy(active = true)
            ?: Machine(id = (machines.value.maxOfOrNull { it.id } ?: 0) + 1, category = category, number = number.trim())
        machines.update { list -> list.filter { it.id != result.id } + result }
        return result
    }

    override suspend fun deactivate(id: Long) =
        machines.update { list -> list.map { if (it.id == id) it.copy(active = false) else it } }
}
