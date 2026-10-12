package com.foxnks.dockshift.domain.legacy

/**
 * Something in a v1 file that prevents importing it.
 *
 * These are data, not messages: the UI turns each one into a sentence in the
 * user's language, e.g. "Line 213: unknown shift type «Πρω»".
 *
 * @property line the file line the problem was found on (1-based).
 */
sealed interface LegacyProblem {
    val line: Int

    /** A quoted value is opened but never closed; the rest of the file is unreadable. */
    data class UnterminatedQuote(override val line: Int) : LegacyProblem

    /** A row does not have the 10 columns of the v1 format. */
    data class WrongColumnCount(override val line: Int, val found: Int) : LegacyProblem

    /** A column that must hold a whole number holds something else. */
    data class NotANumber(override val line: Int, val column: NumberColumn, val value: String) : LegacyProblem

    /** Day, month and year are numbers but not a real date (e.g. 31/2). */
    data class InvalidDate(override val line: Int, val day: Int, val month: Int, val year: Int) : LegacyProblem

    /** The shift label is not one v1 ever wrote. */
    data class UnknownShiftType(override val line: Int, val value: String) : LegacyProblem

    /** The machine category is not one of the fixed categories. */
    data class UnknownMachineCategory(override val line: Int, val value: String) : LegacyProblem

    /** The numeric columns of the v1 format, for [NotANumber]. */
    enum class NumberColumn { DAY, MONTH, YEAR, CONTAINERS }
}

/**
 * Thrown when a v1 file is structurally broken, so that no row after the damage
 * can be trusted. Value-level problems are collected instead (see [LegacyImportPlan]).
 *
 * @property problem what is wrong, and where.
 */
class LegacyCsvException(val problem: LegacyProblem) : Exception(problem.toString())
