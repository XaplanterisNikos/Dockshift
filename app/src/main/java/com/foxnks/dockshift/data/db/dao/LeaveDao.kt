package com.foxnks.dockshift.data.db.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import com.foxnks.dockshift.data.db.entity.LeaveEntity
import kotlinx.coroutines.flow.Flow

/**
 * Database access for planned leaves.
 * Dates are ISO strings, which compare correctly as text.
 */
@Dao
interface LeaveDao {

    /** Every leave, sorted by its first day. */
    @Query("SELECT * FROM leaves ORDER BY startDate")
    fun observeAll(): Flow<List<LeaveEntity>>

    /** Leaves with at least one day between [from] and [to] (both included), sorted by first day. */
    @Query("SELECT * FROM leaves WHERE startDate <= :to AND endDate >= :from ORDER BY startDate")
    fun observeBetween(from: String, to: String): Flow<List<LeaveEntity>>

    /** One leave, or null. */
    @Query("SELECT * FROM leaves WHERE id = :id")
    fun observe(id: Long): Flow<LeaveEntity?>

    /** Inserts a new leave (id 0) or replaces the one with the same id; returns its id. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(leave: LeaveEntity): Long

    /** Deletes one leave. */
    @Query("DELETE FROM leaves WHERE id = :id")
    suspend fun delete(id: Long)
}
