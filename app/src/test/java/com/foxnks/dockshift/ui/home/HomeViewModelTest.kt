package com.foxnks.dockshift.ui.home

import com.foxnks.dockshift.domain.settings.AppSettings
import com.foxnks.dockshift.testing.FakeSettingsRepository
import com.foxnks.dockshift.testing.MainDispatcherRule
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Unit tests of [HomeViewModel] against a [FakeSettingsRepository].
 * They run on the JVM in milliseconds; no emulator and no DataStore file involved.
 */
class HomeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `state is Loading before anyone observes it`() {
        val viewModel = HomeViewModel(FakeSettingsRepository())

        // WhileSubscribed: nothing is read until the screen starts collecting
        assertEquals(HomeUiState.Loading, viewModel.uiState.value)
    }

    @Test
    fun `shows the saved rotation setting`() = runTest {
        val viewModel = HomeViewModel(FakeSettingsRepository(AppSettings(rotationEnabled = true)))
        observe(viewModel)

        assertEquals(HomeUiState.Ready(rotationEnabled = true), viewModel.uiState.value)
    }

    @Test
    fun `fresh install shows rotation off`() = runTest {
        val viewModel = HomeViewModel(FakeSettingsRepository())
        observe(viewModel)

        assertEquals(HomeUiState.Ready(rotationEnabled = false), viewModel.uiState.value)
    }

    @Test
    fun `toggling saves the setting and updates the state`() = runTest {
        val repository = FakeSettingsRepository()
        val viewModel = HomeViewModel(repository)
        observe(viewModel)

        viewModel.onRotationToggled(true)

        // Both the stored value and the screen state follow the switch
        assertTrue(repository.current.rotationEnabled)
        assertEquals(HomeUiState.Ready(rotationEnabled = true), viewModel.uiState.value)
    }

    /**
     * Starts a collector on [HomeViewModel.uiState], like the screen does, so the
     * WhileSubscribed flow actually runs. It lives in backgroundScope, which runTest
     * cancels automatically at the end of the test.
     */
    private fun TestScope.observe(viewModel: HomeViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
    }
}
