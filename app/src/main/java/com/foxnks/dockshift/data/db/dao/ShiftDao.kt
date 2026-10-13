package com.foxnks.dockshift.data.db.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction
import com.foxnks.dockshift.data.db.entity.ShiftEntity
import com.foxnks.dockshift.data.db.entity.ShiftMachineEntity
import com.foxnks.dockshift.data.db.entity.ShiftWithMachines
import kotlinx.coroutines.flow.Flow

/**
 * Database access for shifts and their machine lists.
 *
 * Writes that touch both tables are `@Transaction` functions, so a shift and its
 * machines are always saved or deleted together, never half-way.
 * Dates are ISO strings ("2026-10-13"), which compare correctly as text.
 */
@Dao
abstract class ShiftDao {

    /**
     * Shifts with their machines between two dates (inclusive), sorted by date.
     * `@Transaction` makes Room read the shifts and their relations consistently.
     */
    @Transaction
    @Query("SELECT * FROM shifts WHERE date BETWEEN :from AND :to ORDER BY date")
    abstract fun observeBetween(from: String, to: String): Flow<List<ShiftWithMachines>>

    /** Number of stored shifts, updated on every change. */
    @Query("SELECT COUNT(*) FROM shifts")
    abstract fun observeCount(): Flow<Int>

    /** Years that have shifts, newest first; dates are ISO text, so the year is the first 4 characters. */
    @Query("SELECT DISTINCT CAST(substr(date, 1, 4) AS INTEGER) AS year FROM shifts ORDER BY year DESC")
    abstract fun observeYears(): Flow<List<Int>>

    /**
     * Replaces the shift of [shift]'s date and its whole machine list.
     *
     * @param shift the shift row to store.
     * @param assignments its machine rows, already numbered by position.
     */
    @Transaction
    open suspend fun replace(shift: ShiftEntity, assignments: List<ShiftMachineEntity>) {
        // Old machine rows first, so the new list fully replaces them
        deleteAssignments(shift.date)
        insertShift(shift)
        if (assignments.isNotEmpty()) insertAssignments(assignments)
    }

    /**
     * Deletes one day's shift and its machine list.
     * Done explicitly, not only through the foreign-key cascade, so it never
     * depends on database settings.
     */
    @Transaction
    open suspend fun delete(date: String) {
        deleteAssignments(date)
        deleteShift(date)
    }

    /** Deletes every shift and every machine list. The machines themselves stay. */
    @Transaction
    open suspend fun deleteAll() {
        deleteAllAssignments()
        deleteAllShifts()
    }

    // Building blocks of the transactions above; not meant to be called on their own.

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertShift(shift: ShiftEntity)

    @Insert
    abstract suspend fun insertAssignments(assignments: List<ShiftMachineEntity>)

    @Query("DELETE FROM shift_machines WHERE shiftDate = :date")
    abstract suspend fun deleteAssignments(date: String)

    @Query("DELETE FROM shifts WHERE date = :date")
    abstract suspend fun deleteShift(date: String)

    @Query("DELETE FROM shift_machines")
    abstract suspend fun deleteAllAssignments()

    @Query("DELETE FROM shifts")
    abstract suspend fun deleteAllShifts()
}
