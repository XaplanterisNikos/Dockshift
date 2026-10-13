package com.foxnks.dockshift.ui.legacy

import com.foxnks.dockshift.domain.legacy.LegacyMigration
import com.foxnks.dockshift.domain.legacy.LegacyProblem
import com.foxnks.dockshift.domain.settings.AppSettings
import com.foxnks.dockshift.testing.FakeLegacyImportRepository
import com.foxnks.dockshift.testing.FakeSettingsRepository
import com.foxnks.dockshift.testing.FakeShiftRepository
import com.foxnks.dockshift.testing.FakeTextFileReader
import com.foxnks.dockshift.testing.MainDispatcherRule
import com.foxnks.dockshift.testing.PlainLegacyNotes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** The v1 import flow of [LegacyImportViewModel], against in-memory fakes. */
@OptIn(ExperimentalCoroutinesApi::class) // UnconfinedTestDispatcher is still experimental
class LegacyImportViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val validFile = "content://valid"
    private val brokenFile = "content://broken"
    private val files = mapOf(
        validFile to "1,Δευτέρα,3,10,2022,Πρωί,RTG,12,null,0\n2,Τρίτη,4,10,2022,Ρεπό,none,none,null,0\n",
        brokenFile to "1,Δευτέρα,3,10,2022,Πρω,RTG,12,null,0\n",
    )

    private val shiftCount = MutableStateFlow(0)
    private val settings = FakeSettingsRepository()
    private val importRepository = FakeLegacyImportRepository(shiftCount)

    private fun viewModel() = LegacyImportViewModel(
        migration = LegacyMigration(importRepository, PlainLegacyNotes),
        reader = FakeTextFileReader(files),
        settings = settings,
        shifts = FakeShiftRepository(shiftCount),
    )

    /** Collects the state like the screen does, so WhileSubscribed runs. */
    private fun TestScope.observe(viewModel: LegacyImportViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
    }

    @Test
    fun `fresh install asks the first-run question`() = runTest {
        val viewModel = viewModel()
        observe(viewModel)

        assertTrue(viewModel.uiState.value.askOnStart)
    }

    @Test
    fun `answering the question means it is never asked again`() = runTest {
        val viewModel = viewModel()
        observe(viewModel)

        viewModel.onStartQuestionAnswered()

        assertTrue(settings.current.legacyImportAsked)
        assertFalse(viewModel.uiState.value.askOnStart)
    }

    @Test
    fun `no question when shifts already exist`() = runTest {
        shiftCount.value = 5
        val viewModel = viewModel()
        observe(viewModel)

        assertFalse(viewModel.uiState.value.askOnStart)
    }

    @Test
    fun `no question once answered on a previous run`() = runTest {
        val answered = FakeSettingsRepository(AppSettings(legacyImportAsked = true))
        val viewModel = LegacyImportViewModel(
            migration = LegacyMigration(importRepository, PlainLegacyNotes),
            reader = FakeTextFileReader(files),
            settings = answered,
            shifts = FakeShiftRepository(shiftCount),
        )
        observe(viewModel)

        assertFalse(viewModel.uiState.value.askOnStart)
    }

    @Test
    fun `valid file shows the report and saves only on confirm`() = runTest {
        val viewModel = viewModel()
        observe(viewModel)

        viewModel.onFileChosen(validFile)

        val report = viewModel.uiState.value.step as LegacyImportStep.Report
        assertEquals(2, report.plan.shifts.size)
        // Nothing stored before the user confirms
        assertTrue(importRepository.saved.isEmpty())

        viewModel.onConfirmImport()

        assertEquals(LegacyImportStep.Done(saved = 2), viewModel.uiState.value.step)
        assertEquals(2, viewModel.uiState.value.storedShifts)
    }

    @Test
    fun `file with problems shows them and saves nothing`() = runTest {
        val viewModel = viewModel()
        observe(viewModel)

        viewModel.onFileChosen(brokenFile)

        val step = viewModel.uiState.value.step as LegacyImportStep.Problems
        assertEquals(listOf(LegacyProblem.UnknownShiftType(1, "Πρω")), step.problems)
        assertTrue(importRepository.saved.isEmpty())
    }

    @Test
    fun `unreadable file is reported`() = runTest {
        val viewModel = viewModel()
        observe(viewModel)

        viewModel.onFileChosen("content://missing")

        assertEquals(LegacyImportStep.ReadFailed, viewModel.uiState.value.step)
    }

    @Test
    fun `cancelled picker changes nothing`() = runTest {
        val viewModel = viewModel()
        observe(viewModel)

        viewModel.onFileChosen(null)

        assertEquals(LegacyImportStep.Idle, viewModel.uiState.value.step)
    }

    @Test
    fun `importing the same file twice adds nothing the second time`() = runTest {
        val viewModel = viewModel()
        observe(viewModel)
        viewModel.onFileChosen(validFile)
        viewModel.onConfirmImport()

        viewModel.onFileChosen(validFile)

        // Every day already exists: merge means nothing new
        assertEquals(LegacyImportStep.NothingToImport, viewModel.uiState.value.step)
    }
}
