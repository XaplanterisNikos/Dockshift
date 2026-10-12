package com.foxnks.dockshift.domain.legacy

import com.foxnks.dockshift.domain.legacy.LegacyProblem.NumberColumn
import com.foxnks.dockshift.domain.machine.MachineCategory
import com.foxnks.dockshift.domain.shift.LegacyShiftType
import java.time.DateTimeException
import java.time.LocalDate

/**
 * Translates v1 rows into shifts to import, without touching any storage.
 *
 * Rules (agreed for the v1 → v2 migration):
 * - "none" (or empty) machine category/number means no machine; "null" (or empty)
 *   comment means no comment; the v1 weekday column is ignored.
 * - Container moves greater than zero are kept as a note in the comment.
 * - A date that appears twice in the file: the first row wins.
 * - A date the app already has: the app wins (merge, never overwrite).
 * - Any unreadable value is reported with its line; the whole import is then refused.
 *
 * @param notes wording of the notes added to comments.
 */
class LegacyImportPlanner(private val notes: LegacyNotes) {

    /**
     * Builds the import plan of a whole file.
     *
     * @param rows the rows from [LegacyCsvParser.parse].
     * @param existingDates days that already have a shift in the app.
     * @param knownMachines machines the app already has (active or not).
     */
    fun plan(
        rows: List<LegacyRow>,
        existingDates: Set<LocalDate>,
        knownMachines: Set<LegacyMachine>,
    ): LegacyImportPlan {
        val problems = mutableListOf<LegacyProblem>()
        // LinkedHashMap keeps the first row of each date, as agreed
        val byDate = LinkedHashMap<LocalDate, LegacyShift>()
        var duplicates = 0
        var alreadyInApp = 0

        for (row in rows) {
            val shift = translate(row, problems) ?: continue
            when {
                shift.date in existingDates -> alreadyInApp++
                shift.date in byDate -> duplicates++
                else -> byDate[shift.date] = shift
            }
        }

        val shifts = byDate.values.sortedBy { it.date }
        return LegacyImportPlan(
            shifts = shifts,
            problems = problems,
            rowsRead = rows.size,
            duplicatesInFile = duplicates,
            alreadyInApp = alreadyInApp,
            newMachines = shifts.mapNotNull { it.machine }.toSet() - knownMachines,
        )
    }

    /**
     * Translates one row, adding every problem found to [problems].
     *
     * @return the shift, or null if the row has at least one problem.
     */
    private fun translate(row: LegacyRow, problems: MutableList<LegacyProblem>): LegacyShift? {
        val found = problems.size

        val day = number(row.day, NumberColumn.DAY, row.line, problems)
        val month = number(row.month, NumberColumn.MONTH, row.line, problems)
        val year = number(row.year, NumberColumn.YEAR, row.line, problems)
        val containers = number(row.containers, NumberColumn.CONTAINERS, row.line, problems)

        val date = if (day != null && month != null && year != null) {
            try {
                LocalDate.of(year, month, day)
            } catch (e: DateTimeException) {
                problems += LegacyProblem.InvalidDate(row.line, day, month, year)
                null
            }
        } else {
            null
        }

        val type = LegacyShiftType.parse(row.shiftLabel)
        if (type == null) problems += LegacyProblem.UnknownShiftType(row.line, row.shiftLabel)

        val machine = machine(row, problems)

        // Any problem in this row: report it and import nothing from it
        if (problems.size > found || date == null || type == null || containers == null) return null

        val extraNotes = buildList {
            if (containers > 0) add(notes.containerMoves(containers))
            // v2 allows machines only on working shifts; keep the information as text
            if (machine != null && !type.isWorkShift) add(notes.machineOnNonWorkingDay(machine.label))
        }
        val comments = (listOfNotNull(comment(row.comments)) + extraNotes)
            .joinToString("\n")
            .ifEmpty { null }

        return LegacyShift(
            date = date,
            type = type,
            machine = machine?.takeIf { type.isWorkShift },
            comments = comments,
        )
    }

    /** Parses a whole number, or reports it. Surrounding spaces are tolerated. */
    private fun number(value: String, column: NumberColumn, line: Int, problems: MutableList<LegacyProblem>): Int? =
        value.trim().toIntOrNull().also {
            if (it == null) problems += LegacyProblem.NotANumber(line, column, value)
        }

    /** v1 machine columns → machine, or null for "none"; reports unknown categories. */
    private fun machine(row: LegacyRow, problems: MutableList<LegacyProblem>): LegacyMachine? {
        val category = row.category.trim()
        val number = row.machine.trim()
        if (category.isNone() || number.isNone()) return null

        val known = MachineCategory.entries.firstOrNull { it.name.equals(category, ignoreCase = true) }
        if (known == null) {
            problems += LegacyProblem.UnknownMachineCategory(row.line, row.category)
            return null
        }
        return LegacyMachine(known, number)
    }

    /** v1 comment → comment, or null for the "null" v1 wrote when there was none. */
    private fun comment(value: String): String? =
        value.takeUnless { it.isBlank() || it.trim() == "null" }

    /** v1 wrote "none" for "no machine". */
    private fun String.isNone(): Boolean = isEmpty() || equals("none", ignoreCase = true)
}
