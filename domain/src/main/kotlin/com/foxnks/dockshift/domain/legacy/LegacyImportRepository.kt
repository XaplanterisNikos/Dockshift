package com.foxnks.dockshift.domain.legacy

import java.time.LocalDate

/**
 * What the v1 migration needs from storage. Implemented in the app on top of Room.
 */
interface LegacyImportRepository {

    /** Every day that already has a shift in the app. */
    suspend fun existingDates(): Set<LocalDate>

    /** Every machine the app knows, active or not. */
    suspend fun knownMachines(): Set<LegacyMachine>

    /**
     * Saves the shifts in a single transaction: either all are saved or none.
     * Machines missing from the app are added; days that already have a shift are
     * left untouched (merge).
     *
     * @param shifts the shifts to save.
     * @return how many shifts were actually saved.
     */
    suspend fun save(shifts: List<LegacyShift>): Int
}

/**
 * The v1 → v2 migration: read and check a whole file, then save it on request.
 *
 * Two steps on purpose, so the user sees the report before anything is stored.
 *
 * @param repository storage access.
 * @param notes wording of the notes added to comments.
 */
class LegacyMigration(
    private val repository: LegacyImportRepository,
    notes: LegacyNotes,
) {
    private val planner = LegacyImportPlanner(notes)

    /**
     * Reads and checks a v1 file without saving anything.
     *
     * @param text the whole file contents.
     * @return the plan; check [LegacyImportPlan.problems] before saving.
     */
    suspend fun prepare(text: String): LegacyImportPlan {
        val rows = try {
            LegacyCsvParser.parse(text)
        } catch (e: LegacyCsvException) {
            return LegacyImportPlan.broken(e.problem)
        }
        return planner.plan(rows, repository.existingDates(), repository.knownMachines())
    }

    /**
     * Saves a checked plan.
     *
     * @param plan a plan from [prepare].
     * @return how many shifts were saved.
     * @throws IllegalArgumentException if the plan has problems.
     */
    suspend fun commit(plan: LegacyImportPlan): Int {
        require(plan.problems.isEmpty()) { "A plan with problems must never be saved" }
        return repository.save(plan.shifts)
    }
}
