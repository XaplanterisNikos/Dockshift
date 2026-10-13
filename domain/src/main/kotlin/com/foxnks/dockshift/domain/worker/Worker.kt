package com.foxnks.dockshift.domain.worker

import com.foxnks.dockshift.domain.machine.MachineCategory
import java.util.UUID

/**
 * What the user does at the terminal; it decides which machine categories concern them.
 *
 * @property categories the categories of the role, in display order.
 */
enum class WorkerRole(val categories: Set<MachineCategory>) {
    /** Crane and equipment operator ("Χειριστής"): every category but the trucks. */
    OPERATOR(MachineCategory.entries.filter { it != MachineCategory.TRUCKS }.toSet()),

    /** Truck driver ("Οδηγός"): only the trucks. */
    DRIVER(setOf(MachineCategory.TRUCKS)),

    /** Both ("Και τα δύο"): every category. */
    BOTH(MachineCategory.entries.toSet()),
}

/**
 * The user's profile ("Εργαζόμενος").
 *
 * Nothing is required yet; name and email will become required with the online
 * features (Firebase). An empty profile limits nothing.
 *
 * @property id a permanent identifier (a random UUID), made once on the first start
 * and kept by backups, for the future online account. Never changed by the user.
 * @property name the user's name, shown in the app and used in messages; empty when not set.
 * @property email the user's email, empty or valid (see [WorkerForm.isValidEmail]).
 * @property role operator, driver or both; null when not set.
 * @property categories the machine categories the user works, a subset of the role's.
 */
data class WorkerProfile(
    val id: String = "",
    val name: String = "",
    val email: String = "",
    val role: WorkerRole? = null,
    val categories: Set<MachineCategory> = emptySet(),
) {
    /**
     * The categories the machine picker offers: the ticked ones of the role, in display
     * order. Every category when no role is set; all of the role's if none is ticked.
     */
    val machineCategories: List<MachineCategory>
        get() {
            val allowed = role?.let { r -> categories.intersect(r.categories).ifEmpty { r.categories } }
                ?: MachineCategory.entries.toSet()
            // The enum's order is the display order
            return MachineCategory.entries.filter { it in allowed }
        }

    /** True when the user has entered anything at all. */
    val isFilled: Boolean get() = name.isNotBlank() || email.isNotBlank() || role != null
}

/** Why a profile cannot be saved as entered. */
enum class WorkerProblem {
    /** The name is longer than [WorkerForm.MAX_NAME_LENGTH]. */
    NAME_TOO_LONG,

    /** The email is filled in but not a valid address. */
    EMAIL_INVALID,

    /** A role is chosen but none of its categories is ticked. */
    NO_CATEGORY,
}

/** The rules of the profile form, kept here so they are tested without a phone. */
object WorkerForm {

    /** Longest accepted name, generous for Greek double surnames. */
    const val MAX_NAME_LENGTH = 60

    /** Longest valid email address (RFC 5321 path limit). */
    const val MAX_EMAIL_LENGTH = 254

    /**
     * A practical email pattern: a local part of letters, digits and `._%+-` (no dot at
     * the start, the end, or twice in a row), "@", then a domain of dot-separated labels
     * ending in a letters-only extension of at least two letters, e.g. "foxnks@gmail.com".
     */
    private val EMAIL = Regex(
        "^[A-Za-z0-9_%+-]+(\\.[A-Za-z0-9_%+-]+)*" + // local part
            "@[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?" + // first domain label
            "(\\.[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?)*" + // more labels
            "\\.[A-Za-z]{2,}$", // extension
    )

    /**
     * @param email the text entered, spaces around it ignored.
     * @return true for a valid address, false otherwise (an empty field is checked by the caller).
     */
    fun isValidEmail(email: String): Boolean {
        val clean = email.trim()
        return clean.length <= MAX_EMAIL_LENGTH && EMAIL.matches(clean)
    }

    /**
     * Everything wrong with the form, empty when it can be saved. Every field is optional.
     *
     * @param name the name entered.
     * @param email the email entered.
     * @param role the chosen role, or null.
     * @param categories the ticked categories.
     */
    fun problems(name: String, email: String, role: WorkerRole?, categories: Set<MachineCategory>): Set<WorkerProblem> =
        buildSet {
            if (name.trim().length > MAX_NAME_LENGTH) add(WorkerProblem.NAME_TOO_LONG)
            if (email.isNotBlank() && !isValidEmail(email)) add(WorkerProblem.EMAIL_INVALID)
            if (role != null && categories.none { it in role.categories }) add(WorkerProblem.NO_CATEGORY)
        }

    /**
     * The profile to save, cleaned: spaces trimmed, only the role's categories kept.
     * Call only when [problems] is empty.
     *
     * @param id the profile's permanent identifier, unchanged.
     * @param name the name entered.
     * @param email the email entered.
     * @param role the chosen role, or null (then no categories are kept).
     * @param categories the ticked categories.
     */
    fun build(id: String, name: String, email: String, role: WorkerRole?, categories: Set<MachineCategory>): WorkerProfile =
        WorkerProfile(
            id = id,
            name = name.trim(),
            email = email.trim(),
            role = role,
            categories = role?.let { r -> categories.intersect(r.categories) }.orEmpty(),
        )

    /** A new permanent identifier: a random UUID, e.g. "3f2b8c1e-…". */
    fun newId(): String = UUID.randomUUID().toString()
}
