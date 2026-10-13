package com.foxnks.dockshift.ui.leave

import com.foxnks.dockshift.domain.holidays.GreekHoliday
import com.foxnks.dockshift.domain.leave.Leave
import com.foxnks.dockshift.domain.leave.LeaveProblem
import com.foxnks.dockshift.domain.settings.AppSettings
import com.foxnks.dockshift.domain.shift.Shift
import com.foxnks.dockshift.domain.shift.ShiftType
import com.foxnks.dockshift.testing.FakeSettingsRepository
import com.foxnks.dockshift.testing.InMemoryLeaveRepository
import com.foxnks.dockshift.testing.InMemoryShiftRepository
import com.foxnks.dockshift.testing.MainDispatcherRule
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

/** The leave screen and the add/edit leave screen. */
@OptIn(ExperimentalCoroutinesApi::class) // UnconfinedTestDispatcher is still experimental
class LeaveViewModelsTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val today = LocalDate.of(2026, 10, 13)
    private val clock = Clock.fixed(today.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC)

    private fun d(month: Int, day: Int, year: Int = 2026): LocalDate = LocalDate.of(year, month, day)

    private val summer = Leave(1, d(8, 3), d(8, 16), "Καλοκαιρινή")
    private val christmas = Leave(2, d(12, 21), d(12, 26), "Χριστούγεννα")

    /** Settings with 22 days in 2026 and the port closed on Christmas Day. */
    private fun settings() = FakeSettingsRepository(
        AppSettings(leaveEntitlements = mapOf(2026 to 22), portClosedHolidays = setOf(GreekHoliday.CHRISTMAS)),
    )

    private fun TestScope.observe(viewModel: LeavesViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
    }

    private val LeavesViewModel.ready get() = uiState.value as LeavesUiState.Ready
    private val EditLeaveViewModel.editing get() = uiState.value as EditLeaveUiState.Editing

    @Test
    fun `the year shows its balance and leaves`() = runTest {
        val viewModel = LeavesViewModel(InMemoryLeaveRepository(listOf(summer, christmas)), settings(), clock)
        observe(viewModel)

        val state = viewModel.ready
        assertEquals(2026, state.year)
        assertTrue(state.entitlementSet)
        // 14 summer days taken; 21–26/12 planned, Christmas Day not counted: 5
        assertEquals(14, state.balance.taken)
        assertEquals(5, state.balance.planned)
        assertEquals(3, state.balance.remaining)
        assertEquals(listOf(14, 5), state.leaves.map { it.countedDays })
        assertTrue(2025 in state.years && 2028 in state.years)
    }

    @Test
    fun `the entitlement is saved per year and kept within range`() = runTest {
        val settings = settings()
        val viewModel = LeavesViewModel(InMemoryLeaveRepository(), settings, clock)
        observe(viewModel)

        viewModel.onYearSelected(2027)
        viewModel.onEntitlementChanged(24)
        viewModel.onEntitlementChanged(99)

        assertEquals(60, settings.current.leaveEntitlements[2027])
        assertEquals(22, settings.current.leaveEntitlements[2026])
    }

    @Test
    fun `closed-port holidays are toggled`() = runTest {
        val settings = settings()
        val viewModel = LeavesViewModel(InMemoryLeaveRepository(), settings, clock)
        observe(viewModel)

        viewModel.onPortClosedToggled(GreekHoliday.NEW_YEAR, true)
        viewModel.onPortClosedToggled(GreekHoliday.CHRISTMAS, false)

        assertEquals(setOf(GreekHoliday.NEW_YEAR), settings.current.portClosedHolidays)
    }

    @Test
    fun `a new leave counts its days and the days left`() = runTest {
        val leaves = InMemoryLeaveRepository(listOf(summer))
        val viewModel = EditLeaveViewModel(leaves, InMemoryShiftRepository(), settings(), clock)
        viewModel.start(0, d(12, 21))

        viewModel.onEndSelected(d(12, 26))
        viewModel.onNoteChanged("Χριστούγεννα")

        assertEquals(5, viewModel.editing.countedDays)
        assertEquals(1, viewModel.editing.closedDays)
        assertEquals(3, viewModel.editing.remainingAfter)
        assertNull(viewModel.editing.problem)

        viewModel.onSave()

        assertTrue(viewModel.editing.finished)
        assertEquals("Χριστούγεννα", leaves.stored.last().note)
    }

    @Test
    fun `an overlapping leave cannot be saved`() = runTest {
        val leaves = InMemoryLeaveRepository(listOf(summer))
        val viewModel = EditLeaveViewModel(leaves, InMemoryShiftRepository(), settings(), clock)
        viewModel.start(0, d(8, 15))

        assertEquals(LeaveProblem.Overlaps(summer), viewModel.editing.problem)
        viewModel.onSave()

        assertFalse(viewModel.editing.finished)
        assertEquals(1, leaves.stored.size)
    }

    @Test
    fun `a first day after the last moves the last day along`() = runTest {
        val viewModel = EditLeaveViewModel(InMemoryLeaveRepository(), InMemoryShiftRepository(), settings(), clock)
        viewModel.start(0, d(11, 2))

        viewModel.onStartSelected(d(11, 10))

        assertEquals(d(11, 10), viewModel.editing.end)
    }

    @Test
    fun `stored shifts in the leave are replaced after asking`() = runTest {
        val shifts = InMemoryShiftRepository(listOf(Shift(d(11, 3), ShiftType.MORNING), Shift(d(11, 20), ShiftType.NIGHT)))
        val leaves = InMemoryLeaveRepository()
        val viewModel = EditLeaveViewModel(leaves, shifts, settings(), clock)
        viewModel.start(0, d(11, 2))
        viewModel.onEndSelected(d(11, 6))

        viewModel.onSave()

        // Asked first, nothing changed yet
        assertEquals(1, viewModel.editing.confirmReplace)
        assertTrue(leaves.stored.isEmpty())

        viewModel.onConfirmReplace()

        assertEquals(setOf(d(11, 20)), shifts.stored.keys)
        assertEquals(1, leaves.stored.size)
    }

    @Test
    fun `an existing leave is loaded, changed and deleted`() = runTest {
        val leaves = InMemoryLeaveRepository(listOf(summer, christmas))
        val viewModel = EditLeaveViewModel(leaves, InMemoryShiftRepository(), settings(), clock)
        viewModel.start(summer.id, null)

        assertTrue(viewModel.editing.isExisting)
        assertEquals("Καλοκαιρινή", viewModel.editing.note)

        viewModel.onDeleteRequested()
        viewModel.onConfirmDelete()

        assertEquals(listOf(christmas), leaves.stored)
        assertTrue(viewModel.editing.finished)
    }
}
