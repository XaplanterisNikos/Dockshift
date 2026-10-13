package com.foxnks.dockshift.ui.shift

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * Date texts in the phone's language (Greek day and month names on a Greek phone).
 * Formatters are created per call because the language can change while the app runs.
 */
object ShiftFormat {

    /** Long form for headings, e.g. "Τρίτη 13 Οκτωβρίου 2026". */
    fun long(date: LocalDate): String =
        date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(Locale.getDefault()))

    /** Medium form for fields, e.g. "13 Οκτ 2026". */
    fun medium(date: LocalDate): String =
        date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.getDefault()))

    /** Short form for list rows, e.g. "Τρι 13/10". */
    fun short(date: LocalDate): String =
        date.format(DateTimeFormatter.ofPattern("EEE d/M", Locale.getDefault()))

    /** Short week day for day cards, e.g. "Τετ". */
    fun weekday(date: LocalDate): String =
        date.format(DateTimeFormatter.ofPattern("EEE", Locale.getDefault()))
            .replaceFirstChar { it.titlecase(Locale.getDefault()) }

    /** Month heading, e.g. "Οκτώβριος 2026" (stand-alone month name). */
    fun month(year: Int, month: Int): String =
        LocalDate.of(year, month, 1).format(DateTimeFormatter.ofPattern("LLLL yyyy", Locale.getDefault()))
            .replaceFirstChar { it.titlecase(Locale.getDefault()) }

    /** Short month name for filter chips, e.g. "Οκτ". */
    fun monthShort(month: Int): String =
        LocalDate.of(2000, month, 1).format(DateTimeFormatter.ofPattern("LLL", Locale.getDefault()))
            .replaceFirstChar { it.titlecase(Locale.getDefault()) }
}
