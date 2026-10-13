package com.foxnks.dockshift.data.db

import androidx.room3.RoomDatabase
import androidx.sqlite.SQLiteConnection
import com.foxnks.dockshift.domain.machine.MachineCategory

/**
 * The machine list a fresh install starts with: the same fleet as Dockshift v1
 * (its spinner lists). Users add or soft-delete machines from the settings later.
 */
object MachineSeed {

    /** Fleet numbers per category, in display order. */
    val machines: Map<MachineCategory, List<String>> = mapOf(
        MachineCategory.SC to numbers(97, 100, 101, 107, 108, 111, 114),
        MachineCategory.RTG to numbers(1..45),
        MachineCategory.RMG to numbers(1..22),
        MachineCategory.FL to numbers(545) + numbers(550..553) + numbers(555..564),
        MachineCategory.TRUCKS to numbers(6200) + numbers(6210..6218) + numbers(6225..6235) +
            numbers(6250..6410),
        MachineCategory.QC to numbers(6..23) + numbers(31..43),
    )

    /** Fleet numbers as text, from individual values. */
    private fun numbers(vararg values: Int): List<String> = values.map(Int::toString)

    /** Fleet numbers as text, from a contiguous range. */
    private fun numbers(range: IntRange): List<String> = range.map(Int::toString)
}

/**
 * Inserts [MachineSeed] once, when the database file is first created, with the
 * starting locations of [MachinePositions].
 *
 * Uses the plain SQLite statement API (prepare, bind, step) because the DAOs are
 * not available yet while the database is being created.
 */
class MachineSeedCallback : RoomDatabase.Callback() {

    override suspend fun onCreate(connection: SQLiteConnection) {
        // One prepared statement reused for every row: fast and injection-proof
        connection.prepare("INSERT INTO machines (category, number, active, location) VALUES (?, ?, 1, ?)").use { statement ->
            for ((category, numbers) in MachineSeed.machines) {
                for (number in numbers) {
                    // Parameter indexes are 1-based in SQLite
                    statement.bindText(1, category.name)
                    statement.bindText(2, number)
                    val location = MachinePositions.locationOf(category, number)
                    if (location != null) statement.bindText(3, location) else statement.bindNull(3)
                    statement.step()
                    // Clear the executed state so the statement can run again
                    statement.reset()
                }
            }
        }
    }
}
