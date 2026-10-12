package com.foxnks.dockshift.domain.machine

/**
 * The fixed categories of terminal equipment an operator can drive.
 *
 * The list is closed: a new category means a new app version. Individual machines
 * inside a category are user-managed data (see [Machine]).
 * The declaration order is the display order.
 */
enum class MachineCategory {
    /** Straddle carrier */
    SC,

    /** Rubber-tyred gantry crane */
    RTG,

    /** Rail-mounted gantry crane */
    RMG,

    /** Forklift */
    FL,

    /** Terminal tractor (truck) */
    TRUCKS,

    /** Quay (ship-to-shore) crane */
    QC,
}
