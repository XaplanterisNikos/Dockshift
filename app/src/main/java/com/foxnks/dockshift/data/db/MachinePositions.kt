package com.foxnks.dockshift.data.db

import com.foxnks.dockshift.domain.machine.MachineCategory

/**
 * Where each machine works or is parked, as Dockshift v1 showed it.
 *
 * Only the starting values: from database version 2 the location is a column of
 * `machines` that users change from Settings. Used to fill that column on a fresh
 * install ([MachineSeedCallback]) and when upgrading from version 1
 * ([DatabaseMigrations.MIGRATION_1_2]), and for machines added later by an import.
 */
object MachinePositions {

    /** Straddle carriers share one parking area. */
    const val SC_PARKING = "Πάρκινγκ SC"

    /** Forklifts share one parking area. */
    const val FL_PARKING = "Πάρκινγκ FL"

    /** Trucks with an even fleet number. */
    const val TRUCKS_EVEN = "Προβλήτα ΙΙ Πάρκινγκ Ζυγά"

    /** Trucks with an odd fleet number. */
    const val TRUCKS_ODD = "Προβλήτα ΙΙΙ Πάρκινγκ Μονά"

    /** Machines with their own location, by category and fleet number. */
    private val byNumber: Map<MachineCategory, Map<String, String>> = mapOf(
        MachineCategory.RTG to mapOf(
            "1" to "Προβλήτα ΙΙ Μπλόκ 16",
            "2" to "Προβλήτα ΙΙ Μπλόκ 14",
            "3" to "Προβλήτα ΙΙ Μπλόκ 12",
            "4" to "Προβλήτα ΙΙ Μπλόκ 10",
            "5" to "Προβλήτα ΙΙ Μπλόκ 10",
            "6" to "Προβλήτα ΙΙ Μπλόκ 9",
            "7" to "Προβλήτα ΙΙ Μπλόκ 11",
            "8" to "Προβλήτα ΙΙ Μπλόκ 16",
            "9" to "Προβλήτα ΙΙ Μπλόκ 11",
            "10" to "Προβλήτα ΙΙ Μπλόκ 12",
            "11" to "Προβλήτα ΙΙ Μπλόκ 13",
            "12" to "Προβλήτα ΙΙ Μπλόκ 15",
            "13" to "Προβλήτα ΙΙ Μπλόκ 11",
            "14" to "Προβλήτα ΙΙ Μπλόκ 12",
            "15" to "Προβλήτα ΙΙ Μπλόκ 13",
            "16" to "Προβλήτα ΙΙ Μπλόκ 15",
            "17" to "ΤΡΑΙΝΟ",
            "18" to "Προβλήτα ΙΙΙ Μπλόκ 20",
            "19" to "Προβλήτα ΙΙΙ Μπλόκ 20",
            "20" to "Προβλήτα ΙΙΙ Μπλόκ 20",
            "21" to "Προβλήτα ΙΙΙ Μπλόκ 20",
            "22" to "Προβλήτα ΙΙΙ Μπλόκ 49",
            "23" to "Προβλήτα ΙΙΙ Μπλόκ 39",
            "24" to "Προβλήτα ΙΙΙ Μπλόκ 46",
            "25" to "Προβλήτα ΙΙΙ Μπλόκ 41",
            "26" to "Προβλήτα ΙΙΙ Μπλόκ 42",
            "27" to "Προβλήτα ΙΙΙ Μπλόκ 43",
            "28" to "Προβλήτα ΙΙΙ Μπλόκ 51",
            "29" to "Προβλήτα ΙΙΙ Μπλόκ 47 - 48",
            "30" to "Προβλήτα ΙΙΙ Μπλόκ 45",
            "31" to "Προβλήτα ΙΙΙ Μπλόκ 43 - 44",
            "32" to "Προβλήτα ΙΙΙ Μπλόκ 50 - 51",
            "33" to "Προβλήτα ΙΙΙ Μπλόκ 40",
            "34" to "Προβλήτα ΙΙΙ Μπλόκ 44 - 45",
            "35" to "Προβλήτα ΙΙΙ Μπλόκ 35",
            "36" to "Προβλήτα ΙΙΙ Μπλόκ 37",
            "37" to "Προβλήτα ΙΙΙ Μπλόκ 37 - 38",
            "38" to "Προβλήτα ΙΙΙ Μπλόκ 48",
            "39" to "Προβλήτα ΙΙΙ Μπλόκ 40 - 41",
            "40" to "Προβλήτα ΙΙΙ Μπλόκ 46 - 47",
            "41" to "Προβλήτα ΙΙΙ Μπλόκ 49 - 50",
            "42" to "Προβλήτα ΙΙΙ Μπλόκ 36",
            "43" to "Προβλήτα ΙΙΙ Μπλόκ 38",
            "44" to "Προβλήτα ΙΙΙ Μπλόκ 35",
            "45" to "Προβλήτα ΙΙΙ Μπλόκ 36",
        ),
        MachineCategory.RMG to mapOf(
            "1" to "Προβλήτα ΙΙ Μπλόκ 1",
            "2" to "Προβλήτα ΙΙ Μπλόκ 1",
            "3" to "Προβλήτα ΙΙ Μπλόκ 3",
            "4" to "Προβλήτα ΙΙ Μπλόκ 3",
            "5" to "Προβλήτα ΙΙ Μπλόκ 5",
            "6" to "Προβλήτα ΙΙ Μπλόκ 5",
            "7" to "Προβλήτα ΙΙ Μπλόκ 7",
            "8" to "Προβλήτα ΙΙ Μπλόκ 7",
            "9" to "Προβλήτα ΙΙ Μπλόκ 2",
            "10" to "Προβλήτα ΙΙ Μπλόκ 2",
            "11" to "Προβλήτα ΙΙ Μπλόκ 4",
            "12" to "Προβλήτα ΙΙ Μπλόκ 4",
            "13" to "Προβλήτα ΙΙ Μπλόκ 6",
            "14" to "Προβλήτα ΙΙ Μπλόκ 6",
            "15" to "Προβλήτα ΙΙ Μπλόκ 8",
            "16" to "Προβλήτα ΙΙ Μπλόκ 8",
            "17" to "Προβλήτα ΙΙΙ Μπλόκ 31",
            "18" to "Προβλήτα ΙΙΙ Μπλόκ 31 - 32",
            "19" to "Προβλήτα ΙΙΙ Μπλόκ 32",
            "20" to "Προβλήτα ΙΙΙ Μπλόκ 33",
            "21" to "Προβλήτα ΙΙΙ Μπλόκ 33 - 34",
            "22" to "Προβλήτα ΙΙΙ Μπλόκ 34",
        ),
        MachineCategory.QC to mapOf(
            "6" to "Προβλήτα ΙΙ Ανατολικά",
            "7" to "Προβλήτα ΙΙ Ανατολικά",
            "8" to "Προβλήτα ΙΙ Ανατολικά",
            "9" to "Προβλήτα ΙΙ Δυτικά",
            "10" to "Προβλήτα ΙΙ Δυτικά",
            "11" to "Προβλήτα ΙΙ Ανατολικά",
            "12" to "Προβλήτα ΙΙ Ανατολικά",
            "13" to "Προβλήτα ΙΙ Ανατολικά",
            "14" to "Προβλήτα ΙΙ Ανατολικά",
            "15" to "Προβλήτα ΙΙ Δυτικά",
            "16" to "Προβλήτα ΙΙ Δυτικά",
            "17" to "Προβλήτα ΙΙ Δυτικά",
            "18" to "Προβλήτα ΙΙ Δυτικά",
            "19" to "Προβλήτα ΙΙ Δυτικά",
            "20" to "Προβλήτα ΙΙ Δυτικά",
            "21" to "Προβλήτα ΙΙ Ανατολικά",
            "22" to "Προβλήτα ΙΙ Ανατολικά",
            "23" to "Προβλήτα ΙΙ Ανατολικά",
            "31" to "Προβλήτα Ι Ανατολικά",
            "32" to "Προβλήτα Ι Ανατολικά",
            "33" to "Προβλήτα Ι Ανατολικά",
            "34" to "Προβλήτα Ι Ανατολικά",
            "35" to "Προβλήτα Ι Ανατολικά",
            "36" to "Προβλήτα Ι Δυτικά",
            "37" to "Προβλήτα Ι Δυτικά",
            "38" to "Προβλήτα Ι Δυτικά",
            "39" to "Προβλήτα Ι Δυτικά",
            "40" to "Προβλήτα Ι Δυτικά",
            "41" to "Προβλήτα Ι Δυτικά",
            "42" to "Προβλήτα Ι Δυτικά",
            "43" to "Προβλήτα Ι Δυτικά",
        ),
    )

    /**
     * @param category the machine's category.
     * @param number its fleet number.
     * @return the v1 location of the machine, or null when v1 had none.
     */
    fun locationOf(category: MachineCategory, number: String): String? = when (category) {
        MachineCategory.SC -> SC_PARKING
        MachineCategory.FL -> FL_PARKING
        // v1 rule: even fleet numbers park on one pier, odd on the other
        MachineCategory.TRUCKS -> number.trim().toIntOrNull()?.let { if (it % 2 == 0) TRUCKS_EVEN else TRUCKS_ODD }
        else -> byNumber[category]?.get(number.trim())
    }
}
