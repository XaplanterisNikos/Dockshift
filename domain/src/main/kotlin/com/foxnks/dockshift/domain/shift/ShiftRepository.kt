package com.foxnks.dockshift.domain.shift

import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/**
 * Stored shifts: what the user entered (or imported), never predictions.
 * Rotating-schedule predictions are computed on the fly and are not stored here.
 */
interface ShiftRepository {

    /**
     * Shifts between two dates, both included, sorted by date.
     * Emits again whenever any of them changes.
     *
     * @param from first day of the range.
     * @param to last day of the range.
     */
    fun observeShifts(from: LocalDate, to: LocalDate): Flow<List<Shift>>

    /**
     * The shift of one day, or null when nothing is stored for it.
     *
     * @param date the day.
     */
    fun observeShift(date: LocalDate): Flow<Shift?>

    /**
     * Saves a shift, replacing whatever was stored for the same day
     * (including its list of machines). Machines must already be saved.
     *
     * @param shift the shift to store.
     */
    suspend fun save(shift: Shift)

    /**
     * Deletes the shift of one day, if any.
     *
     * @param date the day.
     */
    suspend fun delete(date: LocalDate)

    /** Deletes every stored shift. Machines are kept. */
    suspend fun deleteAll()
}
