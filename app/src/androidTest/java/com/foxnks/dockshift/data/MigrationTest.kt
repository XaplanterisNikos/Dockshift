package com.foxnks.dockshift.data

import android.content.Context
import androidx.room3.Room
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.foxnks.dockshift.data.db.DatabaseMigrations
import com.foxnks.dockshift.data.db.DockshiftDatabase
import com.foxnks.dockshift.data.leave.RoomLeaveRepository
import com.foxnks.dockshift.data.machine.RoomMachineRepository
import com.foxnks.dockshift.data.shift.RoomShiftRepository
import com.foxnks.dockshift.domain.leave.Leave
import com.foxnks.dockshift.domain.shift.ShiftType
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Upgrades a real version-1 database file to the current version (through every
 * migration in between), exactly as it happens on a phone that installs a new app
 * version over an old one.
 *
 * The version-1 file is written by hand with the SQL of
 * `app/schemas/.../1.json`, so this test keeps working however the code changes.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val name = "migration-test.db"
    private lateinit var path: String

    @Before
    fun deleteOldFile() {
        context.deleteDatabase(name)
        path = context.getDatabasePath(name).absolutePath
        context.getDatabasePath(name).parentFile?.mkdirs()
    }

    @After
    fun cleanUp() {
        context.deleteDatabase(name)
    }

    /** Runs one SQL statement that returns no rows. */
    private fun SQLiteConnection.exec(sql: String) = prepare(sql).use { it.step() }

    /** Writes a version-1 database with two machines and one night shift on RTG 12. */
    private fun createVersion1() {
        val connection = BundledSQLiteDriver().open(path)
        try {
            // Schema of version 1, copied from app/schemas/.../1.json
            connection.exec("CREATE TABLE IF NOT EXISTS `machines` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `category` TEXT NOT NULL, `number` TEXT NOT NULL, `active` INTEGER NOT NULL)")
            connection.exec("CREATE TABLE IF NOT EXISTS `shifts` (`date` TEXT NOT NULL, `type` TEXT NOT NULL, `overtime` INTEGER NOT NULL, `comments` TEXT, PRIMARY KEY(`date`))")
            connection.exec("CREATE TABLE IF NOT EXISTS `shift_machines` (`shiftDate` TEXT NOT NULL, `position` INTEGER NOT NULL, `machineId` INTEGER NOT NULL, `duringOvertime` INTEGER NOT NULL, PRIMARY KEY(`shiftDate`, `position`), FOREIGN KEY(`shiftDate`) REFERENCES `shifts`(`date`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`machineId`) REFERENCES `machines`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION )")
            connection.exec("CREATE UNIQUE INDEX IF NOT EXISTS `index_machines_category_number` ON `machines` (`category`, `number`)")
            connection.exec("CREATE INDEX IF NOT EXISTS `index_shift_machines_machineId` ON `shift_machines` (`machineId`)")
            connection.exec("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
            connection.exec("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '7cf3ca11f4b42e8fc56ad0627dcd3430')")
            // Data as a version-1 app would have stored it
            connection.exec("INSERT INTO machines (id, category, number, active) VALUES (1, 'RTG', '12', 1)")
            connection.exec("INSERT INTO machines (id, category, number, active) VALUES (2, 'TRUCKS', '6999', 0)")
            connection.exec("INSERT INTO shifts (date, type, overtime, comments) VALUES ('2025-01-06', 'NIGHT', 0, 'σχόλιο')")
            connection.exec("INSERT INTO shift_machines (shiftDate, position, machineId, duringOvertime) VALUES ('2025-01-06', 0, 1, 0)")
            connection.exec("PRAGMA user_version = 1")
        } finally {
            connection.close()
        }
    }

    @Test
    fun version1UpgradesKeepingEveryShiftAndAddingLocations() = runTest {
        createVersion1()

        // Opened like the app does (no seed callback: the file already exists)
        val database = Room.databaseBuilder<DockshiftDatabase>(context, name)
            .setDriver(BundledSQLiteDriver())
            .addMigrations(*DatabaseMigrations.ALL)
            .build()
        try {
            val shift = RoomShiftRepository(database.shiftDao()).observeShift(LocalDate.of(2025, 1, 6)).first()!!
            val machines = RoomMachineRepository(database.machineDao()).observeAll().first()

            // The shift is untouched...
            assertEquals(ShiftType.NIGHT, shift.type)
            assertEquals("σχόλιο", shift.comments)
            // ...and now knows where its machine is
            assertEquals("Προβλήτα ΙΙ Μπλόκ 15", shift.machines.single().machine.location)
            // Odd truck number: odd parking; deactivated machines are migrated too
            assertEquals("Προβλήτα ΙΙΙ Πάρκινγκ Μονά", machines.single { it.number == "6999" }.location)
        } finally {
            database.close()
        }
    }

    @Test
    fun machineWithoutV1LocationStaysEmpty() = runTest {
        createVersion1()
        BundledSQLiteDriver().open(path).also { connection ->
            // An RTG number v1 never had
            connection.exec("INSERT INTO machines (id, category, number, active) VALUES (3, 'RTG', '99', 1)")
            connection.close()
        }

        val database = Room.databaseBuilder<DockshiftDatabase>(context, name)
            .setDriver(BundledSQLiteDriver())
            .addMigrations(*DatabaseMigrations.ALL)
            .build()
        try {
            val machines = RoomMachineRepository(database.machineDao()).observeAll().first()
            assertNull(machines.single { it.number == "99" }.location)
        } finally {
            database.close()
        }
    }

    @Test
    fun upgradedDatabaseCanStoreLeaves() = runTest {
        createVersion1()

        val database = Room.databaseBuilder<DockshiftDatabase>(context, name)
            .setDriver(BundledSQLiteDriver())
            .addMigrations(*DatabaseMigrations.ALL)
            .build()
        try {
            // Version 1 → 2 → 3: the new leaves table exists, starts empty and works
            val leaves = RoomLeaveRepository(database.leaveDao())
            assertEquals(emptyList<Leave>(), leaves.observeAll().first())

            val saved = leaves.save(Leave(0, LocalDate.of(2026, 12, 21), LocalDate.of(2026, 12, 26), "Χριστούγεννα"))

            assertEquals(listOf(saved), leaves.observeAll().first())
            // The shifts of version 1 are still there
            assertEquals(ShiftType.NIGHT, RoomShiftRepository(database.shiftDao()).observeShift(LocalDate.of(2025, 1, 6)).first()?.type)
        } finally {
            database.close()
        }
    }
}
