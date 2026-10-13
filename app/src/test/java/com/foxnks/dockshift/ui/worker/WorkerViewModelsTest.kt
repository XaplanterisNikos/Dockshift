package com.foxnks.dockshift.ui.worker

import com.foxnks.dockshift.domain.machine.MachineCategory
import com.foxnks.dockshift.domain.settings.AppSettings
import com.foxnks.dockshift.domain.worker.WorkerProblem
import com.foxnks.dockshift.domain.worker.WorkerProfile
import com.foxnks.dockshift.domain.worker.WorkerRole
import com.foxnks.dockshift.testing.FakeSettingsRepository
import com.foxnks.dockshift.testing.MainDispatcherRule
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** [WorkerViewModel] (the profile form) and [WorkerPromptViewModel] (the first-run question). */
@OptIn(ExperimentalCoroutinesApi::class) // UnconfinedTestDispatcher is still experimental
class WorkerViewModelsTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val stored = WorkerProfile(
        id = "id-1",
        name = "Νίκος",
        email = "foxnks@gmail.com",
        role = WorkerRole.OPERATOR,
        categories = setOf(MachineCategory.RTG),
    )

    @Test
    fun `the form starts with the stored profile`() = runTest {
        val viewModel = WorkerViewModel(FakeSettingsRepository(AppSettings(worker = stored)))
        val state = viewModel.uiState.value

        assertTrue(state.loaded)
        assertEquals("id-1", state.id)
        assertEquals("Νίκος", state.name)
        assertEquals(WorkerRole.OPERATOR, state.role)
        assertEquals(setOf(MachineCategory.RTG), state.categories)
    }

    @Test
    fun `picking a role ticks all of its categories`() = runTest {
        val viewModel = WorkerViewModel(FakeSettingsRepository())

        viewModel.onRoleSelected(WorkerRole.DRIVER)
        assertEquals(setOf(MachineCategory.TRUCKS), viewModel.uiState.value.categories)

        viewModel.onRoleSelected(WorkerRole.OPERATOR)
        viewModel.onCategoryToggled(MachineCategory.QC)
        assertEquals(WorkerRole.OPERATOR.categories - MachineCategory.QC, viewModel.uiState.value.categories)

        // The same role again keeps the user's ticks
        viewModel.onRoleSelected(WorkerRole.OPERATOR)
        assertFalse(MachineCategory.QC in viewModel.uiState.value.categories)
    }

    @Test
    fun `saving stores the cleaned profile, keeps the id and answers the question`() = runTest {
        val settings = FakeSettingsRepository(AppSettings(worker = WorkerProfile(id = "id-1")))
        val viewModel = WorkerViewModel(settings)

        viewModel.onNameChanged("  Νίκος ")
        viewModel.onEmailChanged("foxnks@gmail.com")
        viewModel.onRoleSelected(WorkerRole.DRIVER)
        viewModel.onSave()

        assertTrue(viewModel.uiState.value.finished)
        assertEquals(
            WorkerProfile("id-1", "Νίκος", "foxnks@gmail.com", WorkerRole.DRIVER, setOf(MachineCategory.TRUCKS)),
            settings.current.worker,
        )
        assertTrue(settings.current.workerPromptAsked)
    }

    @Test
    fun `an empty form can be saved`() = runTest {
        val viewModel = WorkerViewModel(FakeSettingsRepository())
        viewModel.onSave()
        assertTrue(viewModel.uiState.value.finished)
    }

    @Test
    fun `a wrong email blocks saving and the error follows the typing`() = runTest {
        val settings = FakeSettingsRepository()
        val viewModel = WorkerViewModel(settings)

        // No error while typing before the first save
        viewModel.onEmailChanged("foxnks@")
        assertTrue(viewModel.uiState.value.problems.isEmpty())

        viewModel.onSave()
        assertEquals(setOf(WorkerProblem.EMAIL_INVALID), viewModel.uiState.value.problems)
        assertFalse(viewModel.uiState.value.finished)
        assertFalse(settings.current.workerPromptAsked)

        // Fixed: the error goes at once
        viewModel.onEmailChanged("foxnks@gmail.com")
        assertTrue(viewModel.uiState.value.problems.isEmpty())
    }

    @Test
    fun `a role with nothing ticked blocks saving`() = runTest {
        val viewModel = WorkerViewModel(FakeSettingsRepository())
        viewModel.onRoleSelected(WorkerRole.DRIVER)
        viewModel.onCategoryToggled(MachineCategory.TRUCKS)

        viewModel.onSave()

        assertEquals(setOf(WorkerProblem.NO_CATEGORY), viewModel.uiState.value.problems)
    }

    @Test
    fun `the profile question comes after the v1 question, once`() = runTest {
        val settings = FakeSettingsRepository()
        val viewModel = WorkerPromptViewModel(settings)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.ask.collect() }
        assertFalse(viewModel.ask.value)

        settings.setLegacyImportAsked()
        assertTrue(viewModel.ask.value)

        viewModel.onAnswered()
        assertFalse(viewModel.ask.value)
        assertTrue(settings.current.workerPromptAsked)
    }
}
