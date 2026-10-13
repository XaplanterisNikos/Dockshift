package com.foxnks.dockshift.data.leave

import com.foxnks.dockshift.data.db.dao.LeaveDao
import com.foxnks.dockshift.data.db.entity.LeaveEntity
import com.foxnks.dockshift.domain.leave.Leave
import com.foxnks.dockshift.domain.leave.LeaveRepository
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * [LeaveRepository] backed by the Room database.
 *
 * @param dao leave data access, provided by Hilt.
 */
class RoomLeaveRepository @Inject constructor(
    private val dao: LeaveDao,
) : LeaveRepository {

    override fun observeAll(): Flow<List<Leave>> = dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override fun observeBetween(from: LocalDate, to: LocalDate): Flow<List<Leave>> =
        dao.observeBetween(from.toString(), to.toString()).map { rows -> rows.map { it.toDomain() } }

    override fun observe(id: Long): Flow<Leave?> = dao.observe(id).map { it?.toDomain() }

    override suspend fun save(leave: Leave): Leave {
        val id = dao.upsert(leave.toEntity())
        return leave.copy(id = id)
    }

    override suspend fun delete(id: Long) {
        dao.delete(id)
    }
}

/** Row → domain; the dates were written by [toEntity], so they always parse. */
internal fun LeaveEntity.toDomain(): Leave =
    Leave(id = id, start = LocalDate.parse(startDate), end = LocalDate.parse(endDate), note = note)

/** Domain → row; a blank note is stored as "no note". */
internal fun Leave.toEntity(): LeaveEntity =
    LeaveEntity(id = id, startDate = start.toString(), endDate = end.toString(), note = note?.trim()?.ifEmpty { null })
