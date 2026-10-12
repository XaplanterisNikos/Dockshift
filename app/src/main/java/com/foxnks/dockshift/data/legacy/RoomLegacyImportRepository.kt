package com.foxnks.dockshift.data.legacy

import com.foxnks.dockshift.data.db.ShiftTypeCodes
import com.foxnks.dockshift.data.db.dao.ImportDao
import com.foxnks.dockshift.data.db.dao.ImportItem
import com.foxnks.dockshift.data.db.entity.ShiftEntity
import com.foxnks.dockshift.domain.legacy.LegacyImportRepository
import com.foxnks.dockshift.domain.legacy.LegacyMachine
import com.foxnks.dockshift.domain.legacy.LegacyShift
import com.foxnks.dockshift.domain.machine.MachineCategory
import java.time.LocalDate
import javax.inject.Inject

/**
 * [LegacyImportRepository] backed by the Room database.
 *
 * @param dao bulk-import data access, provided by Hilt.
 */
class RoomLegacyImportRepository @Inject constructor(
    private val dao: ImportDao,
) : LegacyImportRepository {

    override suspend fun existingDates(): Set<LocalDate> =
        dao.allShiftDates().map(LocalDate::parse).toSet()

    override suspend fun knownMachines(): Set<LegacyMachine> =
        dao.allMachines()
            .map { LegacyMachine(MachineCategory.valueOf(it.category), it.number) }
            .toSet()

    override suspend fun save(shifts: List<LegacyShift>): Int =
        dao.importShifts(
            shifts.map { shift ->
                ImportItem(
                    shift = ShiftEntity(
                        date = shift.date.toString(),
                        type = ShiftTypeCodes.toCode(shift.type),
                        // v1 had no overtime
                        overtime = false,
                        comments = shift.comments,
                    ),
                    machineCategory = shift.machine?.category?.name,
                    machineNumber = shift.machine?.number,
                )
            },
        )
}
