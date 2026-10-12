package com.foxnks.dockshift.data.db.entity

import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey

/**
 * Row of the `machines` table.
 *
 * Columns hold plain values (the category as its enum name); conversion to the
 * domain [com.foxnks.dockshift.domain.machine.Machine] happens in the mappers,
 * so the table format never depends on Kotlin class details.
 *
 * @property id auto-generated identity, referenced by shift assignments.
 * @property category the category name, e.g. "RTG".
 * @property number the fleet number, e.g. "12".
 * @property active false once soft-deleted.
 */
@Entity(
    tableName = "machines",
    // The same physical machine can exist only once
    indices = [Index("category", "number", unique = true)],
)
data class MachineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val category: String,
    val number: String,
    val active: Boolean = true,
)
