package com.foxnks.dockshift.data

import android.content.Context
import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.foxnks.dockshift.data.db.DockshiftDatabase
import com.foxnks.dockshift.data.db.MachineSeed
import com.foxnks.dockshift.data.db.MachineSeedCallback
import com.foxnks.dockshift.data.machine.RoomMachineRepository
import com.foxnks.dockshift.data.shift.RoomShiftRepository
import com.foxnks.dockshift.domain.machine.Machine
import com.foxnks.dockshift.domain.machine.MachineCategory
import com.foxnks.dockshift.domain.shift.MachineAssignment
import com.foxnks.dockshift.domain.shift.Shift
import com.foxnks.dockshift.domain.shift.ShiftType
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The real Room database (in memory, recreated for every test) behind the
 * repositories, on an emulator or device. Covers what JVM tests cannot:
 * SQL, transactions, relations, constraints and the first-run seed.
 */
@RunWith(AndroidJUnit4::class)
class DatabaseTest {

    private lateinit var database: DockshiftDatabase
    private lateinit var shifts: RoomShiftRepository
    private lateinit var machines: RoomMachineRepository

    private val day = LocalDate.of(2026, 10, 13)

    @Before
    fun createDatabase() {
        // Same driver and seed as the app; in memory, so nothing touches real data
        database = Room.inMemoryDatabaseBuilder<DockshiftDatabase>(
            // getApplicationContext is generic: the type must be named explicitly
            ApplicationProvider.getApplicationContext<Context>(),
        )
            .setDriver(BundledSQLiteDriver())
            .addCallback(MachineSeedCallback())
            .build()
        shifts = RoomShiftRepository(database.shiftDao())
        machines = RoomMachineRepository(database.machineDao())
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    /** Looks up a seeded machine by its label, e.g. "RTG 12". */
    private suspend fun machine(label: String): Machine =
        machines.observeActive().first().single { it.label == label }

    @Test
    fun firstRunSeedsTheV1Fleet() = runTest {
        val all = machines.observeActive().first()

        assertEquals(MachineSeed.machines.values.sumOf { it.size }, all.size)
        assertEquals("SC 97", all.first().label)
        // A fresh install starts with the v1 locations
        assertEquals("Προβλήτα ΙΙ Μπλόκ 15", all.single { it.label == "RTG 12" }.location)
    }

    @Test
    fun savedShiftReadsBackWithMachinesInOrder() = runTest {
        val shift = Shift(
            date = day,
            type = ShiftType.NIGHT,
            overtime = true,
            machines = listOf(
                MachineAssignment(machine("RTG 12")),
                MachineAssignment(machine("RTG 7")),
                MachineAssignment(machine("QC 31"), duringOvertime = true),
            ),
            comments = "Μεταφορά στο QC 31 για την υπερωρία",
        )

        shifts.save(shift)

        assertEquals(shift, shifts.observeShift(day).first())
    }

    @Test
    fun savingTheSameDayReplacesTheWholeShift() = runTest {
        shifts.save(Shift(day, ShiftType.MORNING, machines = listOf(MachineAssignment(machine("RTG 12")))))
        val dayOff = Shift(day, ShiftType.DAY_OFF)

        shifts.save(dayOff)

        // Still one shift for the day, and no machines left over from the old one
        assertEquals(listOf(dayOff), shifts.observeShifts(day, day).first())
    }

    @Test
    fun rangeIsInclusiveAndSorted() = runTest {
        val dates = listOf(day.plusDays(2), day, day.plusDays(1), day.plusDays(3))
        dates.forEach { shifts.save(Shift(it, ShiftType.DAY_OFF)) }

        val result = shifts.observeShifts(day, day.plusDays(2)).first().map { it.date }

        assertEquals(listOf(day, day.plusDays(1), day.plusDays(2)), result)
    }

    @Test
    fun deleteRemovesTheShift() = runTest {
        shifts.save(Shift(day, ShiftType.AFTERNOON, machines = listOf(MachineAssignment(machine("SC 97")))))

        shifts.delete(day)

        assertNull(shifts.observeShift(day).first())
    }

    @Test
    fun deactivatedMachineStaysInShiftHistory() = runTest {
        val rtg12 = machine("RTG 12")
        shifts.save(Shift(day, ShiftType.MORNING, machines = listOf(MachineAssignment(rtg12))))

        machines.setActive(rtg12.id, false)

        // Gone from the pick list...
        assertTrue(machines.observeActive().first().none { it.id == rtg12.id })
        // ...but the old shift still shows it, marked inactive
        val historic = shifts.observeShift(day).first()!!.machines.single().machine
        assertEquals("RTG 12", historic.label)
        assertFalse(historic.active)
    }

    @Test
    fun addingAnExistingMachineReactivatesTheSameOne() = runTest {
        val rtg12 = machine("RTG 12")
        machines.setActive(rtg12.id, false)

        val readded = machines.addOrReactivate(MachineCategory.RTG, " 12 ")

        // Same identity, so its history stays connected
        assertEquals(rtg12.id, readded.id)
        assertTrue(readded.active)
    }

    @Test
    fun addingANewMachineMakesItAvailable() = runTest {
        val added = machines.addOrReactivate(MachineCategory.TRUCKS, "6411")

        assertTrue(machines.observeActive().first().any { it.id == added.id && it.label == "TRUCKS 6411" })
    }

    @Test
    fun newTruckStartsAtItsParking() = runTest {
        // Odd fleet number: the odd-numbers parking, as in v1
        val added = machines.addOrReactivate(MachineCategory.TRUCKS, "6411")

        assertEquals("Προβλήτα ΙΙΙ Πάρκινγκ Μονά", added.location)
    }

    @Test
    fun renamedMachineShowsInOldShifts() = runTest {
        val rtg12 = machine("RTG 12")
        shifts.save(Shift(day, ShiftType.MORNING, machines = listOf(MachineAssignment(rtg12))))

        machines.save(rtg12.copy(number = "46", location = "Μπλόκ 1"))

        // Shifts point at the machine, not at its number: the history follows the rename
        val historic = shifts.observeShift(day).first()!!.machines.single().machine
        assertEquals("RTG 46", historic.label)
        assertEquals("Μπλόκ 1", historic.location)
    }

    @Test
    fun savingANewMachineGivesItAnId() = runTest {
        val saved = machines.save(Machine(0, MachineCategory.QC, "50", location = "Προβλήτα ΙΙ"))

        assertTrue(saved.id > 0)
        assertEquals(saved, machines.observeAll().first().single { it.id == saved.id })
    }

    @Test
    fun usageCountsDistinctShifts() = runTest {
        val rtg12 = machine("RTG 12")
        // Twice in one shift (regular and overtime) counts as one shift
        shifts.save(
            Shift(
                day,
                ShiftType.MORNING,
                overtime = true,
                machines = listOf(MachineAssignment(rtg12), MachineAssignment(rtg12, duringOvertime = true)),
            ),
        )
        shifts.save(Shift(day.plusDays(1), ShiftType.MORNING, machines = listOf(MachineAssignment(rtg12))))

        val usage = machines.observeUsage().first()

        assertEquals(2, usage[rtg12.id])
        // Never-used machines are simply missing
        assertNull(usage[machine("RTG 13").id])
    }

    @Test
    fun machineWithShiftsCannotBeDeleted() = runTest {
        val rtg12 = machine("RTG 12")
        shifts.save(Shift(day, ShiftType.MORNING, machines = listOf(MachineAssignment(rtg12))))

        assertFalse(machines.delete(rtg12.id))
        assertTrue(machines.observeAll().first().any { it.id == rtg12.id })
    }

    @Test
    fun unusedMachineIsDeletedForGood() = runTest {
        val typo = machines.addOrReactivate(MachineCategory.RTG, "466")

        assertTrue(machines.delete(typo.id))
        assertTrue(machines.observeAll().first().none { it.id == typo.id })
    }
}
