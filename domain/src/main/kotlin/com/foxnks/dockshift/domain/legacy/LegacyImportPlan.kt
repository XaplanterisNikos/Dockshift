package com.foxnks.dockshift.domain.legacy

import com.foxnks.dockshift.domain.machine.MachineCategory
import com.foxnks.dockshift.domain.shift.ShiftType
import java.time.LocalDate

/**
 * A machine as referenced by a v1 row: category and number only, because v1 had
 * no machine identities. It is matched to (or added as) a real machine on import.
 *
 * @property category the machine's category.
 * @property number its fleet number, trimmed.
 */
data class LegacyMachine(
    val category: MachineCategory,
    val number: String,
) {
    /** Display text, e.g. "RTG 12". */
    val label: String
        get() = "${category.name} $number"
}

/**
 * One shift ready to be imported, already translated from v1 values.
 *
 * @property date the day.
 * @property type the shift type.
 * @property machine the machine driven, or null.
 * @property comments the comment, including notes for v1 data that has no place
 * in v2 (container moves, a machine on a day off); null when empty.
 */
data class LegacyShift(
    val date: LocalDate,
    val type: ShiftType,
    val machine: LegacyMachine?,
    val comments: String?,
)

/**
 * The outcome of reading a v1 file, shown to the user before anything is saved.
 *
 * @property shifts the shifts that would be imported, sorted by date.
 * @property problems everything that prevents the import; when not empty,
 * nothing may be saved.
 * @property rowsRead data rows found in the file.
 * @property duplicatesInFile rows skipped because an earlier row had the same date
 * (the first one is kept).
 * @property alreadyInApp rows skipped because the app already has a shift on that
 * date (what is in the app always wins).
 * @property newMachines machines the file uses that the app does not know yet;
 * they are added during the import.
 */
data class LegacyImportPlan(
    val shifts: List<LegacyShift>,
    val problems: List<LegacyProblem>,
    val rowsRead: Int,
    val duplicatesInFile: Int,
    val alreadyInApp: Int,
    val newMachines: Set<LegacyMachine>,
) {
    /** True when the file is fully valid and has something to import. */
    val canImport: Boolean
        get() = problems.isEmpty() && shifts.isNotEmpty()

    /** First day that would be imported, or null if none. */
    val firstDate: LocalDate?
        get() = shifts.firstOrNull()?.date

    /** Last day that would be imported, or null if none. */
    val lastDate: LocalDate?
        get() = shifts.lastOrNull()?.date

    companion object {
        /**
         * A plan for a file that could not even be split into rows.
         *
         * @param problem the structural problem.
         */
        fun broken(problem: LegacyProblem): LegacyImportPlan = LegacyImportPlan(
            shifts = emptyList(),
            problems = listOf(problem),
            rowsRead = 0,
            duplicatesInFile = 0,
            alreadyInApp = 0,
            newMachines = emptySet(),
        )
    }
}

/**
 * Wording of the notes added to comments for v1 data that v2 has no field for.
 * Implemented in the app with string resources, so the notes follow the
 * user's language; the domain stays free of translated text.
 */
interface LegacyNotes {

    /**
     * @param moves the v1 "containers" value, greater than zero.
     * @return e.g. "Κινήσεις: 34".
     */
    fun containerMoves(moves: Int): String

    /**
     * @param machineLabel e.g. "RTG 12".
     * @return a note for a machine recorded on a day that was not a working shift.
     */
    fun machineOnNonWorkingDay(machineLabel: String): String
}
