package com.foxnks.dockshift.domain.leave

import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/**
 * One annual leave, planned as a range of days (leave at the port is known years
 * ahead, so it is entered in advance).
 *
 * Every day of the range shows as leave and cannot be worked; a stored shift on one
 * of its days is replaced when the leave is saved (the user is asked first).
 *
 * @property id database identity; 0 means "not saved yet".
 * @property start first day of the leave.
 * @property end last day of the leave (the same as [start] for a single day).
 * @property note the user's name for it, e.g. "Καλοκαιρινή", or null.
 */
data class Leave(
    val id: Long,
    val start: LocalDate,
    val end: LocalDate,
    val note: String? = null,
) {
    init {
        require(end >= start) { "A leave cannot end ($end) before it starts ($start)" }
    }

    /** Every day of the leave, in order. */
    val days: List<LocalDate>
        get() = generateSequence(start) { it.plusDays(1) }.takeWhile { it <= end }.toList()

    /**
     * Whether [date] is one of the leave's days.
     *
     * @param date any day.
     */
    operator fun contains(date: LocalDate): Boolean = date in start..end

    /**
     * Whether the two leaves share at least one day.
     *
     * @param other another leave.
     */
    fun overlaps(other: Leave): Boolean = start <= other.end && other.start <= end
}

/**
 * Stored leaves.
 *
 * The domain only defines this contract; the :app module stores them in the database.
 */
interface LeaveRepository {

    /** Every leave, sorted by start, updated on every change. */
    fun observeAll(): Flow<List<Leave>>

    /**
     * Leaves with at least one day between two dates (both included), sorted by start.
     *
     * @param from first day of the range.
     * @param to last day of the range.
     */
    fun observeBetween(from: LocalDate, to: LocalDate): Flow<List<Leave>>

    /**
     * One leave, or null when it does not exist (any more).
     *
     * @param id the leave's identity.
     */
    fun observe(id: Long): Flow<Leave?>

    /**
     * Adds a leave (id 0) or replaces the one with the same id.
     *
     * @param leave the leave to store.
     * @return the stored leave, with its identity.
     */
    suspend fun save(leave: Leave): Leave

    /**
     * Deletes a leave; its days go back to whatever the rotation predicts.
     *
     * @param id the leave's identity.
     */
    suspend fun delete(id: Long)
}
