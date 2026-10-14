package com.foxnks.dockshift.domain.vessels

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * A page that cannot be read: its table lacks a column we need, so the site's
 * layout has changed.
 *
 * @param reason what is missing, in English (for logs and support).
 */
class VesselPageException(reason: String) : Exception(reason)

/**
 * The first HTML table of a page, as rows of cell texts keyed by column title.
 *
 * Deliberately small: the vessel pages are plain server-made tables, so a full HTML
 * parser library is not needed. Column titles are normalized (lower case, letters
 * and digits only: "Completion(%)" → "completion"), so cells are found by meaning,
 * not by position, and a reordered table still reads correctly.
 */
object HtmlTable {

    private val TABLE = Regex("<table\\b.*?</table>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val ROW = Regex("<tr\\b.*?</tr>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val CELL = Regex("<(th|td)\\b[^>]*>(.*?)</\\1\\s*>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val TAG = Regex("<[^>]*>")
    private val SPACES = Regex("\\s+")
    private val NUMERIC_ENTITY = Regex("&#(x[0-9a-fA-F]+|[0-9]+);")

    /**
     * @param html a whole page.
     * @return null when the page has no table (e.g. "no vessels" shown as text);
     * otherwise the header (normalized titles, in order) and the data rows.
     */
    fun parse(html: String): Table? {
        val table = TABLE.find(html)?.value ?: return null
        var header: List<String>? = null
        val rows = mutableListOf<Map<String, String>>()
        ROW.findAll(table).forEach { row ->
            val cells = CELL.findAll(row.value).toList()
            if (cells.isEmpty()) return@forEach
            val texts = cells.map { text(it.groupValues[2]) }
            if (header == null && cells.all { it.groupValues[1].equals("th", ignoreCase = true) }) {
                header = texts.map(::normalize)
            } else {
                val titles = header ?: return@forEach
                // A row that does not fill the columns is a message ("No results"), not a vessel
                if (texts.size == titles.size) rows += titles.zip(texts).toMap()
            }
        }
        return Table(header.orEmpty(), rows)
    }

    /**
     * A table read from a page.
     *
     * @property columns the normalized column titles.
     * @property rows each row as column title → cell text.
     */
    data class Table(val columns: List<String>, val rows: List<Map<String, String>>)

    /** "Completion(%)" → "completion", "Vessel Name" → "vesselname". */
    //fun normalize(title: String): String = title.lowercase().filter { it.isLetterOrDigit() }

    fun normalize(title: String): String {

        val normalized = title.lowercase()
            .filter { it.isLetterOrDigit() }

        return when {
            normalized.startsWith("atasort") -> "ata"
            normalized.startsWith("etbsort") -> "etb"
            normalized.startsWith("etdsort") -> "etd"
            else -> normalized
        }

    }

    /** The visible text of a cell: tags removed, entities decoded, spaces collapsed. */
    private fun text(html: String): String =
        decode(TAG.replace(html, " ")).replace(SPACES, " ").trim()

    /** Decodes the HTML entities a cell may contain. */
    private fun decode(text: String): String {
        val named = text
            .replace("&nbsp;", " ")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
        val numeric = NUMERIC_ENTITY.replace(named) { match ->
            val code = match.groupValues[1]
            val value = if (code.startsWith("x")) code.drop(1).toIntOrNull(16) else code.toIntOrNull()
            value?.let { String(Character.toChars(it)) } ?: match.value
        }
        // Last, so "&amp;lt;" stays the text "&lt;"
        return numeric.replace("&amp;", "&")
    }
}

/** Reads the two vessel pages of the terminal's site. */
object VesselPages {

    private val DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")

    /**
     * The vessels being worked, by name.
     *
     * @param html the "working vessels" page.
     * @throws VesselPageException when the table lacks a needed column.
     */
    fun parseWorking(html: String): List<WorkingVessel> {
        val table = HtmlTable.parse(html) ?: return emptyList()
        println("VesselDebug - Working table columns: ${table.columns}")
        table.require("vesselname", "ata", "etd", "completion")
        return table.rows.mapNotNull { row ->
            val name = row["vesselname"].orEmpty()
            if (name.isEmpty()) return@mapNotNull null
            WorkingVessel(
                name = name,
                callSign = row["callsign"].orEmpty(),
                arrived = dateTime(row["ata"]),
                departs = dateTime(row["etd"]),
                completion = percent(row["completion"]),
            )
        }.sortedBy { it.name }
    }

    /**
     * The expected vessels of one page of the long-term schedule, in page order.
     *
     * @param html one page of the "long-term scheduled vessels" list.
     * @throws VesselPageException when the table lacks a needed column.
     */
    fun parseScheduled(html: String): List<ScheduledVessel> {
        val table = HtmlTable.parse(html) ?: return emptyList()
        table.require("vesselname", "etb")
        return table.rows.mapNotNull { row ->
            val name = row["vesselname"].orEmpty()
            if (name.isEmpty()) return@mapNotNull null
            ScheduledVessel(
                name = name,
                operator = row["vesseloperator"].orEmpty(),
                callSign = row["callsign"].orEmpty(),
                inVoyage = row["invoyage"].orEmpty(),
                outVoyage = row["outvoyage"].orEmpty(),
                lane = row["outlane"].orEmpty(),
                berths = dateTime(row["etb"]),
                departs = dateTime(row["etd"]),
            )
        }
    }

    /**
     * The expected vessels worth showing: not yet gone (departure, or berthing when no
     * departure is given, from [now] on), without repeats (pages read twice), by
     * berthing time.
     *
     * @param vessels every page's vessels together.
     * @param now the current time.
     */
    fun upcoming(vessels: List<ScheduledVessel>, now: LocalDateTime): List<ScheduledVessel> =
        vessels
            .distinct()
            .filter { vessel -> (vessel.departs ?: vessel.berths)?.let { !it.isBefore(now) } ?: true }
            .sortedWith(compareBy(nullsLast()) { it.berths })

    /** Fails when a needed column is missing: the page changed and must not be misread. */
    private fun HtmlTable.Table.require(vararg names: String) {
        val missing = names.filter { it !in columns }
        if (missing.isNotEmpty()) throw VesselPageException("Missing columns $missing in $columns")
    }

    /** "13/10/2026 00:50" → a time; blank or unreadable → null (the site leaves some empty). */
    private fun dateTime(text: String?): LocalDateTime? {
        val clean = text?.trim().orEmpty()
        if (clean.isEmpty()) return null
        return try {
            LocalDateTime.parse(clean, DATE_TIME)
        } catch (e: DateTimeParseException) {
            null
        }
    }

    /** "12", "12%" or "12.5" → 12 (rounded), kept within 0–100; unreadable → 0. */
    private fun percent(text: String?): Int {
        val number = text.orEmpty().replace("%", "").replace(',', '.').trim().toDoubleOrNull() ?: return 0
        return Math.round(number).toInt().coerceIn(0, 100)
    }
}
