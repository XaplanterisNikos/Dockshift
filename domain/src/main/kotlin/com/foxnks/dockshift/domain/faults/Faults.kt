package com.foxnks.dockshift.domain.faults

import com.foxnks.dockshift.domain.machine.MachineCategory
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/**
 * A kind of fault a machine can have. Picked from a list, never typed, so every
 * fault reads the same in both languages and can be counted.
 *
 * Stored by [name]: renaming a constant would lose stored faults, so names never change.
 */
enum class FaultType {
    /** Spreader (the frame that grabs the container). */
    SPREADER,

    /** Twistlocks (the spreader's corner locks). */
    TWISTLOCKS,

    /** Flippers (the spreader's guides). */
    FLIPPERS,

    /** Hoist (lifting and lowering). */
    HOIST,

    /** Trolley (travel along the beam or boom). */
    TROLLEY,

    /** Boom (crane boom, or the forklift's mast). */
    BOOM,

    /** Gantry travel (along the rails or the yard). */
    GANTRY,

    /** Left engine. */
    ENGINE_LEFT,

    /** Right engine. */
    ENGINE_RIGHT,

    /** Tyre: flat or damaged. */
    TIRE,

    /** Losing air (brakes or suspension). */
    AIR_LEAK,

    /** Damaged in a collision. */
    CRASHED,

    /** Lights out. */
    LIGHTS,

    /** Broken or cracked glass. */
    GLASS,

    /** Wipers. */
    WIPERS,

    /** Heating. */
    HEATING,

    /** Cab controls (joysticks). */
    CONTROLS,

    /** Radio. */
    RADIO,

    /** On-board terminal. */
    TERMINAL,
}

/** Which faults each machine category can have, in the order they are offered. */
object FaultCatalog {

    /**
     * @param category a machine category.
     * @return its faults, most frequent first, as the operators listed them.
     */
    fun forCategory(category: MachineCategory): List<FaultType> = when (category) {
        // Terminal tractors, driven by the drivers
        MachineCategory.TRUCKS -> listOf(
            FaultType.LIGHTS, FaultType.TIRE, FaultType.AIR_LEAK, FaultType.CRASHED,
            FaultType.RADIO, FaultType.TERMINAL, FaultType.HEATING, FaultType.WIPERS,
        )
        // Straddle carriers
        MachineCategory.SC -> listOf(
            FaultType.TIRE, FaultType.ENGINE_LEFT, FaultType.ENGINE_RIGHT, FaultType.SPREADER,
            FaultType.RADIO, FaultType.TERMINAL, FaultType.CRASHED, FaultType.LIGHTS, FaultType.HEATING,
        )
        MachineCategory.RTG -> listOf(
            FaultType.SPREADER, FaultType.TIRE, FaultType.TERMINAL, FaultType.GLASS,
            FaultType.LIGHTS, FaultType.TROLLEY, FaultType.RADIO, FaultType.HEATING,
        )
        MachineCategory.RMG -> listOf(
            FaultType.SPREADER, FaultType.TERMINAL, FaultType.RADIO, FaultType.LIGHTS,
            FaultType.GLASS, FaultType.HEATING,
        )
        MachineCategory.FL -> listOf(
            FaultType.SPREADER, FaultType.BOOM, FaultType.LIGHTS, FaultType.TIRE, FaultType.HEATING,
            FaultType.GLASS, FaultType.WIPERS, FaultType.TERMINAL, FaultType.RADIO,
        )
        // Proposed from how ship-to-shore cranes work; to be confirmed by QC operators
        MachineCategory.QC -> listOf(
            FaultType.SPREADER, FaultType.TWISTLOCKS, FaultType.FLIPPERS, FaultType.HOIST,
            FaultType.TROLLEY, FaultType.BOOM, FaultType.GANTRY, FaultType.CONTROLS,
            FaultType.TERMINAL, FaultType.RADIO, FaultType.LIGHTS, FaultType.GLASS,
            FaultType.WIPERS, FaultType.HEATING,
        )
    }
}

/**
 * A fault reported on a machine. It stays open until someone checks it as fixed, in
 * the same shift or any later one; then it stays in the machine's history.
 *
 * @property id identity in storage; 0 for one not stored yet.
 * @property machineId the machine it belongs to.
 * @property type what is wrong.
 * @property reportedOn the day it was reported.
 * @property fixedOn the day it was checked as fixed, or null while open.
 */
data class MachineFault(
    val id: Long,
    val machineId: Long,
    val type: FaultType,
    val reportedOn: LocalDate,
    val fixedOn: LocalDate? = null,
) {
    init {
        require(fixedOn == null || !fixedOn.isBefore(reportedOn)) { "A fault cannot be fixed before it was reported" }
    }

    /** True while not fixed. */
    val isOpen: Boolean get() = fixedOn == null
}

/** One machine's faults, split for its page. */
data class MachineFaults(
    /** Open faults, oldest first. */
    val open: List<MachineFault>,
    /** Fixed faults, latest repair first. */
    val history: List<MachineFault>,
) {
    /** The faults that can still be reported: the category's, minus the ones already open. */
    fun reportable(catalog: List<FaultType>): List<FaultType> {
        val openTypes = open.map { it.type }.toSet()
        return catalog.filter { it !in openTypes }
    }

    companion object {
        /** @param faults every fault of one machine, in any order. */
        fun of(faults: List<MachineFault>): MachineFaults = MachineFaults(
            open = faults.filter { it.isOpen }.sortedWith(compareBy({ it.reportedOn }, { it.id })),
            history = faults.filter { !it.isOpen }
                .sortedWith(compareByDescending<MachineFault> { it.fixedOn }.thenByDescending { it.reportedOn }),
        )
    }
}

/** Where faults are stored. Defined here, implemented by the :app module. */
interface FaultRepository {

    /**
     * @param machineId a machine.
     * @return all its faults, open and fixed, again after every change.
     */
    fun observeForMachine(machineId: Long): Flow<List<MachineFault>>

    /** Every open fault of every machine, again after every change. */
    fun observeOpen(): Flow<List<MachineFault>>

    /**
     * Opens a fault, unless the same one is already open on that machine.
     *
     * @param machineId the machine.
     * @param type what is wrong.
     * @param date the day it is reported.
     */
    suspend fun report(machineId: Long, type: FaultType, date: LocalDate)

    /**
     * Checks an open fault as fixed.
     *
     * @param id the fault.
     * @param date the day it was found fixed; never before it was reported.
     */
    suspend fun fix(id: Long, date: LocalDate)

    /**
     * Removes a fault entered by mistake, open or fixed.
     *
     * @param id the fault.
     */
    suspend fun delete(id: Long)
}
