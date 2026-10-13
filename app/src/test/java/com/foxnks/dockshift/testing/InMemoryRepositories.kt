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
 * [MachineRepository] in memory, keeping the database's rules: sorted lists, and
 * a machine with usage can never be deleted.
 *
 * @param initial machines stored from the start.
 * @param usage shifts per machine id, as the database would count them.
 */
class InMemoryMachineRepository(
    initial: List<Machine> = emptyList(),
    usage: Map<Long, Int> = emptyMap(),
) : MachineRepository {

    private val machines = MutableStateFlow(initial)
    private val usageById = MutableStateFlow(usage)

    /** Everything stored, sorted by id, for assertions. */
    val stored: List<Machine> get() = machines.value.sortedBy { it.id }

    /** The display order of the real repository: category, then number. */
    private val order = compareBy<Machine> { it.category.ordinal }.thenBy { it.number.toIntOrNull() ?: Int.MAX_VALUE }

    override fun observeAll(): Flow<List<Machine>> = machines.map { it.sortedWith(order) }

    override fun observeActive(): Flow<List<Machine>> = machines.map { list -> list.filter { it.active }.sortedWith(order) }

    override fun observeUsage(): Flow<Map<Long, Int>> = usageById

    override suspend fun addOrReactivate(category: MachineCategory, number: String): Machine {
        val existing = machines.value.firstOrNull { it.category == category && it.number == number.trim() }
        return save(existing?.copy(active = true) ?: Machine(id = 0, category = category, number = number.trim()))
    }

    override suspend fun save(machine: Machine): Machine {
        val saved = if (machine.id == 0L) machine.copy(id = (machines.value.maxOfOrNull { it.id } ?: 0L) + 1) else machine
        machines.update { list -> list.filter { it.id != saved.id } + saved }
        return saved
    }

    override suspend fun setActive(id: Long, active: Boolean) =
        machines.update { list -> list.map { if (it.id == id) it.copy(active = active) else it } }

    override suspend fun delete(id: Long): Boolean {
        if ((usageById.value[id] ?: 0) > 0) return false
        machines.update { list -> list.filter { it.id != id } }
        return true
    }
}
