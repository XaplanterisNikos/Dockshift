package com.foxnks.dockshift.data.db

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import com.foxnks.dockshift.domain.machine.MachineCategory

/**
 * Every schema migration of [DockshiftDatabase], in order.
 *
 * Rules: each migration keeps all user data; SQL is written out in full (never
 * built from constants that could change later); a migration, once released, is
 * never edited.
 */
object DatabaseMigrations {

    /**
     * Version 1 → 2: machines get a `location` column, filled with the v1 locations.
     *
     * Every existing machine, including ones the user or an import added, gets the
     * location v1 would have shown for it; shifts are not touched at all.
     */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override suspend fun migrate(connection: SQLiteConnection) {
            connection.prepare("ALTER TABLE `machines` ADD COLUMN `location` TEXT").use { it.step() }

            // Read every machine first, then update: never write while a read is open
            val machines = mutableListOf<Triple<Long, String, String>>()
            connection.prepare("SELECT `id`, `category`, `number` FROM `machines`").use { statement ->
                while (statement.step()) {
                    machines += Triple(statement.getLong(0), statement.getText(1), statement.getText(2))
                }
            }

            connection.prepare("UPDATE `machines` SET `location` = ? WHERE `id` = ?").use { statement ->
                for ((id, categoryName, number) in machines) {
                    val category = MachineCategory.entries.firstOrNull { it.name == categoryName } ?: continue
                    val location = MachinePositions.locationOf(category, number) ?: continue
                    statement.bindText(1, location)
                    statement.bindLong(2, id)
                    statement.step()
                    // Clear the executed state so the statement can run again
                    statement.reset()
                }
            }
        }
    }

    /**
     * Version 2 → 3: a new `leaves` table for planned annual leave.
     *
     * Only adds a table, so no existing data is touched. The SQL is the one Room
     * generates for `LeaveEntity` (see `app/schemas/.../3.json`).
     */
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override suspend fun migrate(connection: SQLiteConnection) {
            connection.prepare(
                "CREATE TABLE IF NOT EXISTS `leaves` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`startDate` TEXT NOT NULL, `endDate` TEXT NOT NULL, `note` TEXT)",
            ).use { it.step() }
        }
    }

    /** All migrations, for the database builder. */
    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3)
}
