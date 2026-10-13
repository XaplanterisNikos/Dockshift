package com.foxnks.dockshift.domain.holidays

import java.time.LocalDate
import java.time.MonthDay

/**
 * Greek Orthodox Easter Sunday.
 *
 * Computed with Meeus' Julian algorithm: the Orthodox Church dates Easter on the
 * Julian calendar, so the result is a Julian date that is then moved to the
 * Gregorian calendar we use. Pure arithmetic, so it needs no internet and works for
 * any year the calendar can show.
 */
object OrthodoxEaster {

    /**
     * @param year a Gregorian year (the calendar shows 1900–2199).
     * @return the date of Orthodox Easter Sunday in that year.
     */
    fun of(year: Int): LocalDate {
        // Meeus' Julian algorithm
        val a = year % 4
        val b = year % 7
        val c = year % 19
        val d = (19 * c + 15) % 30
        val e = (2 * a + 4 * b - d + 34) % 7
        val month = (d + e + 114) / 31
        val day = (d + e + 114) % 31 + 1
        // Julian → Gregorian: 13 days in 1900–2099, 14 in 2100–2199, and so on
        val calendarGap = year / 100 - year / 400 - 2
        return LocalDate.of(year, month, day).plusDays(calendarGap.toLong())
    }
}

/**
 * The official public holidays of Greece (national, for everyone).
 *
 * Each one is either on a fixed day of the year or a fixed number of days from
 * Orthodox Easter. Declared in calendar order, which is also the priority when two
 * fall on the same day (only Labour Day can meet an Easter holiday): the earlier
 * one is shown.
 *
 * @property fixedDay the day of the year, or null for an Easter-based holiday.
 * @property daysFromEaster days after (or, negative, before) Easter Sunday; used
 * only when [fixedDay] is null.
 */
enum class GreekHoliday(val fixedDay: MonthDay?, val daysFromEaster: Int = 0) {
    /** Πρωτοχρονιά, 1 January */
    NEW_YEAR(MonthDay.of(1, 1)),

    /** Θεοφάνεια, 6 January */
    EPIPHANY(MonthDay.of(1, 6)),

    /** Καθαρά Δευτέρα, 48 days before Easter */
    CLEAN_MONDAY(null, -48),

    /** 25η Μαρτίου */
    INDEPENDENCE_DAY(MonthDay.of(3, 25)),

    /** Μεγάλη Παρασκευή */
    GOOD_FRIDAY(null, -2),

    /** Κυριακή του Πάσχα */
    EASTER(null, 0),

    /** Δευτέρα του Πάσχα */
    EASTER_MONDAY(null, 1),

    /**
     * Πρωτομαγιά. Always shown on 1 May: when it falls in Holy Week the state
     * sometimes moves it by decree, but that cannot be computed, so the app keeps
     * the calendar date (a product decision).
     */
    LABOUR_DAY(MonthDay.of(5, 1)),

    /** Αγίου Πνεύματος (Whit Monday), 50 days after Easter */
    WHIT_MONDAY(null, 50),

    /** Δεκαπενταύγουστος, 15 August */
    ASSUMPTION(MonthDay.of(8, 15)),

    /** 28η Οκτωβρίου */
    OHI_DAY(MonthDay.of(10, 28)),

    /** Χριστούγεννα, 25 December */
    CHRISTMAS(MonthDay.of(12, 25)),

    /** Σύναξη της Θεοτόκου, 26 December */
    SECOND_DAY_OF_CHRISTMAS(MonthDay.of(12, 26)),
    ;

    /**
     * The date of this holiday in [year].
     *
     * @param year a Gregorian year.
     */
    fun dateIn(year: Int): LocalDate =
        fixedDay?.atYear(year) ?: OrthodoxEaster.of(year).plusDays(daysFromEaster.toLong())
}

/** Looks up the Greek public holidays of any period. */
object GreekHolidays {

    /**
     * Every public holiday of [year], one entry per holiday, by date.
     *
     * @param year a Gregorian year.
     * @return all [GreekHoliday] entries with their dates (two may share a date).
     */
    fun of(year: Int): List<Pair<LocalDate, GreekHoliday>> =
        GreekHoliday.entries.map { it.dateIn(year) to it }.sortedBy { it.first }

    /**
     * The holidays between two dates, both included, one per day.
     *
     * @param from first day of the range.
     * @param to last day of the range.
     * @return the holiday of each day that has one; when two share a day, the one
     * declared first in [GreekHoliday] wins.
     */
    fun between(from: LocalDate, to: LocalDate): Map<LocalDate, GreekHoliday> {
        if (to < from) return emptyMap()
        val result = sortedMapOf<LocalDate, GreekHoliday>()
        for (year in from.year..to.year) {
            GreekHoliday.entries.forEach { holiday ->
                val date = holiday.dateIn(year)
                // Declaration order = priority: keep the first holiday of a day
                if (date in from..to && date !in result) result[date] = holiday
            }
        }
        return result
    }
}
