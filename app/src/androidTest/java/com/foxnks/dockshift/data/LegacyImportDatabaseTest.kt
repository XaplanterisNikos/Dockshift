package com.foxnks.dockshift.data

import android.content.Context
import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.foxnks.dockshift.data.db.DockshiftDatabase
import com.foxnks.dockshift.data.db.MachineSeedCallback
import com.foxnks.dockshift.data.legacy.RoomLegacyImportRepository
import com.foxnks.dockshift.data.machine.RoomMachineRepository
import com.foxnks.dockshift.data.shift.RoomShiftRepository
import com.foxnks.dockshift.domain.legacy.LegacyMachine
import com.foxnks.dockshift.domain.legacy.LegacyShift
import com.foxnks.dockshift.domain.machine.MachineCategory
import com.foxnks.dockshift.domain.shift.Shift
import com.foxnks.dockshift.domain.shift.ShiftType
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** The v1 import's single transaction, on the real database (in memory). */
@RunWith(AndroidJUnit4::class)
class LegacyImportDatabaseTest {

    private lateinit var database: DockshiftDatabase
    private lateinit var imports: RoomLegacyImportRepository
    private lateinit var shifts: RoomShiftRepository
    private lateinit var machines: RoomMachineRepository

    private val day = LocalDate.of(2022, 10, 3)

    @Before
    fun createDatabase() {
        database = Room.inMemoryDatabaseBuilder<DockshiftDatabase>(
            // getApplicationContext is generic: the type must be named explicitly
            ApplicationProvider.getApplicationContext<Context>(),
        )
            .setDriver(BundledSQLiteDriver())
            .addCallback(MachineSeedCallback())
            .build()
        imports = RoomLegacyImportRepository(database.importDao())
        shifts = RoomShiftRepository(database.shiftDao())
        machines = RoomMachineRepository(database.machineDao())
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun importedShiftsReadBackWithTheirSeededMachine() = runTest {
        val saved = imports.save(
            listOf(
                LegacyShift(day, ShiftType.MORNING, LegacyMachine(MachineCategory.RTG, "12"), "σχόλιο"),
                LegacyShift(day.plusDays(1), ShiftType.DAY_OFF, machine = null, comments = null),
            ),
        )

        assertEquals(2, saved)
        val morning = shifts.observeShift(day).first()!!
        assertEquals("RTG 12", morning.machines.single().machine.label)
        assertEquals("σχόλιο", morning.comments)
        // The seeded machine was linked, not duplicated
        assertEquals(1, machines.observeAll().first().count { it.label == "RTG 12" })
    }

    @Test
    fun unknownMachineIsAddedOnce() = runTest {
        val newTruck = LegacyMachine(MachineCategory.TRUCKS, "6999")

        imports.save(
            listOf(
                LegacyShift(day, ShiftType.NIGHT, newTruck, null),
                LegacyShift(day.plusDays(1), ShiftType.NIGHT, newTruck, null),
            ),
        )

        assertEquals(1, machines.observeActive().first().count { it.label == "TRUCKS 6999" })
        assertTrue(newTruck in imports.knownMachines())
    }

    @Test
    fun existingDaysAreNeverOverwritten() = runTest {
        // A shift entered in v2 before the import
        val mine = Shift(day, ShiftType.AFTERNOON, comments = "from v2")
        shifts.save(mine)

        val saved = imports.save(listOf(LegacyShift(day, ShiftType.MORNING, null, "from v1")))

        assertEquals(0, saved)
        assertEquals(mine, shifts.observeShift(day).first())
    }

    @Test
    fun existingDatesAreReported() = runTest {
        shifts.save(Shift(day, ShiftType.DAY_OFF))

        assertEquals(setOf(day), imports.existingDates())
    }
}
