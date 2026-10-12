package com.foxnks.dockshift.domain.legacy

/**
 * Reads the backup file of Dockshift v1 (v1.10 and v1.11).
 *
 * A Kotlin port of `ShiftCsv.read` from v1.11, including its repairs for files
 * written by v1.10, which did not quote values:
 * - a comment containing commas produced extra columns: they are joined back;
 * - a comment containing a line break split the row in two lines: they are joined back.
 *
 * It only splits the text into [LegacyRow]s; it does not interpret any value
 * (that is [LegacyImportPlanner]'s job).
 */
object LegacyCsvParser {

    /** Every v1 row has exactly this many columns. */
    const val COLUMN_COUNT = 10

    // Positions of the v1 columns that are kept (id = 0 and dayOfWeek = 1 are not)
    private const val COL_DAY = 2
    private const val COL_MONTH = 3
    private const val COL_YEAR = 4
    private const val COL_SHIFT = 5
    private const val COL_CATEGORY = 6
    private const val COL_MACHINE = 7
    private const val COL_COMMENTS = 8
    private const val COL_CONTAINERS = 9

    /**
     * Splits a whole v1 file into rows.
     *
     * Accepts a UTF-8 byte order mark, Windows (CRLF) line endings and blank lines.
     *
     * @param text the whole file contents.
     * @return the rows in file order; empty for an empty file.
     * @throws LegacyCsvException if the file is structurally broken (unterminated
     * quote, or a row whose columns cannot be repaired to exactly 10).
     */
    fun parse(text: String): List<LegacyRow> {
        // The byte order mark some editors add must not become part of the first value
        val records = splitRecords(text.removePrefix("﻿"))
        val rows = mutableListOf<LegacyRow>()

        var i = 0
        while (i < records.size) {
            val record = records[i++]
            var fields = record.fields.toMutableList()

            // v1.10 repair: an unquoted line break inside a comment cut the row short.
            // Glue following short, unquoted lines back on, but never glue a damaged
            // row onto a complete one (that one is a real row of its own).
            while (fields.size < COLUMN_COUNT && !record.quoted && i < records.size &&
                !records[i].quoted && records[i].fields.size < COLUMN_COUNT
            ) {
                val next = records[i++]
                fields[fields.lastIndex] = fields.last() + "\n" + next.fields.first()
                fields.addAll(next.fields.drop(1))
            }

            if (fields.size < COLUMN_COUNT) {
                throw LegacyCsvException(LegacyProblem.WrongColumnCount(record.line, fields.size))
            }
            if (fields.size > COLUMN_COUNT) {
                // A quoted row is well-formed, so extra columns there are real damage
                if (record.quoted) {
                    throw LegacyCsvException(LegacyProblem.WrongColumnCount(record.line, fields.size))
                }
                // v1.10 repair: commas inside an unquoted comment made extra columns
                fields = mergeCommentColumns(fields)
            }

            rows += LegacyRow(
                line = record.line,
                day = fields[COL_DAY],
                month = fields[COL_MONTH],
                year = fields[COL_YEAR],
                shiftLabel = fields[COL_SHIFT],
                category = fields[COL_CATEGORY],
                machine = fields[COL_MACHINE],
                comments = fields[COL_COMMENTS],
                containers = fields[COL_CONTAINERS],
            )
        }
        return rows
    }

    /**
     * Rejoins a row that has too many columns because its comment held commas.
     * Everything between the machine column and the last column (containers) is
     * the comment.
     */
    private fun mergeCommentColumns(fields: List<String>): MutableList<String> {
        val comment = fields.subList(COL_COMMENTS, fields.lastIndex).joinToString(",")
        return (fields.subList(0, COL_COMMENTS) + comment + fields.last()).toMutableList()
    }

    /**
     * One CSV record as split by [splitRecords].
     *
     * @property line file line where the record starts (1-based).
     * @property quoted true if any of its values was quoted (a well-formed v1.11 row).
     */
    private class Record(val line: Int) {
        val fields = mutableListOf<String>()
        var quoted = false

        /** True for an empty line, which is skipped. */
        val isBlank: Boolean
            get() = !quoted && fields.size == 1 && fields[0].isBlank()
    }

    /**
     * Splits text into RFC 4180 records: quoted values may contain commas, doubled
     * quotes and line breaks. Blank lines are skipped.
     *
     * @throws LegacyCsvException if a quoted value is never closed.
     */
    private fun splitRecords(text: String): List<Record> {
        val records = mutableListOf<Record>()
        var line = 1
        var record = Record(line)
        val field = StringBuilder()
        var inQuotes = false
        var quoteLine = 0

        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                inQuotes -> when {
                    // A doubled quote inside quotes is one literal quote
                    c == '"' && i + 1 < text.length && text[i + 1] == '"' -> {
                        field.append('"')
                        i++
                    }
                    c == '"' -> inQuotes = false
                    else -> {
                        // Line breaks inside quotes belong to the value, but still
                        // advance the file line counter for later error messages
                        if (c == '\n') line++
                        field.append(c)
                    }
                }
                // A quote only opens a quoted value at the very start of the value
                c == '"' && field.isEmpty() -> {
                    inQuotes = true
                    quoteLine = line
                    record.quoted = true
                }
                c == ',' -> {
                    record.fields += field.toString()
                    field.clear()
                }
                c == '\n' || c == '\r' -> {
                    // CRLF counts as a single line break
                    if (c == '\r' && i + 1 < text.length && text[i + 1] == '\n') i++
                    record.fields += field.toString()
                    field.clear()
                    if (!record.isBlank) records += record
                    line++
                    record = Record(line)
                }
                else -> field.append(c)
            }
            i++
        }

        if (inQuotes) throw LegacyCsvException(LegacyProblem.UnterminatedQuote(quoteLine))

        // The last line may have no trailing line break
        record.fields += field.toString()
        if (!record.isBlank) records += record
        return records
    }
}
