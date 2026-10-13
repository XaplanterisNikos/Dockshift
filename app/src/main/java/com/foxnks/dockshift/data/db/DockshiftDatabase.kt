package com.foxnks.dockshift.data.db

import androidx.room3.Database
import androidx.room3.RoomDatabase
import com.foxnks.dockshift.data.db.dao.ImportDao
import com.foxnks.dockshift.data.db.dao.MachineDao
import com.foxnks.dockshift.data.db.dao.ShiftDao
import com.foxnks.dockshift.data.db.entity.MachineEntity
import com.foxnks.dockshift.data.db.entity.ShiftEntity
import com.foxnks.dockshift.data.db.entity.ShiftMachineEntity

/**
 * The app's Room database.
 *
 * Every schema change raises [version] and must ship with a migration that keeps
 * the users' data; destructive migrations are never used. `exportSchema` writes
 * each version's schema to `app/schemas/` so migrations can be validated.
 *
 * Versions:
 * 1. machines, shifts, shift_machines.
 * 2. machines.location ([DatabaseMigrations.MIGRATION_1_2]).
 */
@Database(
    entities = [MachineEntity::class, ShiftEntity::class, ShiftMachineEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class DockshiftDatabase : RoomDatabase() {

    /** Shifts and their machine lists. */
    abstract fun shiftDao(): ShiftDao

    /** The machine list. */
    abstract fun machineDao(): MachineDao

    /** Bulk imports (v1 migration, backups). */
    abstract fun importDao(): ImportDao

    companion object {
        /** File name of the database in the app's private storage. */
        const val NAME = "dockshift.db"
    }
}
