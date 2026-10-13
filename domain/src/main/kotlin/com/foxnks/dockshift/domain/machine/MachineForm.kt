package com.foxnks.dockshift.domain.machine

/**
 * The values of the add/edit machine form, exactly as typed, and the rules that
 * decide what saving them means.
 *
 * The form never touches storage: [plan] looks at the current machine list and
 * returns a [MachineSavePlan], which the caller then carries out. Keeping the
 * decision here makes every rule of the machine list testable on the JVM.
 *
 * @property id the machine being edited, or 0 for a new one.
 * @property category the machine's category; chosen only when adding, fixed afterwards.
 * @property number the fleet number as typed (trimmed when saved).
 * @property location the location as typed; blank means "no location".
 * @property active whether the machine is offered when picking machines for a shift.
 */
data class MachineForm(
    val id: Long = 0,
    val category: MachineCategory,
    val number: String = "",
    val location: String = "",
    val active: Boolean = true,
) {

    /** True when adding a new machine, false when editing a stored one. */
    val isNew: Boolean get() = id == 0L

    /**
     * Decides what saving this form does, against the current machine list.
     *
     * - Number blank or longer than [MAX_NUMBER_LENGTH] → an error plan.
     * - Adding a number that exists **inactive** → that machine is reactivated, so
     *   the same physical machine keeps one identity (and its history).
     * - Adding a number that exists active, or renaming onto a number another
     *   machine already has (active or not) → [MachineSavePlan.Duplicate].
     * - Otherwise → [MachineSavePlan.Save] of the cleaned-up machine.
     *
     * @param existing every stored machine, active and inactive.
     * @return what to do; nothing has been saved yet.
     */
    fun plan(existing: List<Machine>): MachineSavePlan {
        val cleanNumber = number.trim()
        if (cleanNumber.isEmpty()) return MachineSavePlan.BlankNumber
        if (cleanNumber.length > MAX_NUMBER_LENGTH) return MachineSavePlan.NumberTooLong

        val cleanLocation = cleanLocation(location)
        // Another machine (never the one being edited) with the same category and number
        val other = existing.firstOrNull { it.category == category && it.number == cleanNumber && it.id != id }

        return when {
            other == null -> MachineSavePlan.Save(
                Machine(id = id, category = category, number = cleanNumber, active = active, location = cleanLocation),
            )
            // Adding a retired machine back: reuse it; a typed location replaces the old one
            isNew && !other.active -> MachineSavePlan.Reactivate(
                other.copy(active = true, location = cleanLocation ?: other.location),
            )
            else -> MachineSavePlan.Duplicate(other)
        }
    }

    companion object {

        /** Longest accepted fleet number; real ones have at most 4 digits. */
        const val MAX_NUMBER_LENGTH = 8

        /**
         * A form filled with a stored machine, for editing it.
         *
         * @param machine the machine to edit.
         */
        fun from(machine: Machine): MachineForm = MachineForm(
            id = machine.id,
            category = machine.category,
            number = machine.number,
            location = machine.location.orEmpty(),
            active = machine.active,
        )

        /**
         * Trims a typed location and collapses repeated spaces, so the same place is
         * never stored in two spellings.
         *
         * @param text the location as typed.
         * @return the cleaned location, or null when nothing was typed.
         */
        fun cleanLocation(text: String): String? =
            text.trim().replace(Regex("\\s+"), " ").ifEmpty { null }
    }
}

/** The outcome of [MachineForm.plan]: what saving the form should do. */
sealed interface MachineSavePlan {

    /**
     * Store this machine: insert it when its id is 0, otherwise update it.
     *
     * @property machine the machine with cleaned-up values.
     */
    data class Save(val machine: Machine) : MachineSavePlan

    /**
     * The number belongs to an inactive machine: store it active again.
     *
     * @property machine the existing machine, already marked active.
     */
    data class Reactivate(val machine: Machine) : MachineSavePlan

    /**
     * Another machine already has this category and number; nothing is saved.
     *
     * @property existing that machine, so the message can name it.
     */
    data class Duplicate(val existing: Machine) : MachineSavePlan

    /** The number field is empty. */
    data object BlankNumber : MachineSavePlan

    /** The number is longer than [MachineForm.MAX_NUMBER_LENGTH]. */
    data object NumberTooLong : MachineSavePlan
}
