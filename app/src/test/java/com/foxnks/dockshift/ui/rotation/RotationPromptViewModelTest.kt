package com.foxnks.dockshift.ui.rotation

import com.foxnks.dockshift.domain.rotation.RotationAnchor
import com.foxnks.dockshift.domain.rotation.RotationSchedule
import com.foxnks.dockshift.domain.settings.AppSettings
import com.foxnks.dockshift.domain.settings.WorkSchedule
import com.foxnks.dockshift.domain.shift.Shift
import com.foxnks.dockshift.domain.shift.ShiftType
import com.foxnks.dockshift.testing.FakeSettingsRepository
import com.foxnks.dockshift.testing.InMemoryShiftRepository
import com.foxnks.dockshift.testing.MainDispatcherRule
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** When the one-time rotation question is asked, and what its answers save. */
class RotationPromptViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val today = LocalDate.of(2026, 10, 13)
    private val clock = Clock.fixed(today.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
    private val sample = RotationAnchor(LocalDate.of(2024, 8, 10), ShiftType.AFTERNOON)

    /** Three weeks of the sample operator's shifts, ending yesterday. */
    private val matchingShifts = InMemoryShiftRepository(
        (1..21L).map { i ->
            val day = today.minusDays(i)
            Shift(day, RotationSchedule.typeOn(sample, day))
        },
    )

    /** Settings after the first-run import question, as on any phone past the first start. */
    private val afterFirstRun = AppSettings(legacyImportAsked = true, workerPromptAsked = true)

    /** Collects the suggestion like the screen does, so WhileSubscribed runs. */
    private fun TestScope.observe(viewModel: RotationPromptViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.suggestion.collect() }
    }

    @Test
    fun `asks when the recent shifts fit the rotation`() = runTest {
        val viewModel = RotationPromptViewModel(FakeSettingsRepository(afterFirstRun), matchingShifts, clock)
        observe(viewModel)

        assertEquals(RotationSchedule.latestAnchor(sample, today), viewModel.suggestion.value)
    }

    @Test
    fun `yes saves the place and the question is gone`() = runTest {
        val settings = FakeSettingsRepository(afterFirstRun)
        val viewModel = RotationPromptViewModel(settings, matchingShifts, clock)
        observe(viewModel)

        viewModel.onAccept(viewModel.suggestion.value!!)

        assertEquals(RotationSchedule.latestAnchor(sample, today), settings.current.rotationAnchor)
        assertTrue(settings.current.rotationPromptAnswered)
        assertNull(viewModel.suggestion.value)
    }

    @Test
    fun `not now is remembered and saves nothing`() = runTest {
        val settings = FakeSettingsRepository(afterFirstRun)
        val viewModel = RotationPromptViewModel(settings, matchingShifts, clock)
        observe(viewModel)

        viewModel.onDecline()

        assertNull(settings.current.rotationAnchor)
        assertNull(viewModel.suggestion.value)
    }

    @Test
    fun `not asked before the first-run questions, with a place, or with a limited schedule`() = runTest {
        listOf(
            AppSettings(),
            // The profile question comes first
            AppSettings(legacyImportAsked = true),
            afterFirstRun.copy(rotationAnchor = sample),
            afterFirstRun.copy(workSchedule = WorkSchedule.MORNING_ONLY),
            afterFirstRun.copy(rotationPromptAnswered = true),
        ).forEach { saved ->
            val viewModel = RotationPromptViewModel(FakeSettingsRepository(saved), matchingShifts, clock)
            observe(viewModel)
            assertNull("$saved", viewModel.suggestion.value)
        }
    }

    @Test
    fun `not asked when the shifts do not fit clearly`() = runTest {
        val few = InMemoryShiftRepository(listOf(Shift(today.minusDays(1), ShiftType.MORNING)))
        val viewModel = RotationPromptViewModel(FakeSettingsRepository(afterFirstRun), few, clock)
        observe(viewModel)

        assertNull(viewModel.suggestion.value)
    }
}
