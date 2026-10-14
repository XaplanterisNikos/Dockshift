package com.foxnks.dockshift.ui.faults

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.foxnks.dockshift.domain.faults.FaultCatalog
import com.foxnks.dockshift.domain.faults.FaultRepository
import com.foxnks.dockshift.domain.faults.FaultType
import com.foxnks.dockshift.domain.faults.MachineFault
import com.foxnks.dockshift.domain.faults.MachineFaults
import com.foxnks.dockshift.domain.machine.Machine
import com.foxnks.dockshift.domain.machine.MachineRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What a machine's fault page shows. */
sealed interface FaultsUiState {

    /** The machine and its faults are being read. */
    data object Loading : FaultsUiState

    /** The machine was deleted meanwhile. */
    data object Missing : FaultsUiState

    /**
     * @property machine the machine.
     * @property faults its open faults and history.
     * @property reportable the faults of its category that are not open now, in order.
     * @property reported the fault just reported, for a confirmation; null once shown.
     */
    data class Ready(
        val machine: Machine,
        val faults: MachineFaults,
        val reportable: List<FaultType>,
        val reported: FaultType? = null,
    ) : FaultsUiState
}

/**
 * ViewModel of a machine's fault page, opened from "Today" or from the machine's
 * edit sheet. Faults are picked from the machine category's list, never typed.
 *
 * @param machines the machine list, for the machine itself.
 * @param faults where faults are stored.
 * @param clock the current time: faults are reported and fixed "today".
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class FaultsViewModel @Inject constructor(
    private val machines: MachineRepository,
    private val faults: FaultRepository,
    private val clock: Clock,
) : ViewModel() {

    private val machineId = MutableStateFlow<Long?>(null)
    private val reported = MutableStateFlow<FaultType?>(null)

    /** The page state. */
    val uiState: StateFlow<FaultsUiState> = machineId.filterNotNull().flatMapLatest { id ->
        combine(
            machines.observeAll().map { list -> list.firstOrNull { it.id == id } },
            faults.observeForMachine(id),
            reported,
        ) { machine, list, justReported ->
            if (machine == null) {
                FaultsUiState.Missing
            } else {
                val split = MachineFaults.of(list)
                FaultsUiState.Ready(machine, split, split.reportable(FaultCatalog.forCategory(machine.category)), justReported)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), FaultsUiState.Loading)

    /**
     * Shows the faults of a machine. Safe to call again (e.g. after rotation).
     *
     * @param id the machine from the navigation key.
     */
    fun start(id: Long) {
        machineId.value = id
    }

    /** @param type the fault tapped: opened today on this machine. */
    fun onReport(type: FaultType) {
        val id = machineId.value ?: return
        viewModelScope.launch {
            faults.report(id, type, today())
            reported.value = type
        }
    }

    /** The confirmation was shown. */
    fun onReportedShown() {
        reported.value = null
    }

    /** @param fault an open fault checked as fixed today. */
    fun onFixed(fault: MachineFault) {
        viewModelScope.launch { faults.fix(fault.id, maxOf(today(), fault.reportedOn)) }
    }

    /** @param fault a fault entered by mistake, removed after the screen's confirmation. */
    fun onDelete(fault: MachineFault) {
        viewModelScope.launch { faults.delete(fault.id) }
    }

    private fun today(): LocalDate = LocalDate.now(clock)

    private companion object {
        /** Keeps the state alive across a rotation. */
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
