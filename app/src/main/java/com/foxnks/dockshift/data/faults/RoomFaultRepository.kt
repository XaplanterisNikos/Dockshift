package com.foxnks.dockshift.data.faults

import com.foxnks.dockshift.data.db.dao.FaultDao
import com.foxnks.dockshift.data.db.entity.MachineFaultEntity
import com.foxnks.dockshift.domain.faults.FaultRepository
import com.foxnks.dockshift.domain.faults.FaultType
import com.foxnks.dockshift.domain.faults.MachineFault
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * [FaultRepository] backed by the Room database.
 *
 * @param dao fault data access, provided by Hilt.
 */
class RoomFaultRepository @Inject constructor(
    private val dao: FaultDao,
) : FaultRepository {

    override fun observeForMachine(machineId: Long): Flow<List<MachineFault>> =
        dao.observeForMachine(machineId).map { rows -> rows.mapNotNull { it.toDomain() } }

    override fun observeOpen(): Flow<List<MachineFault>> =
        dao.observeOpen().map { rows -> rows.mapNotNull { it.toDomain() } }

    override suspend fun report(machineId: Long, type: FaultType, date: LocalDate) {
        dao.reportOnce(MachineFaultEntity(id = 0, machineId = machineId, type = type.name, reportedOn = date.toString(), fixedOn = null))
    }

    override suspend fun fix(id: Long, date: LocalDate) {
        dao.fix(id, date.toString())
    }

    override suspend fun delete(id: Long) {
        dao.delete(id)
    }
}

/**
 * Row → domain; null for a type this version does not know (written by a newer one),
 * which is skipped rather than crashing the screen.
 */
internal fun MachineFaultEntity.toDomain(): MachineFault? {
    val faultType = FaultType.entries.firstOrNull { it.name == type } ?: return null
    return MachineFault(
        id = id,
        machineId = machineId,
        type = faultType,
        reportedOn = LocalDate.parse(reportedOn),
        fixedOn = fixedOn?.let { date -> LocalDate.parse(date) },
    )
}

/** Domain → row. */
internal fun MachineFault.toEntity(): MachineFaultEntity =
    MachineFaultEntity(id = id, machineId = machineId, type = type.name, reportedOn = reportedOn.toString(), fixedOn = fixedOn?.toString())
