package com.foxnks.dockshift.testing

import com.foxnks.dockshift.data.legacy.TextFileReader
import com.foxnks.dockshift.domain.legacy.LegacyImportRepository
import com.foxnks.dockshift.domain.legacy.LegacyMachine
import com.foxnks.dockshift.domain.legacy.LegacyNotes
import com.foxnks.dockshift.domain.legacy.LegacyShift
import com.foxnks.dockshift.domain.shift.Shift
import com.foxnks.dockshift.domain.shift.ShiftRepository
import java.io.IOException
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * In-memory [LegacyImportRepository]. Saved shifts also raise [shiftCount], so a
 * [FakeShiftRepository] sharing it sees the import, as the real database would.
 *
 * @param shiftCount the shared stored-shift counter.
 */
class FakeLegacyImportRepository(private val shiftCount: MutableStateFlow<Int>) : LegacyImportRepository {

    /** Everything saved so far, in order. */
    val saved = mutableListOf<LegacyShift>()

    override suspend fun existingDates(): Set<LocalDate> = saved.map { it.date }.toSet()

    override suspend fun knownMachines(): Set<LegacyMachine> = emptySet()

    override suspend fun save(shifts: List<LegacyShift>): Int {
        saved += shifts
        shiftCount.value += shifts.size
        return shifts.size
    }
}

/**
 * [ShiftRepository] that only knows how many shifts there are; the other
 * functions are not used by the code under test and fail loudly if called.
 *
 * @param count the shared stored-shift counter.
 */
class FakeShiftRepository(private val count: MutableStateFlow<Int>) : ShiftRepository {
    override fun observeCount(): Flow<Int> = count
    override fun observeShifts(from: LocalDate, to: LocalDate): Flow<List<Shift>> = error("not used")
    override fun observeShift(date: LocalDate): Flow<Shift?> = error("not used")
    override suspend fun save(shift: Shift): Unit = error("not used")
    override suspend fun delete(date: LocalDate): Unit = error("not used")
    override suspend fun deleteAll(): Unit = error("not used")
}

/**
 * [TextFileReader] serving fixed texts by uri; unknown uris fail like an unreadable file.
 *
 * @param files uri → file contents.
 */
class FakeTextFileReader(private val files: Map<String, String>) : TextFileReader {
    override suspend fun read(uri: String): String = files[uri] ?: throw IOException("No such file: $uri")
}

/** Notes with plain, language-independent wording. */
object PlainLegacyNotes : LegacyNotes {
    override fun containerMoves(moves: Int) = "moves=$moves"
    override fun machineOnNonWorkingDay(machineLabel: String) = "machine=$machineLabel"
}
