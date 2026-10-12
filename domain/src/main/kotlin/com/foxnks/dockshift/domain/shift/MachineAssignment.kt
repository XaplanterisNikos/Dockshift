package com.foxnks.dockshift.domain.shift

import com.foxnks.dockshift.domain.machine.Machine

/**
 * One machine driven during a shift. A shift holds these in the order they
 * happened, because operators can be moved between machines mid-shift.
 *
 * @property machine the machine that was driven.
 * @property duringOvertime true if it was driven in the 4 overtime hours,
 * false if in the regular 8 hours.
 */
data class MachineAssignment(
    val machine: Machine,
    val duringOvertime: Boolean = false,
)
