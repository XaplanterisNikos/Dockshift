package com.foxnks.dockshift.data.db.entity

import androidx.room3.Entity
import androidx.room3.PrimaryKey

/**
 * Row of the `shifts` table: one per calendar day.
 *
 * @property date ISO date ("2026-10-13"). Being the primary key, it makes a second
 * entry for the same day impossible; ISO text also sorts in calendar order,
 * so date ranges are simple BETWEEN queries.
 * @property type stable storage code of the shift type (see `ShiftTypeCodes`).
 * @property overtime true when the day had the 4 overtime hours.
 * @property comments free notes, or null.
 */
@Entity(tableName = "shifts")
data class ShiftEntity(
    @PrimaryKey val date: String,
    val type: String,
    val overtime: Boolean,
    val comments: String?,
)
