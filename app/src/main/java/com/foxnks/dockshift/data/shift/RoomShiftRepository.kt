package com.foxnks.dockshift.data.shift

import com.foxnks.dockshift.data.db.dao.ShiftDao
import com.foxnks.dockshift.data.db.toAssignmentEntities
import com.foxnks.dockshift.data.db.toDomain
import com.foxnks.dockshift.data.db.toEntity
import com.foxnks.dockshift.domain.shift.Shift
import com.foxnks.dockshift.domain.shift.ShiftRepository
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * [ShiftRepository] backed by the Room database.
 *
 * @param dao shift data access, provided by Hilt.
 */
class RoomShiftRepository @Inject constructor(
    private val dao: ShiftDao,
) : ShiftRepository {

    override fun observeShifts(from: LocalDate, to: LocalDate): Flow<List<Shift>> =
        dao.observeBetween(from.toString(), to.toString())
            .map { rows -> rows.map { it.toDomain() } }

    override fun observeShift(date: LocalDate): Flow<Shift?> =
        // A one-day range: the same query, so the same consistent read
        observeShifts(date, date).map { it.firstOrNull() }

    override fun observeCount(): Flow<Int> = dao.observeCount()

    override fun observeYears(): Flow<List<Int>> = dao.observeYears()

    override suspend fun save(shift: Shift) {
        dao.replace(shift.toEntity(), shift.toAssignmentEntities())
    }

    override suspend fun delete(date: LocalDate) {
        dao.delete(date.toString())
    }

    override suspend fun deleteAll() {
        dao.deleteAll()
    }
}
