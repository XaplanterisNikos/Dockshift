package com.foxnks.dockshift.ui.vessels

import com.foxnks.dockshift.domain.vessels.RefreshResult
import com.foxnks.dockshift.domain.vessels.VesselSnapshot
import com.foxnks.dockshift.domain.vessels.WorkingVessel
import com.foxnks.dockshift.testing.FakeVesselRepository
import com.foxnks.dockshift.testing.MainDispatcherRule
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** [VesselsViewModel] with a fake repository. */
@OptIn(ExperimentalCoroutinesApi::class) // UnconfinedTestDispatcher is still experimental
class VesselsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val now = LocalDateTime.of(2026, 10, 14, 10, 0)
    private val clock = Clock.fixed(now.toInstant(ZoneOffset.UTC), ZoneOffset.UTC)

    private val sample = VesselSnapshot(now, listOf(WorkingVessel("SEA BREEZE", "ABC1", null, null, 40)), emptyList())

    /** Collects the state like the screen does, so WhileSubscribed runs. */
    private fun TestScope.observe(viewModel: VesselsViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
    }

    @Test
    fun `opening a screen refreshes automatically and quietly`() = runTest {
        val repository = FakeVesselRepository().apply { next = sample }
        val viewModel = VesselsViewModel(repository, clock)
        observe(viewModel)

        viewModel.onShown()

        assertEquals(listOf(false), repository.calls)
        assertEquals(sample, viewModel.uiState.value.snapshot)
        assertNull(viewModel.uiState.value.message)
        assertFalse(viewModel.uiState.value.refreshing)
    }

    @Test
    fun `an automatic failure is told only when there is nothing to show`() = runTest {
        val repository = FakeVesselRepository().apply { result = RefreshResult.NETWORK_ERROR }
        val viewModel = VesselsViewModel(repository, clock)
        observe(viewModel)

        viewModel.onShown()
        assertEquals(RefreshResult.NETWORK_ERROR, viewModel.uiState.value.message)
        viewModel.onMessageShown()

        repository.stored.value = sample
        viewModel.onShown()
        assertNull(viewModel.uiState.value.message)
    }

    @Test
    fun `the button always gets an answer`() = runTest {
        val repository = FakeVesselRepository().apply { next = sample }
        val viewModel = VesselsViewModel(repository, clock)
        observe(viewModel)

        viewModel.onRefresh()

        assertEquals(listOf(true), repository.calls)
        assertEquals(RefreshResult.UPDATED, viewModel.uiState.value.message)
    }

    @Test
    fun `the button does nothing while locked`() = runTest {
        val repository = FakeVesselRepository()
        repository.lastManual.value = now.minusMinutes(20)
        val viewModel = VesselsViewModel(repository, clock)
        observe(viewModel)

        viewModel.onRefresh()

        assertTrue(repository.calls.isEmpty())
        assertEquals(now.minusMinutes(20), viewModel.uiState.value.lastManualRefresh)
    }

    @Test
    fun `nothing is read when the pages are not set up`() = runTest {
        val repository = FakeVesselRepository(isConfigured = false)
        val viewModel = VesselsViewModel(repository, clock)
        observe(viewModel)

        viewModel.onShown()

        assertTrue(repository.calls.isEmpty())
        assertFalse(viewModel.uiState.value.configured)
    }
}
