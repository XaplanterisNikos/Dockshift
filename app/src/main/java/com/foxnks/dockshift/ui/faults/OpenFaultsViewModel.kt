package com.foxnks.dockshift.ui.faults

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.foxnks.dockshift.domain.faults.FaultRepository
import com.foxnks.dockshift.domain.faults.MachineFault
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * The open faults of every machine, for the machine cards on "Today" and the count in
 * the machine edit sheet.
 *
 * @param faults where faults are stored.
 */
@HiltViewModel
class OpenFaultsViewModel @Inject constructor(faults: FaultRepository) : ViewModel() {

    /** Open faults by machine id, oldest first; machines without any are absent. */
    val byMachine: StateFlow<Map<Long, List<MachineFault>>> = faults.observeOpen()
        .map { list -> list.sortedBy { it.reportedOn }.groupBy { it.machineId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyMap())

    private companion object {
        /** Keeps the state alive across a rotation. */
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
