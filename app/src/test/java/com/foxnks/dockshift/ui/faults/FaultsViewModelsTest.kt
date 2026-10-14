package com.foxnks.dockshift.ui.faults

import com.foxnks.dockshift.domain.faults.FaultType
import com.foxnks.dockshift.domain.faults.MachineFault
import com.foxnks.dockshift.domain.machine.Machine
import com.foxnks.dockshift.domain.machine.MachineCategory
import com.foxnks.dockshift.testing.InMemoryFaultRepository
import com.foxnks.dockshift.testing.InMemoryMachineRepository
import com.foxnks.dockshift.testing.MainDispatcherRule
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** [FaultsViewModel] (a machine's fault page) and [OpenFaultsViewModel] (cards and counts). */
@OptIn(ExperimentalCoroutinesApi::class) // UnconfinedTestDispatcher is still experimental
class FaultsViewModelsTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val today = LocalDate.of(2026, 10, 14)
    private val clock = Clock.fixed(today.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC)

    private val truck = Machine(5, MachineCategory.TRUCKS, "6999")
    private val rtg = Machine(7, MachineCategory.RTG, "12")
    private val machines = InMemoryMachineRepository(listOf(truck, rtg))

    private val FaultsViewModel.ready get() = uiState.value as FaultsUiState.Ready

    private fun kotlinx.coroutines.test.TestScope.observe(viewModel: FaultsViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
    }

    @Test
    fun `the page offers the category's faults`() = runTest {
        val viewModel = FaultsViewModel(machines, InMemoryFaultRepository(), clock)
        observe(viewModel)
        viewModel.start(truck.id)

        assertEquals(truck, viewModel.ready.machine)
        assertEquals(FaultType.LIGHTS, viewModel.ready.reportable.first())
        assertFalse(FaultType.SPREADER in viewModel.ready.reportable)
        assertTrue(viewModel.ready.faults.open.isEmpty())
    }

    @Test
    fun `reporting opens a fault today and takes it off the buttons`() = runTest {
        val faults = InMemoryFaultRepository()
        val viewModel = FaultsViewModel(machines, faults, clock)
        observe(viewModel)
        viewModel.start(rtg.id)

        viewModel.onReport(FaultType.SPREADER)

        val open = viewModel.ready.faults.open.single()
        assertEquals(FaultType.SPREADER, open.type)
        assertEquals(today, open.reportedOn)
        assertFalse(FaultType.SPREADER in viewModel.ready.reportable)
        assertEquals(FaultType.SPREADER, viewModel.ready.reported)
        viewModel.onReportedShown()
        assertNull(viewModel.ready.reported)
    }

    @Test
    fun `fixing moves a fault to the history, in another shift too`() = runTest {
        // Reported three days ago, found fixed today
        val faults = InMemoryFaultRepository(listOf(MachineFault(1, rtg.id, FaultType.LIGHTS, today.minusDays(3))))
        val viewModel = FaultsViewModel(machines, faults, clock)
        observe(viewModel)
        viewModel.start(rtg.id)

        viewModel.onFixed(viewModel.ready.faults.open.single())

        assertTrue(viewModel.ready.faults.open.isEmpty())
        val fixed = viewModel.ready.faults.history.single()
        assertEquals(today.minusDays(3), fixed.reportedOn)
        assertEquals(today, fixed.fixedOn)
        // Fixed: it can be reported again
        assertTrue(FaultType.LIGHTS in viewModel.ready.reportable)
    }

    @Test
    fun `a mistake is removed completely`() = runTest {
        val faults = InMemoryFaultRepository(listOf(MachineFault(1, rtg.id, FaultType.RADIO, today)))
        val viewModel = FaultsViewModel(machines, faults, clock)
        observe(viewModel)
        viewModel.start(rtg.id)

        viewModel.onDelete(viewModel.ready.faults.open.single())

        assertTrue(faults.stored.isEmpty())
    }

    @Test
    fun `a deleted machine shows as missing`() = runTest {
        val viewModel = FaultsViewModel(machines, InMemoryFaultRepository(), clock)
        observe(viewModel)
        viewModel.start(99)

        assertEquals(FaultsUiState.Missing, viewModel.uiState.value)
    }

    @Test
    fun `open faults are grouped by machine for the cards`() = runTest {
        val faults = InMemoryFaultRepository(
            listOf(
                MachineFault(1, rtg.id, FaultType.LIGHTS, today.minusDays(1)),
                MachineFault(2, rtg.id, FaultType.RADIO, today.minusDays(5)),
                MachineFault(3, truck.id, FaultType.TIRE, today.minusDays(9), today.minusDays(8)),
            ),
        )
        val viewModel = OpenFaultsViewModel(faults)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.byMachine.collect() }

        // Oldest first; the fixed truck fault is not open
        assertEquals(listOf(FaultType.RADIO, FaultType.LIGHTS), viewModel.byMachine.value[rtg.id]?.map { it.type })
        assertNull(viewModel.byMachine.value[truck.id])
    }
}
