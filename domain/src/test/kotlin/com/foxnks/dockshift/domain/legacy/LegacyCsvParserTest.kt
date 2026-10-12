package com.foxnks.dockshift.domain.legacy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The 10 tests of `ShiftCsvTest` (v1.11), ported. v2 only reads the v1 format, so
 * the three v1 writer tests became the matching reader tests: whatever v1.11 wrote
 * must read back correctly.
 */
class LegacyCsvParserTest {

    // 1. readsPlainV110Rows
    @Test
    fun `reads plain v1_10 rows`() {
        // Exactly what v1.10 wrote: no quotes, empty comment as "null"
        val rows = LegacyCsvParser.parse(
            "1,Δευτέρα,3,10,2022,Πρωί,RTG,12,null,45\n" +
                "2,Τρίτη,4,10,2022,Ρεπό,none,none,null,0\n",
        )

        assertEquals(2, rows.size)
        assertEquals(
            LegacyRow(
                line = 1, day = "3", month = "10", year = "2022", shiftLabel = "Πρωί",
                category = "RTG", machine = "12", comments = "null", containers = "45",
            ),
            rows[0],
        )
        assertEquals("Ρεπό", rows[1].shiftLabel)
        assertEquals(2, rows[1].line)
    }

    // 2. repairsV110CommaInsideComment
    @Test
    fun `repairs v1_10 comma inside comment`() {
        // v1.10 did not quote comments, so this row has 12 columns
        val rows = LegacyCsvParser.parse("7,Τετάρτη,5,10,2022,Νύχτα,QC,3,βλάβη, αλλαγή, μετά OK,30\n")

        assertEquals(1, rows.size)
        assertEquals("βλάβη, αλλαγή, μετά OK", rows[0].comments)
        assertEquals("30", rows[0].containers)
    }

    // 3. repairsV110LineBreakInsideComment
    @Test
    fun `repairs v1_10 line break inside comment`() {
        // v1.10 wrote a multi-line comment unquoted, splitting the row in two lines
        val rows = LegacyCsvParser.parse(
            "1,Πέμπτη,6,10,2022,Πρωί,SC,101,πρώτη γραμμή\n" +
                "δεύτερη γραμμή,12\n" +
                "2,Παρασκευή,7,10,2022,Απόγευμα,FL,4,null,0\n",
        )

        assertEquals(2, rows.size)
        assertEquals("πρώτη γραμμή\nδεύτερη γραμμή", rows[0].comments)
        assertEquals("12", rows[0].containers)
        assertEquals("Απόγευμα", rows[1].shiftLabel)
        // The second row starts on line 3 of the file
        assertEquals(3, rows[1].line)
    }

    // 4. roundTripsNewFormatWithSpecialCharacters → reads what v1.11 wrote for it
    @Test
    fun `reads v1_11 quoted comment with comma, quotes and line break`() {
        val rows = LegacyCsvParser.parse(
            "0,Σάββατο,8,10,2026,Νύχτα,RMG,5,\"σχόλιο με, κόμμα \"\"εισαγωγικά\"\"\nκαι αλλαγή γραμμής\",17\n",
        )

        assertEquals(1, rows.size)
        assertEquals("σχόλιο με, κόμμα \"εισαγωγικά\"\nκαι αλλαγή γραμμής", rows[0].comments)
        assertEquals("2026", rows[0].year)
        assertEquals("17", rows[0].containers)
    }

    // 5. plainRowsAreWrittenExactlyLikeV110 → a plain v1.11 row reads like v1.10
    @Test
    fun `plain v1_11 row reads field by field`() {
        val rows = LegacyCsvParser.parse("0,Κυριακή,9,10,2026,Πρωί,TRUCKS,7,ok,3\n")

        assertEquals(
            LegacyRow(
                line = 1, day = "9", month = "10", year = "2026", shiftLabel = "Πρωί",
                category = "TRUCKS", machine = "7", comments = "ok", containers = "3",
            ),
            rows.single(),
        )
    }

    // 6. emptyCommentIsWrittenAsEmptyNotNull → v1.11's empty comment reads as empty
    @Test
    fun `v1_11 empty comment reads as empty`() {
        val rows = LegacyCsvParser.parse("0,Δευτέρα,1,1,2026,Ρεπό,none,none,,0\n")

        assertEquals("", rows.single().comments)
    }

    // 7. handlesWindowsLineEndingsBomAndBlankLines
    @Test
    fun `handles windows line endings, BOM and blank lines`() {
        val rows = LegacyCsvParser.parse("﻿1,Δευτέρα,3,10,2022,Πρωί,RTG,12,null,45\r\n\r\n")

        assertEquals(1, rows.size)
        assertEquals("45", rows[0].containers)
        // The BOM must not stick to the first value of the file
        assertEquals("3", rows[0].day)
    }

    // 8. rejectsBrokenRowInsteadOfImportingPartially
    @Test
    fun `broken value is left for the planner, with its line`() {
        // v1.11 failed while reading; in v2 the parser keeps the raw text and the
        // planner reports it (see LegacyImportPlannerTest), so ALL problems are listed
        val rows = LegacyCsvParser.parse(
            "1,Δευτέρα,3,10,2022,Πρωί,RTG,12,null,45\n" +
                "2,Τρίτη,X,10,2022,Πρωί,RTG,12,null,45\n",
        )

        assertEquals("X", rows[1].day)
        assertEquals(2, rows[1].line)
    }

    // 9. rejectsTruncatedRowFollowedByFullRow
    @Test
    fun `rejects truncated row followed by full row`() {
        // A damaged short row must not be glued onto the next valid row
        val error = assertThrows(LegacyCsvException::class.java) {
            LegacyCsvParser.parse(
                "1,Δευτέρα,3,10\n" +
                    "2,Τρίτη,4,10,2022,Πρωί,RTG,12,null,45\n",
            )
        }

        assertEquals(LegacyProblem.WrongColumnCount(line = 1, found = 4), error.problem)
    }

    // 10. emptyFileGivesNoShifts
    @Test
    fun `empty file gives no rows`() {
        assertTrue(LegacyCsvParser.parse("").isEmpty())
    }

    // New in v2

    @Test
    fun `unterminated quote is reported at the line it opens`() {
        val error = assertThrows(LegacyCsvException::class.java) {
            LegacyCsvParser.parse(
                "1,Δευτέρα,3,10,2022,Πρωί,RTG,12,null,0\n" +
                    "2,Τρίτη,4,10,2022,Πρωί,RTG,12,\"never closed,0\n",
            )
        }

        assertEquals(LegacyProblem.UnterminatedQuote(line = 2), error.problem)
    }

    @Test
    fun `quoted row with extra columns is damage, not a v1_10 comment`() {
        val error = assertThrows(LegacyCsvException::class.java) {
            LegacyCsvParser.parse("1,Δευτέρα,3,10,2022,Πρωί,RTG,12,\"ok\",0,extra\n")
        }

        assertEquals(LegacyProblem.WrongColumnCount(line = 1, found = 11), error.problem)
    }
}
