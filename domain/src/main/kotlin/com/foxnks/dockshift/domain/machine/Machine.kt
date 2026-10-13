package com.foxnks.dockshift.domain.machine

/**
 * One physical machine of the terminal, e.g. RTG 12.
 *
 * Machines are never physically deleted: a retired one is only deactivated
 * ("soft delete"), so past shifts keep showing which machine was driven.
 *
 * @property id database identity; 0 means "not saved yet".
 * @property category the fixed equipment category.
 * @property number the fleet number as printed on the machine (kept as text,
 * so numbering schemes never need an app update).
 * @property active false once the user removed it from the pick list.
 * @property location where the machine works or is parked, e.g. "Προβλήτα ΙΙ Μπλόκ 15";
 * one per machine (not per shift), editable by the user; null when unknown.
 */
data class Machine(
    val id: Long,
    val category: MachineCategory,
    val number: String,
    val active: Boolean = true,
    val location: String? = null,
) {
    init {
        require(number.isNotBlank()) { "Machine number must not be blank" }
    }

    /** Short display text, e.g. "RTG 12". */
    val label: String
        get() = "${category.name} $number"
}
